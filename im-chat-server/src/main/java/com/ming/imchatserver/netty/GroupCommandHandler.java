package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.application.facade.SocialFacade;
import com.ming.imchatserver.application.model.GroupMessagePage;
import com.ming.imchatserver.application.model.GroupMessagePersistResult;
import com.ming.imchatserver.application.model.GroupMessageView;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.message.MessageContentCodec;

/**
 * 群组命令处理器。
 */
public class GroupCommandHandler implements WsCommandHandler {

    private static final int DEFAULT_PULL_MAX_LIMIT = 200;
    private static final int DEFAULT_GROUP_PULL_LIMIT = 50;
    private static final long INVALID_GROUP_CURSOR_SEQ = Long.MIN_VALUE;

    private final MessageFacade messageFacade;
    private final SocialFacade socialFacade;
    private final NettyProperties nettyProperties;
    private final WsProtocolSupport protocolSupport;

    public GroupCommandHandler(MessageFacade messageFacade,
                               SocialFacade socialFacade,
                               NettyProperties nettyProperties,
                               WsProtocolSupport protocolSupport) {
        this.messageFacade = messageFacade;
        this.socialFacade = socialFacade;
        this.nettyProperties = nettyProperties;
        this.protocolSupport = protocolSupport;
    }

    @Override
    public boolean supports(String commandType) {
        return "GROUP_JOIN".equals(commandType)
                || "GROUP_QUIT".equals(commandType)
                || "GROUP_MEMBER_LIST".equals(commandType)
                || "GROUP_CHAT".equals(commandType)
                || "GROUP_PULL_OFFLINE".equals(commandType);
    }

    @Override
    public void handle(WsCommandContext context) throws Exception {
        switch (context.commandType()) {
            case "GROUP_JOIN" -> handleGroupJoin(context);
            case "GROUP_QUIT" -> handleGroupQuit(context);
            case "GROUP_MEMBER_LIST" -> handleGroupMemberList(context);
            case "GROUP_CHAT" -> handleGroupChat(context);
            case "GROUP_PULL_OFFLINE" -> handleGroupPullOffline(context);
            default -> throw new IllegalArgumentException("unsupported command: " + context.commandType());
        }
    }

    private void handleGroupJoin(WsCommandContext context) throws Exception {
        Long userId = requireUser(context);
        long groupId = positiveGroupId(context.payload());
        var result = socialFacade.joinGroup(groupId, userId);
        ObjectNode resp = protocolSupport.mapper().createObjectNode();
        resp.put("type", "GROUP_JOIN_RESULT");
        resp.put("groupId", groupId);
        resp.put("success", true);
        resp.put("joined", result.joined());
        resp.put("idempotent", result.idempotent());
        protocolSupport.sendJson(context.channel(), resp);
    }

    private void handleGroupQuit(WsCommandContext context) throws Exception {
        Long userId = requireUser(context);
        long groupId = positiveGroupId(context.payload());
        var result = socialFacade.quitGroup(groupId, userId);
        ObjectNode resp = protocolSupport.mapper().createObjectNode();
        resp.put("type", "GROUP_QUIT_RESULT");
        resp.put("groupId", groupId);
        resp.put("success", true);
        resp.put("quit", result.quit());
        resp.put("idempotent", result.idempotent());
        protocolSupport.sendJson(context.channel(), resp);
    }

    private void handleGroupMemberList(WsCommandContext context) throws Exception {
        Long userId = requireUser(context);
        long groupId = positiveGroupId(context.payload());
        int limit = context.payload().has("limit") ? context.payload().path("limit").asInt(50) : 50;
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        Long cursorUserId = context.payload().has("cursorUserId") && !context.payload().get("cursorUserId").isNull()
                ? context.payload().get("cursorUserId").asLong()
                : null;
        var page = socialFacade.listMembers(groupId, cursorUserId, limit);
        ObjectNode resp = protocolSupport.mapper().createObjectNode();
        resp.put("type", "GROUP_MEMBER_LIST_RESULT");
        resp.put("groupId", groupId);
        resp.put("success", true);
        resp.put("hasMore", page.hasMore());
        if (page.nextCursor() == null) {
            resp.putNull("nextCursor");
        } else {
            resp.put("nextCursor", page.nextCursor());
        }
        protocolSupport.writeMemberList(resp, page.items());
        protocolSupport.sendJson(context.channel(), resp);
    }

