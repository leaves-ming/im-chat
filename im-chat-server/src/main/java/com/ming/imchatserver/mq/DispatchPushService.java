package com.ming.imchatserver.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.config.RedisStateProperties;
import com.ming.imchatserver.dao.GroupMessageDO;
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.message.MessageContentCodec;
import com.ming.imchatserver.message.RecallProtocolSupport;
import com.ming.imchatserver.netty.ChannelUserManager;
import com.ming.imchatserver.service.GroupMessageService;
import com.ming.imchatserver.service.GroupService;
import com.ming.imchatserver.service.MessageService;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

import static com.ming.imchatserver.message.RecallProtocolSupport.STATUS_RETRACTED;

/**
 * 消息分发落地服务：把 MQ 消息推送到在线用户。
 */
@Component

public class DispatchPushService {

    private static final Logger logger = LoggerFactory.getLogger(DispatchPushService.class);
    private static final int GROUP_STATUS_RETRACTED = 2;

    private final ChannelUserManager channelUserManager;
    private final MessageService messageService;
    private final GroupMessageService groupMessageService;
    private final GroupService groupService;
    private final RedisStateProperties redisStateProperties;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public DispatchPushService(ChannelUserManager channelUserManager,
                               MessageService messageService,
                               GroupMessageService groupMessageService,
                               GroupService groupService,
                               RedisStateProperties redisStateProperties) {
        this.channelUserManager = channelUserManager;
        this.messageService = messageService;
        this.groupMessageService = groupMessageService;
        this.groupService = groupService;
        this.redisStateProperties = redisStateProperties;
    }

    public DispatchPushService(ChannelUserManager channelUserManager, MessageService messageService) {
        this(channelUserManager, messageService, null, null, null);
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
            fanoutToUser(payload.getToUserId(),
                    RecallProtocolSupport.buildSingleRecallNode(mapper, "MSG_RECALL_NOTIFY", current));
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
        int deliveredChannels = 0;
        ObjectNode notify = RecallProtocolSupport.buildSingleRecallNode(mapper, "MSG_RECALL_NOTIFY", payload);
        deliveredChannels += fanoutToUser(payload.getToUserId(), notify);

        if (!isOriginServer(payload.getOriginServerId())) {
            deliveredChannels += fanoutToUser(payload.getFromUserId(), notify);
        }

        logger.info("mq recall delivered serverMsgId={} toUserId={} deliveredChannels={}",
                payload.getServerMsgId(), payload.getToUserId(), deliveredChannels);
    }

    public void dispatchGroup(DispatchMessagePayload payload) throws Exception {
        if (payload == null || payload.getGroupId() == null || groupService == null) {
            return;
        }
        if (DispatchMessagePayload.EVENT_TYPE_RECALL.equalsIgnoreCase(payload.getEventType())) {
            dispatchGroupRecall(payload);
            return;
        }

        GroupMessageDO current = groupMessageService == null ? null : groupMessageService.findByServerMsgId(payload.getServerMsgId());
        if (current != null && isRetracted(current)) {
            dispatchGroupRecall(buildGroupRecallPayload(current, payload.getOriginServerId()));
            return;
        }

        ObjectNode deliver = mapper.createObjectNode();
        deliver.put("type", "GROUP_MSG_PUSH");
        writeGroupMessageNode(deliver,
                payload.getGroupId(),
                payload.getSeq(),
                payload.getServerMsgId(),
                payload.getFromUserId(),
                payload.getMsgType(),
                payload.getContent(),
                false,
                "SENT",
                null,
                null);
        fanoutGroup(payload.getGroupId(), mapper.writeValueAsString(deliver), false);
    }

    private void dispatchGroupRecall(DispatchMessagePayload payload) throws Exception {
        fanoutGroup(payload.getGroupId(),
                mapper.writeValueAsString(RecallProtocolSupport.buildGroupRecallNode(mapper, "GROUP_MSG_RECALL_NOTIFY", payload)),
                isOriginServer(payload.getOriginServerId()));
    }

