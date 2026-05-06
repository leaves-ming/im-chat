package com.ming.imchatserver.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.dao.OutboxMessageDO;
import com.ming.imchatserver.mapper.OutboxMapper;
import com.ming.imchatserver.netty.ChannelUserManager;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 本地消息表重试定时任务。
 */
@Slf4j
@Component
public class OutboxRetryJob {

    private final OutboxMapper outboxMapper;
    private final ChannelUserManager channelUserManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedissonClient redissonClient;

    public OutboxRetryJob(OutboxMapper outboxMapper,
                          ChannelUserManager channelUserManager,
                          RedissonClient redissonClient) {
        this.outboxMapper = outboxMapper;
        this.channelUserManager = channelUserManager;
        this.redissonClient = redissonClient;
    }

    /**
     * 每30分钟重试一次待发送的消息，分布式锁避免多实例重复执行
     */
    @Scheduled(fixedDelay = 1000 * 60 * 30)
    public void retryPendingMessages() {
        RLock lock = redissonClient.getLock("outbox:retry:job:lock");
        try {
            if (!lock.tryLock(5, 60, TimeUnit.SECONDS)) {
                log.info("其他实例正在执行重试任务，跳过");
                return;
            }
            // 查询需要重试的消息：状态为PENDING，重试次数<3，到了重试时间
            List<OutboxMessageDO> pendingList = outboxMapper.selectPendingMessages(new Date(), 100);
            log.info("查询到{}条待重试消息", pendingList.size());
            
            for (OutboxMessageDO outbox : pendingList) {
                try {
                    JsonNode payload = objectMapper.readTree(outbox.getPayload());
                    long targetUserId = payload.path("targetUserId").asLong();
                    String msgType = payload.path("msgType").asText();
                    String content = payload.path("content").asText();
                    
                    // 重试推送消息给接收端
                    ObjectNode deliverMsg = objectMapper.createObjectNode()
                            .put("type", "CHAT_DELIVER")
                            .put("serverMsgId", outbox.getMessageId())
                            .put("fromUserId", outbox.getFromUserId())
                            .put("msgType", msgType)
                            .put("content", content);
                    
                    String payLoad = objectMapper.writeValueAsString(deliverMsg);
                    channelUserManager.getChannels(targetUserId).forEach(channel -> {
                        try {
                            channel.writeAndFlush(new TextWebSocketFrame(payLoad));
                        } catch (Exception e) {
                            log.error("推送消息失败，channel:{}", channel, e);
                        }
                    });
                    
                    // 更新重试次数和下次重试时间
                    outbox.setRetryCount(outbox.getRetryCount() + 1);
                    if (outbox.getRetryCount() >= outbox.getMaxRetryCount()) {
                        outbox.setStatus(-1); // 标记为失败
                        outbox.setFailReason("重试次数超过上限");
                    } else {
                        // 指数退避
                        long nextRetryDelay = 1000L * 10 * (long) Math.pow(2, outbox.getRetryCount());
                        outbox.setNextRetryAt(new Date(System.currentTimeMillis() + nextRetryDelay));
                    }
                    outbox.setUpdatedAt(new Date());
                    outboxMapper.updateById(outbox);
                } catch (Exception e) {
                    log.error("重试消息失败，outboxId:{}", outbox.getId(), e);
                    outbox.setRetryCount(outbox.getRetryCount() + 1);
                    if (outbox.getRetryCount() >= outbox.getMaxRetryCount()) {
                        outbox.setStatus(-1);
                    }
                    outbox.setUpdatedAt(new Date());
                    outboxMapper.updateById(outbox);
                }
            }
        } catch (InterruptedException e) {
            log.error("获取分布式锁失败", e);
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}