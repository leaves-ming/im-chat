package com.ming.imchatserver.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.message.MessageContentCodec;
import com.ming.imchatserver.netty.ChannelUserManager;
import com.ming.imchatserver.service.MessageService;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 消息分发落地服务：把 MQ 消息推送到在线用户。
 */
@Component
public class DispatchPushService {

    private static final Logger logger = LoggerFactory.getLogger(DispatchPushService.class);
    private static final String STATUS_RETRACTED = "RETRACTED";

    private final ChannelUserManager channelUserManager;
    private final MessageService messageService;
    private final ObjectMapper mapper = new ObjectMapper();

    public DispatchPushService(ChannelUserManager channelUserManager, MessageService messageService) {
        this.channelUserManager = channelUserManager;
        this.messageService = messageService;
    }

    public void dispatchSingle(DispatchMessagePayload payload) throws Exception {
        if (DispatchMessagePayload.EVENT_TYPE_RECALL.equalsIgnoreCase(payload.getEventType())) {
            dispatchSingleRecall(payload);
            return;
        }

        Collection<Channel> targets = channelUserManager.getChannels(payload.getToUserId());
        if (targets.isEmpty()) {
            logger.info("mq dispatch target offline, serverMsgId={} toUserId={}",
                    payload.getServerMsgId(), payload.getToUserId());
            return;
        }

        MessageDO current = messageService == null ? null : messageService.findByServerMsgId(payload.getServerMsgId());
        if (current != null && isRetracted(current)) {
            String recallPayload = buildRecallPayload(current);
            for (Channel channel : targets) {
                channel.writeAndFlush(new TextWebSocketFrame(recallPayload));
            }
            logger.info("mq dispatch converted to recall notify serverMsgId={} toUserId={} channels={}",
                    payload.getServerMsgId(), payload.getToUserId(), targets.size());
            return;
        }

        ObjectNode deliver = mapper.createObjectNode();
        deliver.put("type", "CHAT_DELIVER");
        deliver.put("fromUserId", payload.getFromUserId());
        deliver.put("toUserId", payload.getToUserId());
        deliver.put("clientMsgId", payload.getClientMsgId());
        deliver.put("serverMsgId", payload.getServerMsgId());
        String msgType = MessageContentCodec.normalizeMsgType(payload.getMsgType());
        deliver.put("msgType", msgType);
        MessageContentCodec.writeProtocolContent(deliver, "content", msgType, payload.getContent());

        String text = mapper.writeValueAsString(deliver);
        for (Channel channel : targets) {
            channel.writeAndFlush(new TextWebSocketFrame(text));
        }

        logger.info("mq dispatch delivered serverMsgId={} toUserId={} channels={}",
                payload.getServerMsgId(), payload.getToUserId(), targets.size());
    }

    private void dispatchSingleRecall(DispatchMessagePayload payload) throws Exception {
        Collection<Channel> targets = channelUserManager.getChannels(payload.getToUserId());
        if (targets.isEmpty()) {
            logger.info("mq recall target offline, serverMsgId={} toUserId={}",
                    payload.getServerMsgId(), payload.getToUserId());
            return;
        }

        ObjectNode notify = mapper.createObjectNode();
        notify.put("type", "MSG_RECALL_NOTIFY");
        notify.put("serverMsgId", payload.getServerMsgId());
        if (payload.getClientMsgId() == null) {
            notify.putNull("clientMsgId");
        } else {
            notify.put("clientMsgId", payload.getClientMsgId());
        }
        notify.put("fromUserId", payload.getFromUserId());
        notify.put("toUserId", payload.getToUserId());
        notify.put("msgType", MessageContentCodec.normalizeMsgType(payload.getMsgType()));
        notify.putNull("content");
        notify.put("status", payload.getStatus() == null ? STATUS_RETRACTED : payload.getStatus());
        if (payload.getRetractedAt() == null) {
            notify.putNull("retractedAt");
        } else {
            notify.put("retractedAt", payload.getRetractedAt());
        }
        if (payload.getRetractedBy() == null) {
            notify.putNull("retractedBy");
        } else {
            notify.put("retractedBy", payload.getRetractedBy());
        }

        String text = mapper.writeValueAsString(notify);
        for (Channel channel : targets) {
            channel.writeAndFlush(new TextWebSocketFrame(text));
        }
        logger.info("mq recall delivered serverMsgId={} toUserId={} channels={}",
                payload.getServerMsgId(), payload.getToUserId(), targets.size());
    }

    private boolean isRetracted(MessageDO message) {
        return message != null && (STATUS_RETRACTED.equalsIgnoreCase(message.getStatus()) || message.getRetractedAt() != null);
    }

    private String buildRecallPayload(MessageDO message) throws Exception {
        ObjectNode notify = mapper.createObjectNode();
        notify.put("type", "MSG_RECALL_NOTIFY");
        notify.put("serverMsgId", message.getServerMsgId());
        if (message.getClientMsgId() == null) {
            notify.putNull("clientMsgId");
        } else {
            notify.put("clientMsgId", message.getClientMsgId());
        }
        notify.put("fromUserId", message.getFromUserId());
        notify.put("toUserId", message.getToUserId());
        notify.put("msgType", MessageContentCodec.normalizeMsgType(message.getMsgType()));
        notify.putNull("content");
        notify.put("status", STATUS_RETRACTED);
        if (message.getCreatedAt() == null) {
            notify.putNull("createdAt");
        } else {
            notify.put("createdAt", message.getCreatedAt().toInstant().toString());
        }
        if (message.getRetractedAt() == null) {
            notify.putNull("retractedAt");
        } else {
            notify.put("retractedAt", message.getRetractedAt().toInstant().toString());
        }
        if (message.getRetractedBy() == null) {
            notify.putNull("retractedBy");
        } else {
            notify.put("retractedBy", message.getRetractedBy());
        }
        return mapper.writeValueAsString(notify);
    }
}
