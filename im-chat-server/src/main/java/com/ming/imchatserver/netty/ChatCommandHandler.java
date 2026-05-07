package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.application.facade.SocialFacade;
import com.ming.imchatserver.application.model.SingleMessagePage;
import com.ming.imchatserver.application.model.SingleMessageView;
import com.ming.imchatserver.application.model.SingleSyncCursor;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.dao.OutboxMessageDO;
import com.ming.imchatserver.mapper.OutboxMapper;
import com.ming.imchatserver.service.IdempotencyService;
import com.ming.imapicontract.message.MessageContentCodec;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import io.netty.channel.Channel;

import java.time.Instant;

/**
 * 单聊命令处理器。
 */
public class ChatCommandHandler implements WsCommandHandler {

    private static final int DEFAULT_PULL_MAX_LIMIT = 200;
    private static final SingleSyncCursor INVALID_SINGLE_SYNC_CURSOR = new SingleSyncCursor(null, -1L);

    private final MessageFacade messageFacade;
    private final SocialFacade socialFacade;
    private final NettyProperties nettyProperties;
    private final WsProtocolSupport protocolSupport;
    private final ChannelUserManager channelUserManager;
    private final IdempotencyService idempotencyService;
    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public ChatCommandHandler(MessageFacade messageFacade,
                              SocialFacade socialFacade,
                              NettyProperties nettyProperties,
                              WsProtocolSupport protocolSupport,
                              ChannelUserManager channelUserManager,
                              IdempotencyService idempotencyService,
                              OutboxMapper outboxMapper,
                              ObjectMapper objectMapper) {
        this.messageFacade = messageFacade;
        this.socialFacade = socialFacade;
        this.nettyProperties = nettyProperties;
        this.protocolSupport = protocolSupport;
        this.channelUserManager = channelUserManager;
        this.idempotencyService = idempotencyService;
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String commandType) {
        return "CHAT".equals(commandType) || "ACK_REPORT".equals(commandType) || "PULL_OFFLINE".equals(commandType);
    }

    @Override
    public void handle(WsCommandContext context) throws Exception {
        switch (context.commandType()) {
            case "CHAT" -> handleChat(context);
            case "ACK_REPORT" -> handleAckReport(context);
            case "PULL_OFFLINE" -> handlePullOffline(context);
            default -> throw new IllegalArgumentException("unsupported command: " + context.commandType());
        }
    }

