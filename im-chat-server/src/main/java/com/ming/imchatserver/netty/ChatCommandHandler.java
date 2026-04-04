package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.application.facade.SocialFacade;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.message.MessageContentCodec;
import com.ming.imchatserver.service.MessageService;
import io.netty.channel.Channel;

import java.time.Instant;

/**
 * 单聊命令处理器。
 */
public class ChatCommandHandler implements WsCommandHandler {

    private static final int DEFAULT_PULL_MAX_LIMIT = 200;
    private static final MessageService.SyncCursor INVALID_SINGLE_SYNC_CURSOR = new MessageService.SyncCursor(null, -1L);

    private final MessageFacade messageFacade;
    private final SocialFacade socialFacade;
    private final NettyProperties nettyProperties;
    private final WsProtocolSupport protocolSupport;
    private final ChannelUserManager channelUserManager;

    public ChatCommandHandler(MessageFacade messageFacade,
                              SocialFacade socialFacade,
                              NettyProperties nettyProperties,
                              WsProtocolSupport protocolSupport,
                              ChannelUserManager channelUserManager) {
        this.messageFacade = messageFacade;
        this.socialFacade = socialFacade;
        this.nettyProperties = nettyProperties;
        this.protocolSupport = protocolSupport;
        this.channelUserManager = channelUserManager;
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
        Long fromUserId = context.userId();
        if (fromUserId == null) {
            throw new UnauthorizedWsException("user not bound");
        }
        JsonNode node = context.payload();
        long targetUserId = node.path("targetUserId").asLong(0L);
        if (targetUserId <= 0) {
            throw new IllegalArgumentException("targetUserId must be greater than 0");
        }
        String msgType = MessageContentCodec.normalizeMsgType(node.path("msgType").asText(null));
        String content = MessageContentCodec.validateAndSerializeIncomingContent(msgType, node.get("content"));
        String clientMsgId = normalizeClientMsgId(node.path("clientMsgId").asText(null));
        if (nettyProperties.isSingleChatRequireActiveContact() && !socialFacade.isSingleChatAllowed(fromUserId, targetUserId)) {
            throw new SecurityException("single chat requires active bilateral contacts");
        }
        MessageFacade.ChatPersistResult result = messageFacade.sendChat(fromUserId, targetUserId, clientMsgId, msgType, content);
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
        ObjectNode resp = protocolSupport.mapper().createObjectNode();
        resp.put("type", "ACK_REPORT_RESULT");
        resp.put("serverMsgId", serverMsgId);
        resp.put("status", targetStatus);
        resp.put("updated", result.updated());
        protocolSupport.sendJson(context.channel(), resp);
        if (result.updated() > 0 && !enqueueDistributedNotify(result.message(), result.status())) {
            ObjectNode notify = protocolSupport.mapper().createObjectNode();
            notify.put("type", "MSG_STATUS_NOTIFY");
            notify.put("serverMsgId", serverMsgId);
            notify.put("status", targetStatus);
            notify.put("toUserId", result.message().getToUserId());
            String payload = protocolSupport.mapper().writeValueAsString(notify);
            for (Channel channel : channelUserManager.getChannels(result.message().getFromUserId())) {
                channel.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(payload));
            }
        }
    }

    private boolean enqueueDistributedNotify(MessageDO message, String status) {
        return messageFacade.enqueueStatusNotify(message, status);
    }

    private void handlePullOffline(WsCommandContext context) throws Exception {
        Long userId = context.userId();
        if (userId == null) {
            throw new UnauthorizedWsException("user not bound");
        }
        String deviceId = protocolSupport.currentDeviceId(context.channel());
        JsonNode node = context.payload();
        int limit = node.has("limit") ? node.get("limit").asInt(nettyProperties.getSyncBatchSize()) : nettyProperties.getSyncBatchSize();
        int maxLimit = nettyProperties.getOfflinePullMaxLimit() > 0 ? nettyProperties.getOfflinePullMaxLimit() : DEFAULT_PULL_MAX_LIMIT;
        if (limit < 1 || limit > maxLimit) {
            throw new IllegalArgumentException("limit must be between 1 and " + maxLimit);
        }
        MessageService.SyncCursor syncCursor = parseSingleSyncCursor(node, deviceId);
        if (syncCursor == INVALID_SINGLE_SYNC_CURSOR) {
            return;
        }
        MessageService.CursorPageResult pageResult = messageFacade.pullOffline(userId, deviceId, syncCursor, limit);
        ObjectNode resp = protocolSupport.mapper().createObjectNode();
        resp.put("type", "PULL_OFFLINE_RESULT");
        resp.put("hasMore", pageResult.isHasMore());
        protocolSupport.writeSingleSyncProgress(resp, deviceId, null, pageResult.getNextCursorCreatedAt(), pageResult.getNextCursorId());
        ArrayNode arr = protocolSupport.mapper().createArrayNode();
        for (MessageDO message : pageResult.getMessages()) {
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

    private MessageService.SyncCursor parseSingleSyncCursor(JsonNode node, String currentDeviceId) {
        JsonNode syncToken = node.get("syncToken");
        String cursorCreatedAtStr;
        Long cursorId;
        if (syncToken != null && !syncToken.isNull()) {
            if (!syncToken.isObject()) {
                throw new IllegalArgumentException("syncToken must be an object");
            }
            String chatType = textValue(syncToken.get("chatType"));
            if (chatType != null && !"SINGLE".equalsIgnoreCase(chatType)) {
                throw new IllegalArgumentException("syncToken.chatType must be SINGLE");
            }
            String tokenDeviceId = textValue(syncToken.get("deviceId"));
            if (tokenDeviceId != null && currentDeviceId != null && !tokenDeviceId.equals(currentDeviceId)) {
                throw new IllegalArgumentException("syncToken.deviceId must match current device");
            }
            cursorCreatedAtStr = textValue(syncToken.get("cursorCreatedAt"));
            cursorId = longValue(syncToken.get("cursorId"));
        } else {
            cursorCreatedAtStr = textValue(node.get("cursorCreatedAt"));
            cursorId = longValue(node.get("cursorId"));
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
            return new MessageService.SyncCursor(java.util.Date.from(Instant.parse(cursorCreatedAtStr)), cursorId);
        } catch (Exception ex) {
            throw new IllegalArgumentException("cursorCreatedAt must be ISO-8601 instant");
        }
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private Long longValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asLong();
    }

    private String normalizeClientMsgId(String clientMsgId) {
        if (clientMsgId == null) {
            return null;
        }
        String trimmed = clientMsgId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
