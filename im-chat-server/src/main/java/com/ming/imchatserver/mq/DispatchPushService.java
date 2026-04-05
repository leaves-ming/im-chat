package com.ming.imchatserver.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.application.model.GroupMessageView;
import com.ming.imchatserver.config.RedisStateProperties;
import com.ming.imchatserver.message.MessageContentCodec;
import com.ming.imchatserver.message.RecallProtocolSupport;
import com.ming.imchatserver.netty.ChannelUserManager;
import com.ming.imchatserver.service.remote.RemoteGroupMessageService;
import com.ming.imchatserver.service.remote.RemoteGroupService;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

import static com.ming.imchatserver.message.RecallProtocolSupport.STATUS_RETRACTED;

/**
 * 消息分发落地服务。
 *
 * <p>该组件消费可靠投递链路产生的分发负载，并将其转换为 WebSocket 协议消息后推送给当前在线连接。</p>
 * <p>职责主要包括：</p>
 * <p>1. 处理单聊消息、单聊撤回、状态通知三类单聊分发事件。</p>
 * <p>2. 处理群聊消息与群消息撤回事件。</p>
 * <p>3. 在真正下发前再次查询消息状态，避免已撤回消息被当作普通消息推送。</p>
 * <p>4. 通过 {@link ChannelUserManager} 将消息扇出到用户的所有在线 Channel。</p>
 *
 * <p>该类只负责“在线推送”，离线补拉、MQ 重试等能力由其他组件承担。</p>
 */
@Component

public class DispatchPushService {

    private static final Logger logger = LoggerFactory.getLogger(DispatchPushService.class);
    private static final int GROUP_STATUS_RETRACTED = 2;

    private final ChannelUserManager channelUserManager;
    private final RemoteGroupMessageService groupMessageService;
    private final RemoteGroupService groupService;
    private final RedisStateProperties redisStateProperties;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public DispatchPushService(ChannelUserManager channelUserManager,
                               RemoteGroupMessageService groupMessageService,
                               RemoteGroupService groupService,
                               RedisStateProperties redisStateProperties) {
        this.channelUserManager = channelUserManager;
        this.groupMessageService = groupMessageService;
        this.groupService = groupService;
        this.redisStateProperties = redisStateProperties;
    }

    /**
     * 供精简场景或测试使用的构造器。
     */
    public DispatchPushService(ChannelUserManager channelUserManager) {
        this(channelUserManager, null, null, null);
    }