    private void handleChat(WsCommandContext context) throws Exception {
        Long fromUserId = WsCommandHelper.requireUser(context);
        JsonNode node = context.payload();
        long targetUserId = WsCommandHelper.positiveTargetUserId(node);
        String msgType = MessageContentCodec.normalizeMsgType(node.path("msgType").asText(null));
        String content = MessageContentCodec.validateAndSerializeIncomingContent(msgType, node.get("content"));
        String clientMsgId = WsCommandHelper.normalizeClientMsgId(node.path("clientMsgId").asText(null));
        if (nettyProperties.isSingleChatRequireActiveContact() && !socialFacade.isSingleChatAllowed(fromUserId, targetUserId)) {
            throw new SecurityException("single chat requires active bilateral contacts");
        }
        // 幂等校验：1小时内重复消息直接拦截
        if (clientMsgId != null && !idempotencyService.claimClientMessage(fromUserId, clientMsgId, Duration.ofHours(1))) {
            throw new IllegalArgumentException("DUPLICATE_REQUEST: message already processed");
        }
        
        // 写入本地消息表
        OutboxMessageDO outbox = new OutboxMessageDO();
        outbox.setEventId(UUID.randomUUID().toString().replace("-", ""));
        outbox.setClientMsgId(clientMsgId);
        outbox.setFromUserId(fromUserId);
        outbox.setPayload(objectMapper.writeValueAsString(context.payload()));
        outbox.setStatus(0); // PENDING
        outbox.setAckStatus(0); // 未确认
        outbox.setRetryCount(0);
        outbox.setMaxRetryCount(3);
        outbox.setNextRetryAt(new Date(System.currentTimeMillis() + 1000 * 10)); // 10秒后可重试
        outbox.setCreatedAt(new Date());
        outbox.setUpdatedAt(new Date());
        outboxMapper.insert(outbox);
        
        MessageFacade.ChatPersistResult result;
        try {
            result = messageFacade.sendChat(fromUserId, targetUserId, clientMsgId, msgType, content);
            // 持久化成功，更新状态为SUCCESS
            outbox.setStatus(1);
            outbox.setMessageId(Long.valueOf(result.serverMsgId()));
            outbox.setSentAt(new Date());
            outbox.setUpdatedAt(new Date());
            outboxMapper.updateById(outbox);
        } catch (Exception e) {
            // 发送失败，更新重试次数
            outbox.setRetryCount(outbox.getRetryCount() + 1);
            outbox.setFailReason(e.getMessage());
            if (outbox.getRetryCount() >= outbox.getMaxRetryCount()) {
                outbox.setStatus(-1); // 标记为失败
            } else {
                // 指数退避下次重试时间
                long nextRetryDelay = 1000L * 10 * (long) Math.pow(2, outbox.getRetryCount());
                outbox.setNextRetryAt(new Date(System.currentTimeMillis() + nextRetryDelay));
            }
            outbox.setUpdatedAt(new Date());
            outboxMapper.updateById(outbox);
            throw e;
        }
        ObjectNode ack = protocolSupport.mapper().createObjectNode();
        ack.put("type", "SERVER_ACK");
        if (result.clientMsgId() == null) {
            ack.putNull("clientMsgId");
        } else {
            ack.put("clientMsgId", result.clientMsgId());
        }
        ack.put("serverMsgId", result.serverMsgId());
        ack.put("status", "PERSISTED");
        ack.put("createdNew", result.createdNew());
        protocolSupport.sendJson(context.channel(), ack);
    }