    private boolean isRetracted(MessageDO message) {
        return message != null && (STATUS_RETRACTED.equalsIgnoreCase(message.getStatus()) || message.getRetractedAt() != null);
    }

    private boolean isRetracted(GroupMessageDO message) {
        return message != null && (Integer.valueOf(GROUP_STATUS_RETRACTED).equals(message.getStatus()) || message.getRetractedAt() != null);
    }

    private DispatchMessagePayload buildGroupRecallPayload(GroupMessageDO message, String originServerId) {
        DispatchMessagePayload payload = new DispatchMessagePayload();
        payload.setEventType(DispatchMessagePayload.EVENT_TYPE_RECALL);
        payload.setOriginServerId(originServerId);
        payload.setGroupId(message.getGroupId());
        payload.setSeq(message.getSeq());
        payload.setServerMsgId(message.getServerMsgId());
        payload.setClientMsgId(message.getClientMsgId());
        payload.setFromUserId(message.getFromUserId());
        payload.setMsgType(message.getMsgType());
        payload.setStatus(STATUS_RETRACTED);
        payload.setCreatedAt(message.getCreatedAt() == null ? null : message.getCreatedAt().toInstant().toString());
        payload.setRetractedAt(message.getRetractedAt() == null ? null : message.getRetractedAt().toInstant().toString());
        payload.setRetractedBy(message.getRetractedBy());
        return payload;
    }

    private void writeGroupMessageNode(ObjectNode target,
                                       Long groupId,
                                       Long seq,
                                       String serverMsgId,
                                       Long fromUserId,
                                       String msgType,
                                       String content,
                                       boolean retracted,
                                       String status,
                                       String retractedAt,
                                       Long retractedBy) {
        target.put("groupId", groupId);
        if (seq == null) {
            target.putNull("seq");
        } else {
            target.put("seq", seq);
        }
        target.put("serverMsgId", serverMsgId);
        target.put("fromUserId", fromUserId);
        target.put("msgType", MessageContentCodec.normalizeMsgType(msgType));
        if (retracted) {
            target.putNull("content");
        } else {
            MessageContentCodec.writeProtocolContent(target, "content", msgType, content);
        }
        target.put("status", status);
        writeNullableText(target, "retractedAt", retractedAt);
        writeNullableLong(target, "retractedBy", retractedBy);
    }

    private int fanoutToUser(Long userId, ObjectNode payload) throws Exception {
        if (userId == null) {
            return 0;
        }
        Collection<Channel> targets = channelUserManager.getChannels(userId);
        if (targets.isEmpty()) {
            return 0;
        }
        String text = mapper.writeValueAsString(payload);
        for (Channel channel : targets) {
            channel.writeAndFlush(new TextWebSocketFrame(text));
        }
        return targets.size();
    }

    private void fanoutGroup(Long groupId, String payload, boolean skipCurrentNode) {
        if (skipCurrentNode) {
            // 分布式撤回/群推送场景下，请求发起节点已返回结果或已做本地处理，这里按节点维度跳过，避免重复通知。
            logger.info("mq group dispatch skipped on origin node groupId={}", groupId);
            return;
        }
        int deliveredChannels = 0;
        for (Long userId : groupService.listActiveMemberUserIds(groupId)) {
            Collection<Channel> targets = channelUserManager.getChannels(userId);
            for (Channel channel : targets) {
                channel.writeAndFlush(new TextWebSocketFrame(payload));
            }
            deliveredChannels += targets.size();
        }
        logger.info("mq group dispatch delivered groupId={} deliveredChannels={}", groupId, deliveredChannels);
    }

    private boolean isOriginServer(String originServerId) {
        if (originServerId == null || redisStateProperties == null || redisStateProperties.getServerId() == null) {
            return false;
        }
        return originServerId.equals(redisStateProperties.getServerId());
    }

    private void writeNullableText(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private void writeNullableLong(ObjectNode node, String field, Long value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }
}