    /**
     * 分发单聊相关事件。
     *
     * <p>普通消息会封装成 {@code CHAT_DELIVER} 推送给接收方的所有在线连接；若事件类型为撤回或状态通知，
     * 则改走对应的专用分支。</p>
     * <p>在发送普通消息前，会再次查询消息当前状态。若消息已被撤回，则不再发送原消息，而是改发撤回通知，
     * 避免 MQ 延迟导致的“先撤回后收到原消息”。</p>
     *
     * @param payload MQ 下发的分发负载
     * @throws Exception 序列化或网络写出过程中出现异常
     */
    public void dispatchSingle(DispatchMessagePayload payload) throws Exception {
        if (DispatchMessagePayload.EVENT_TYPE_RECALL.equalsIgnoreCase(payload.getEventType())) {
            dispatchSingleRecall(payload);
            return;
        }
        if (DispatchMessagePayload.EVENT_TYPE_STATUS_NOTIFY.equalsIgnoreCase(payload.getEventType())) {
            dispatchStatusNotify(payload);
            return;
        }

        Collection<Channel> targets = channelUserManager.getChannels(payload.getToUserId());
        if (targets.isEmpty()) {
            logger.info("mq dispatch target offline, serverMsgId={} toUserId={}",
                    payload.getServerMsgId(), payload.getToUserId());
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

    /**
     * 分发单聊撤回通知。
     *
     * <p>接收方始终需要收到撤回通知；发送方是否需要额外收到一份，取决于该撤回事件是否来自当前节点，
     * 以避免同一节点上本地处理和 MQ 回流造成重复通知。</p>
     */
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

    /**
     * 向指定用户分发消息状态通知，例如送达、已读等状态变更。
     */
    private void dispatchStatusNotify(DispatchMessagePayload payload) throws Exception {
        Long notifyUserId = payload.getNotifyUserId();
        if (notifyUserId == null) {
            logger.warn("mq status notify missing target user serverMsgId={}", payload.getServerMsgId());
            return;
        }
        ObjectNode notify = mapper.createObjectNode();
        notify.put("type", "MSG_STATUS_NOTIFY");
        notify.put("serverMsgId", payload.getServerMsgId());
        notify.put("status", payload.getStatus());
        if (payload.getToUserId() == null) {
            notify.putNull("toUserId");
        } else {
            notify.put("toUserId", payload.getToUserId());
        }
        int deliveredChannels = fanoutToUser(notifyUserId, notify);
        logger.info("mq status notify delivered serverMsgId={} notifyUserId={} deliveredChannels={}",
                payload.getServerMsgId(), notifyUserId, deliveredChannels);
    }

    /**
     * 分发群聊相关事件。
     *
     * <p>普通群消息会封装成 {@code GROUP_MSG_PUSH} 并向群内所有在线成员广播；若事件本身是撤回，
     * 或数据库中的当前消息状态已经变为撤回，则统一转换为群撤回通知。</p>
     *
     * @param payload MQ 下发的群消息分发负载
     * @throws Exception 序列化或网络写出过程中出现异常
     */
    public void dispatchGroup(DispatchMessagePayload payload) throws Exception {
        if (payload == null || payload.getGroupId() == null || groupService == null) {
            return;
        }
        if (DispatchMessagePayload.EVENT_TYPE_RECALL.equalsIgnoreCase(payload.getEventType())) {
            dispatchGroupRecall(payload);
            return;
        }

        GroupMessageView current = groupMessageService == null ? null : groupMessageService.findByServerMsgId(payload.getServerMsgId());
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

    /**
     * 构造并广播群消息撤回通知。
     */
    private void dispatchGroupRecall(DispatchMessagePayload payload) throws Exception {
        fanoutGroup(payload.getGroupId(),
                mapper.writeValueAsString(RecallProtocolSupport.buildGroupRecallNode(mapper, "GROUP_MSG_RECALL_NOTIFY", payload)),
                false);
    }

    /**
     * 判断群消息是否已经处于撤回状态。
     */
    private boolean isRetracted(GroupMessageView message) {
        return message != null && (Integer.valueOf(GROUP_STATUS_RETRACTED).equals(message.status()) || message.retractedAt() != null);
    }

    /**
     * 将群消息数据库记录转换为撤回分发负载。
     *
     * <p>用于“MQ 收到的是普通推送事件，但数据库中该消息已撤回”的补偿场景。</p>
     */
    private DispatchMessagePayload buildGroupRecallPayload(GroupMessageView message, String originServerId) {
        DispatchMessagePayload payload = new DispatchMessagePayload();
        payload.setEventType(DispatchMessagePayload.EVENT_TYPE_RECALL);
        payload.setOriginServerId(originServerId);
        payload.setGroupId(message.groupId());
        payload.setSeq(message.seq());
        payload.setServerMsgId(message.serverMsgId());
        payload.setClientMsgId(message.clientMsgId());
        payload.setFromUserId(message.fromUserId());
        payload.setMsgType(message.msgType());
        payload.setStatus(STATUS_RETRACTED);
        payload.setCreatedAt(message.createdAt() == null ? null : message.createdAt().toInstant().toString());
        payload.setRetractedAt(message.retractedAt() == null ? null : message.retractedAt().toInstant().toString());
        payload.setRetractedBy(message.retractedBy());
        return payload;
    }

    /**
     * 将群消息核心字段写入协议对象。
     *
     * <p>普通消息会写入内容；撤回消息不再下发原始内容，而是写入空值并附带撤回信息。</p>
     */
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

    /**
     * 向指定用户的所有在线连接扇出一条协议消息。
     *
     * @return 实际写出的 Channel 数量
     */
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

    /**
     * 向群内所有在线成员广播一条已经序列化好的协议消息。
     *
     * <p>{@code skipCurrentNode} 当前未启用，预留给后续跨节点去重或本地节点跳过逻辑。</p>
     */
    private void fanoutGroup(Long groupId, String payload, boolean skipCurrentNode) {
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

    /**
     * 判断事件是否由当前服务节点产生。
     */
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
