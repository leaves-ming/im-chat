package com.ming.imchatserver.application.facade.impl;

import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.config.RateLimitProperties;
import com.ming.imchatserver.config.RedisStateProperties;
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.mapper.DeliveryMapper;
import com.ming.imchatserver.metrics.MetricsService;
import com.ming.imchatserver.service.IdempotencyService;
import com.ming.imchatserver.service.MessageService;
import com.ming.imchatserver.service.RateLimitService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

/**
 * 单聊消息应用门面默认实现。
 */
@Component
public class MessageFacadeImpl implements MessageFacade {

    private final MessageService messageService;
    private final DeliveryMapper deliveryMapper;
    private final MetricsService metricsService;
    private final IdempotencyService idempotencyService;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final RedisStateProperties redisStateProperties;
    private final NettyProperties nettyProperties;

    public MessageFacadeImpl(MessageService messageService,
                             DeliveryMapper deliveryMapper,
                             MetricsService metricsService,
                             IdempotencyService idempotencyService,
                             RateLimitService rateLimitService,
                             RateLimitProperties rateLimitProperties,
                             RedisStateProperties redisStateProperties,
                             NettyProperties nettyProperties) {
        this.messageService = messageService;
        this.deliveryMapper = deliveryMapper;
        this.metricsService = metricsService;
        this.idempotencyService = idempotencyService;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.redisStateProperties = redisStateProperties;
        this.nettyProperties = nettyProperties;
    }

    @Override
    public ChatPersistResult sendChat(Long fromUserId,
                                      Long targetUserId,
                                      String clientMsgId,
                                      String msgType,
                                      String content) {
        if (!consumeMessageRateLimit(fromUserId)) {
            throw new IllegalArgumentException("RATE_LIMITED:message send rate exceeded");
        }
        if (!claimClientMessageId(fromUserId, clientMsgId)) {
            throw new IllegalArgumentException("DUPLICATE_REQUEST:clientMsgId replay detected");
        }

        MessageDO msg = new MessageDO();
        msg.setClientMsgId(clientMsgId);
        msg.setFromUserId(fromUserId);
        msg.setToUserId(targetUserId);
        msg.setMsgType(msgType);
        msg.setContent(content);
        msg.setStatus("SENT");
        try {
            MessageService.PersistResult result = messageService.persistMessage(msg);
            return new ChatPersistResult(clientMsgId, result.getServerMsgId(), result.isCreatedNew());
        } catch (RuntimeException ex) {
            releaseClientMessageId(fromUserId, clientMsgId);
            throw ex;
        }
    }

    @Override
    public AckReportResult reportAck(Long reporterUserId, String serverMsgId, String targetStatus) {
        MessageDO message = messageService.findByServerMsgId(serverMsgId);
        if (message == null) {
            throw new IllegalArgumentException("serverMsgId not found");
        }
        if (reporterUserId == null || !reporterUserId.equals(message.getToUserId())) {
            throw new SecurityException("not message recipient");
        }
        int updated = messageService.updateStatusByServerMsgId(serverMsgId, targetStatus);
        Date ackAt = updated > 0 ? new Date() : null;
        if (updated > 0 && deliveryMapper != null) {
            deliveryMapper.upsertAck(message.getId(), reporterUserId, ackAt, "ACKED".equals(targetStatus) ? ackAt : null);
            recordAckLatency(targetStatus, message.getCreatedAt(), ackAt);
        }
        return new AckReportResult(message, targetStatus, updated, ackAt);
    }

    @Override
    public boolean enqueueStatusNotify(MessageDO message, String status) {
        return messageService != null && messageService.enqueueStatusNotify(message, status);
    }

    @Override
    public MessageService.CursorPageResult pullOffline(Long userId,
                                                       String deviceId,
                                                       MessageService.SyncCursor syncCursor,
                                                       int limit) {
        return syncCursor == null
                ? messageService.pullOfflineFromCheckpoint(userId, deviceId, limit)
                : messageService.pullOffline(userId, deviceId, syncCursor, limit);
    }

    @Override
    public MessageService.CursorPageResult loadInitialSync(Long userId, String deviceId, int limit) {
        return messageService.pullOfflineFromCheckpoint(userId, deviceId, limit);
    }

    @Override
    public void advanceSyncCursor(Long userId, String deviceId, MessageService.CursorPageResult pageResult) {
        if (pageResult == null || pageResult.getNextCursorCreatedAt() == null || pageResult.getNextCursorId() == null) {
            return;
        }
        messageService.advanceSyncCursor(userId, deviceId,
                new MessageService.SyncCursor(pageResult.getNextCursorCreatedAt(), pageResult.getNextCursorId()));
    }

    @Override
    public MessageDO recallMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds) {
        MessageDO existing = messageService.findByServerMsgId(serverMsgId);
        if (existing == null) {
            throw new IllegalArgumentException("serverMsgId not found");
        }
        return messageService.recallMessage(operatorUserId, serverMsgId, recallWindowSeconds);
    }

    private void recordAckLatency(String ackType, Date createdAt, Date ackAt) {
        if (metricsService == null || createdAt == null || ackAt == null) {
            return;
        }
        metricsService.observeAckLatency(ackType, createdAt.getTime(), ackAt.getTime());
    }

    private boolean consumeMessageRateLimit(Long userId) {
        if (rateLimitService == null || rateLimitProperties == null || userId == null) {
            return true;
        }
        return rateLimitService.checkAndIncrement(
                "message_send",
                "userId",
                String.valueOf(userId),
                rateLimitProperties.getMessageSend().getLimit(),
                rateLimitProperties.getMessageSend().getWindowSeconds()).allowed();
    }

    private boolean claimClientMessageId(Long userId, String clientMsgId) {
        if (idempotencyService == null || redisStateProperties == null || clientMsgId == null || clientMsgId.isBlank() || userId == null) {
            return true;
        }
        return idempotencyService.claimClientMessage(userId, clientMsgId,
                Duration.ofSeconds(redisStateProperties.getClientMsgIdTtlSeconds()));
    }

    private void releaseClientMessageId(Long userId, String clientMsgId) {
        if (idempotencyService == null || clientMsgId == null || clientMsgId.isBlank() || userId == null) {
            return;
        }
        idempotencyService.releaseClientMessage(userId, clientMsgId);
    }
}