    private void handleGroupChat(WsCommandContext context) throws Exception {
        Long fromUserId = requireUser(context);
        long groupId = positiveGroupId(context.payload());
        String msgType = MessageContentCodec.normalizeMsgType(context.payload().path("msgType").asText(null));
        String content = MessageContentCodec.validateAndSerializeIncomingContent(msgType, context.payload().get("content"));
        String clientMsgId = normalizeClientMsgId(context.payload().path("clientMsgId").asText(null));
        GroupMessagePersistResult persistResult = messageFacade.sendGroupChat(groupId, fromUserId, clientMsgId, msgType, content);
        GroupMessageView message = persistResult.message();
        socialFacade.dispatchGroupPush(groupId, message);
    }

    private void handleGroupPullOffline(WsCommandContext context) throws Exception {
        Long userId = requireUser(context);
        long groupId = positiveGroupId(context.payload());
        int defaultLimit = nettyProperties.getSyncBatchSize() > 0 ? nettyProperties.getSyncBatchSize() : DEFAULT_GROUP_PULL_LIMIT;
        int limit = context.payload().has("limit") ? context.payload().path("limit").asInt(defaultLimit) : defaultLimit;
        int maxLimit = nettyProperties.getOfflinePullMaxLimit() > 0 ? nettyProperties.getOfflinePullMaxLimit() : DEFAULT_PULL_MAX_LIMIT;
        if (limit < 1 || limit > maxLimit) {
            throw new IllegalArgumentException("limit must be between 1 and " + maxLimit);
        }
        Long cursorSeq = parseGroupCursorSeq(context.payload(), groupId);
        if (Long.valueOf(INVALID_GROUP_CURSOR_SEQ).equals(cursorSeq)) {
            return;
        }
        GroupMessagePage pullResult = messageFacade.pullGroupOffline(groupId, userId, cursorSeq, limit);
        ObjectNode resp = protocolSupport.mapper().createObjectNode();
        resp.put("type", "GROUP_PULL_OFFLINE_RESULT");
        resp.put("groupId", groupId);
        resp.put("hasMore", pullResult.hasMore());
        protocolSupport.writeGroupSyncProgress(resp, groupId, pullResult.nextCursorSeq());
        ArrayNode messages = protocolSupport.mapper().createArrayNode();
        for (GroupMessageView message : pullResult.messages()) {
            ObjectNode item = protocolSupport.mapper().createObjectNode();
            protocolSupport.writeGroupMessageNode(item, message);
            messages.add(item);
        }
        resp.set("messages", messages);
        protocolSupport.sendJson(context.channel(), resp);
    }

    private Long requireUser(WsCommandContext context) {
        if (context.userId() == null) {
            throw new UnauthorizedWsException("user not bound");
        }
        return context.userId();
    }

    private long positiveGroupId(JsonNode payload) {
        long groupId = payload.path("groupId").asLong(0L);
        if (groupId <= 0) {
            throw new IllegalArgumentException("groupId must be greater than 0");
        }
        return groupId;
    }

    private Long parseGroupCursorSeq(JsonNode node, Long groupId) {
        JsonNode syncToken = node.get("syncToken");
        Long cursorSeq;
        if (syncToken != null && !syncToken.isNull()) {
            if (!syncToken.isObject()) {
                throw new IllegalArgumentException("syncToken must be an object");
            }
            String chatType = textValue(syncToken.get("chatType"));
            if (chatType != null && !"GROUP".equalsIgnoreCase(chatType)) {
                throw new IllegalArgumentException("syncToken.chatType must be GROUP");
            }
            Long tokenGroupId = longValue(syncToken.get("groupId"));
            if (tokenGroupId != null && !tokenGroupId.equals(groupId)) {
                throw new IllegalArgumentException("syncToken.groupId must match groupId");
            }
            cursorSeq = longValue(syncToken.get("cursorSeq"));
        } else {
            cursorSeq = longValue(node.get("cursorSeq"));
        }
        if (cursorSeq != null && cursorSeq < 0L) {
            throw new IllegalArgumentException("cursorSeq must be greater than or equal to 0");
        }
        return cursorSeq;
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
