package com.ming.imchatserver.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.netty.ChannelUserManager;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 消息推送事件监听器。
 * <p>
 * 监听 {@link MessagePersistedEvent}，异步执行消息下发，并向发送端回写 DELIVER_ACK。
 * @author ming
 */
@Component
public class MessagePushEventListener {

    private static final Logger logger = LoggerFactory.getLogger(MessagePushEventListener.class);

    private final ChannelUserManager channelUserManager;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param channelUserManager 在线连接映射管理器
     */
    public MessagePushEventListener(ChannelUserManager channelUserManager) {
        this.channelUserManager = channelUserManager;
    }

    /**
     * 处理消息持久化事件并执行推送。
     * <p>
     * - 目标在线：发送 CHAT_DELIVER，给发送端回 DELIVER_ACK。<br>
     * - 目标离线：仅给发送端回 DELIVER_ACK（附带 TARGET_OFFLINE）。
     */
    @Async("imEventExecutor")
    @EventListener
    public void onMessagePersisted(MessagePersistedEvent event) {
        try {
            ObjectNode deliver = mapper.createObjectNode();
            deliver.put("type", "CHAT_DELIVER");
            deliver.put("fromUserId", event.getFromUserId());
            deliver.put("content", event.getContent());
            deliver.put("clientMsgId", event.getClientMsgId());
            deliver.put("serverMsgId", event.getServerMsgId());

            Collection<Channel> targets = channelUserManager.getChannels(event.getToUserId());
            if (targets.isEmpty()) {
                //TODO 常量
                writeSenderAck(event, "TARGET_OFFLINE");
                logger.info("deliver queued - target offline: from={} to={} clientMsgId={} serverMsgId={}",
                        event.getFromUserId(), event.getToUserId(), event.getClientMsgId(), event.getServerMsgId());
                return;
            }

            String payload = mapper.writeValueAsString(deliver);
            for (Channel c : targets) {
                c.writeAndFlush(new TextWebSocketFrame(payload));
            }

            writeSenderAck(event, null);
            logger.info("deliver forwarded (SENT): from={} to={} clientMsgId={} serverMsgId={} channelsSent={}",
                    event.getFromUserId(), event.getToUserId(), event.getClientMsgId(), event.getServerMsgId(), targets.size());
        } catch (Exception ex) {
            logger.error("onMessagePersisted error", ex);
        }
    }

    /**
     * 向发送端回写 DELIVER_ACK。
     */
    private void writeSenderAck(MessagePersistedEvent event, String info) throws Exception {
        ObjectNode ack = mapper.createObjectNode();
        ack.put("type", "DELIVER_ACK");
        ack.put("clientMsgId", event.getClientMsgId());
        ack.put("serverMsgId", event.getServerMsgId());
        ack.put("status", "SENT");
        if (info != null) {
            ack.put("info", info);
        }
        event.getSenderChannel().writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(ack)));
    }
}
