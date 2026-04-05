package com.ming.immessageservice.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.immessageservice.config.RedisStateProperties;
import com.ming.immessageservice.config.ReliabilityProperties;
import com.ming.immessageservice.infrastructure.dao.GroupMessageDO;
import com.ming.immessageservice.infrastructure.dao.MessageDO;
import com.ming.immessageservice.infrastructure.dao.OutboxMessageDO;
import com.ming.immessageservice.infrastructure.mapper.OutboxMapper;
import com.ming.immessageservice.mq.DispatchMessagePayload;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

/**
 * outbox 追加服务。
 */
@Service
public class DispatchOutboxDomainService {

    private static final int OUTBOX_STATUS_NEW = 0;
    private static final String DEFAULT_DISPATCH_TOPIC = "im.msg.dispatch";

    private final OutboxMapper outboxMapper;
    private final ReliabilityProperties reliabilityProperties;
    private final RedisStateProperties redisStateProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DispatchOutboxDomainService(OutboxMapper outboxMapper,
                                       ReliabilityProperties reliabilityProperties,
                                       RedisStateProperties redisStateProperties) {
        this.outboxMapper = outboxMapper;
        this.reliabilityProperties = reliabilityProperties;
        this.redisStateProperties = redisStateProperties;
    }

    public void appendSingleMessage(MessageDO message) {
        if (message == null) {
            return;
        }
        DispatchMessagePayload payload = new DispatchMessagePayload();
        payload.setEventId(UUID.randomUUID().toString());
        payload.setEventType(DispatchMessagePayload.EVENT_TYPE_MESSAGE);
        payload.setOriginServerId(currentServerId());
        payload.setServerMsgId(message.getServerMsgId());
        payload.setClientMsgId(message.getClientMsgId());
        payload.setFromUserId(message.getFromUserId());
        payload.setToUserId(message.getToUserId());
        payload.setMsgType(message.getMsgType());
        payload.setContent(message.getContent());
        payload.setStatus(message.getStatus());
        payload.setCreatedAt(message.getCreatedAt() == null ? null : message.getCreatedAt().toInstant().toString());
        appendDispatchOutbox(message.getId(), DispatchMessagePayload.TAG_SINGLE, payload);
    }

    public void appendSingleRecall(MessageDO message) {
        if (message == null) {
            return;
        }
        DispatchMessagePayload payload = new DispatchMessagePayload();
        payload.setEventId(UUID.randomUUID().toString());
        payload.setEventType(DispatchMessagePayload.EVENT_TYPE_RECALL);
        payload.setOriginServerId(currentServerId());
        payload.setServerMsgId(message.getServerMsgId());
        payload.setClientMsgId(message.getClientMsgId());
        payload.setFromUserId(message.getFromUserId());
        payload.setToUserId(message.getToUserId());
        payload.setMsgType(message.getMsgType());
        payload.setStatus(message.getStatus());
        payload.setCreatedAt(message.getCreatedAt() == null ? null : message.getCreatedAt().toInstant().toString());
        payload.setRetractedAt(message.getRetractedAt() == null ? null : message.getRetractedAt().toInstant().toString());
        payload.setRetractedBy(message.getRetractedBy());
        appendDispatchOutbox(message.getId(), DispatchMessagePayload.TAG_SINGLE, payload);
    }

    public void appendStatusNotify(MessageDO message, String status, Long notifyUserId) {
        if (message == null || notifyUserId == null || status == null) {
            return;
        }
        DispatchMessagePayload payload = new DispatchMessagePayload();
        payload.setEventId(UUID.randomUUID().toString());
        payload.setEventType(DispatchMessagePayload.EVENT_TYPE_STATUS_NOTIFY);
        payload.setOriginServerId(currentServerId());
        payload.setServerMsgId(message.getServerMsgId());
        payload.setFromUserId(message.getFromUserId());
        payload.setToUserId(message.getToUserId());
        payload.setNotifyUserId(notifyUserId);
        payload.setStatus(status);
        appendDispatchOutbox(message.getId(), DispatchMessagePayload.TAG_SINGLE, payload);
    }

    public void appendGroupMessage(GroupMessageDO message) {
        appendGroupEvent(message, DispatchMessagePayload.EVENT_TYPE_MESSAGE);
    }

    public void appendGroupRecall(GroupMessageDO message) {
        appendGroupEvent(message, DispatchMessagePayload.EVENT_TYPE_RECALL);
    }

    private void appendGroupEvent(GroupMessageDO message, String eventType) {
        if (message == null) {
            return;
        }
        DispatchMessagePayload payload = new DispatchMessagePayload();
        payload.setEventId(UUID.randomUUID().toString());
        payload.setEventType(eventType);
        payload.setOriginServerId(currentServerId());
        payload.setServerMsgId(message.getServerMsgId());
        payload.setClientMsgId(message.getClientMsgId());
        payload.setFromUserId(message.getFromUserId());
        payload.setGroupId(message.getGroupId());
        payload.setSeq(message.getSeq());
        payload.setMsgType(message.getMsgType());
        payload.setStatus(Integer.valueOf(2).equals(message.getStatus()) ? "RETRACTED" : "SENT");
        payload.setContent(DispatchMessagePayload.EVENT_TYPE_RECALL.equals(eventType) ? null : message.getContent());
        payload.setCreatedAt(message.getCreatedAt() == null ? null : message.getCreatedAt().toInstant().toString());
        payload.setRetractedAt(message.getRetractedAt() == null ? null : message.getRetractedAt().toInstant().toString());
        payload.setRetractedBy(message.getRetractedBy());
        appendDispatchOutbox(message.getId(), DispatchMessagePayload.TAG_GROUP, payload);
    }

    private void appendDispatchOutbox(Long messageId, String tag, DispatchMessagePayload payload) {
        try {
            OutboxMessageDO outbox = new OutboxMessageDO();
            outbox.setEventId(payload.getEventId());
            outbox.setMessageId(messageId);
            outbox.setTopic(reliabilityProperties == null ? DEFAULT_DISPATCH_TOPIC : reliabilityProperties.getDispatchTopic());
            outbox.setTag(tag);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setStatus(OUTBOX_STATUS_NEW);
            outbox.setRetryCount(0);
            outbox.setNextRetryAt(new Date());
            outbox.setProcessingAt(null);
            outboxMapper.insert(outbox);
        } catch (Exception ex) {
            throw new IllegalStateException("append dispatch outbox failed", ex);
        }
    }

    private String currentServerId() {
        return redisStateProperties == null ? null : redisStateProperties.getServerId();
    }
}