    private void handlePullOffline(WsCommandContext context) throws Exception {
        Long userId = WsCommandHelper.requireUser(context);
        String deviceId = protocolSupport.currentDeviceId(context.channel());
        JsonNode node = context.payload();
        int limit = node.has("limit") ? node.get("limit").asInt(nettyProperties.getSyncBatchSize()) : nettyProperties.getSyncBatchSize();
        int maxLimit = nettyProperties.getOfflinePullMaxLimit() > 0 ? nettyProperties.getOfflinePullMaxLimit() : DEFAULT_PULL_MAX_LIMIT;
        WsCommandHelper.validateLimit(limit, 1, maxLimit);
        SingleSyncCursor syncCursor = parseSingleSyncCursor(node, deviceId);
        if (syncCursor == INVALID_SINGLE_SYNC_CURSOR) {
            return;
        }
        SingleMessagePage pageResult = messageFacade.pullOffline(userId, deviceId, syncCursor, limit);
        ObjectNode resp = protocolSupport.mapper().createObjectNode();
        resp.put("type", "PULL_OFFLINE_RESULT");
        resp.put("hasMore", pageResult.hasMore());
        protocolSupport.writeSingleSyncProgress(resp, deviceId, pageResult.nextCursorCreatedAt(), pageResult.nextCursorId());
        ArrayNode arr = protocolSupport.mapper().createArrayNode();
        for (SingleMessageView message : pageResult.messages()) {
            ObjectNode item = protocolSupport.mapper().createObjectNode();
            protocolSupport.writeSingleMessageNode(item, message);
            arr.add(item);
        }
        resp.set("messages", arr);
        context.channel().writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(protocolSupport.mapper().writeValueAsString(resp)))
                .addListener(future -> {
                    if (future.isSuccess()) {
                        messageFacade.advanceSyncCursor(userId, deviceId, pageResult);
                    }
                });
    }

    private SingleSyncCursor parseSingleSyncCursor(JsonNode node, String currentDeviceId) {
        JsonNode syncToken = node.get("syncToken");
        String cursorCreatedAtStr;
        Long cursorId;
        if (syncToken != null && !syncToken.isNull()) {
            if (!syncToken.isObject()) {
                throw new IllegalArgumentException("syncToken must be an object");
            }
            String chatType = WsCommandHelper.textValue(syncToken.get("chatType"));
            if (chatType != null && !"SINGLE".equalsIgnoreCase(chatType)) {
                throw new IllegalArgumentException("syncToken.chatType must be SINGLE");
            }
            String tokenDeviceId = WsCommandHelper.textValue(syncToken.get("deviceId"));
            if (tokenDeviceId != null && currentDeviceId != null && !tokenDeviceId.equals(currentDeviceId)) {
                throw new IllegalArgumentException("syncToken.deviceId must match current device");
            }
            cursorCreatedAtStr = WsCommandHelper.textValue(syncToken.get("cursorCreatedAt"));
            cursorId = WsCommandHelper.longValue(syncToken.get("cursorId"));
        } else {
            cursorCreatedAtStr = WsCommandHelper.textValue(node.get("cursorCreatedAt"));
            cursorId = WsCommandHelper.longValue(node.get("cursorId"));
        }
        boolean hasCursorCreatedAt = cursorCreatedAtStr != null && !cursorCreatedAtStr.isBlank();
        boolean hasCursorId = cursorId != null;
        if (!hasCursorCreatedAt && Long.valueOf(0L).equals(cursorId)) {
            return null;
        }
        if (hasCursorCreatedAt != hasCursorId) {
            throw new IllegalArgumentException("cursorCreatedAt and cursorId must be provided together");
        }
        if (!hasCursorCreatedAt) {
            return null;
        }
        try {
            return new SingleSyncCursor(java.util.Date.from(Instant.parse(cursorCreatedAtStr)), cursorId);
        } catch (Exception ex) {
            throw new IllegalArgumentException("cursorCreatedAt must be ISO-8601 instant");
        }
    }
    private void handleAckReport(WsCommandContext context) throws Exception {
        JsonNode node = context.payload();
        String serverMsgId = node.path("serverMsgId").asText(null);
        if (serverMsgId == null || serverMsgId.isBlank()) {
            throw new IllegalArgumentException("serverMsgId required");
        }
        String targetStatus = node.path("status").asText(null);
        if (!"DELIVERED".equals(targetStatus) && !"ACKED".equals(targetStatus)) {
            throw new IllegalArgumentException("status must be DELIVERED or ACKED");
        }
        MessageFacade.AckReportResult result = messageFacade.reportAck(context.userId(), serverMsgId, targetStatus);
        
        // 更新本地消息表ACK状态
        if (result.updated() > 0) {
            int ackStatus = "DELIVERED".equals(targetStatus) ? 1 : 2;
            int status = "ACKED".equals(targetStatus) ? 2 : 1;
            outboxMapper.updateAckStatus(Long.parseLong(serverMsgId), result.message().fromUserId(), ackStatus, status);
        }
        
        ObjectNode resp = protocolSupport.mapper().createObjectNode();
        resp.put("type", "ACK_REPORT_RESULT");
        resp.put("serverMsgId", serverMsgId);
        resp.put("status", targetStatus);
        resp.put("updated", result.updated());
        protocolSupport.sendJson(context.channel(), resp);
        if (result.updated() > 0 && !result.statusNotifyAppended()) {
            ObjectNode notify = protocolSupport.mapper().createObjectNode();
            notify.put("type", "MSG_STATUS_NOTIFY");
            notify.put("serverMsgId", serverMsgId);
            notify.put("status", targetStatus);
            notify.put("toUserId", result.message().toUserId());
            String payload = protocolSupport.mapper().writeValueAsString(notify);
            for (Channel channel : channelUserManager.getChannels(result.message().fromUserId())) {
                channel.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(payload));
            }
        }
    }
}
