package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.dao.ContactDO;
import com.ming.imchatserver.dao.GroupMemberDO;
import com.ming.imchatserver.dao.GroupMessageDO;
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.mapper.DeliveryMapper;
import com.ming.imchatserver.metrics.MetricsService;
import com.ming.imchatserver.sensitive.SensitiveWordHitException;
import com.ming.imchatserver.service.ContactService;
import com.ming.imchatserver.service.GroupBizException;
import com.ming.imchatserver.service.GroupMessageService;
import com.ming.imchatserver.service.GroupService;
import com.ming.imchatserver.service.MessageService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
/**
 * WebSocket 业务主处理器。
 * <p>
 * 职责：
 * - 处理握手完成后的用户绑定与重连同步；
 * - 处理文本业务指令（CHAT / ACK_REPORT / PULL_OFFLINE / CONTACT_*）；
 * - 处理心跳帧与连接关闭事件；
 * - 将发送消息的“落库”和“推送”通过事件机制解耦。
 */

    public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketFrameHandler.class);
    private static final int DEFAULT_PULL_MAX_LIMIT = 200;
    private static final int DEFAULT_GROUP_PULL_LIMIT = 50;
    private static final int DEFAULT_CONTACT_LIST_LIMIT = 50;
    private static final int DEFAULT_GROUP_PUSH_BATCH_SIZE = 200;

    private final ChannelUserManager channelUserManager;
    private final ObjectMapper mapper = new ObjectMapper();
    private final MessageService messageService;
    private final ContactService contactService;
    private final GroupService groupService;
    private final GroupMessageService groupMessageService;
    private final NettyProperties nettyProperties;
    private final DeliveryMapper deliveryMapper;
    private final MetricsService metricsService;
    private final Executor groupPushExecutor;
    private final GroupPushCoordinator groupPushCoordinator;
    /**
     * @param channelUserManager 在线连接管理器
     * @param messageService     消息服务（落库、状态更新、分页拉取）
     * @param nettyProperties    Netty 运行参数
     * @param deliveryMapper     投递状态落库组件
     */
    
    public WebSocketFrameHandler(ChannelUserManager channelUserManager,
                                 MessageService messageService,
                                 ContactService contactService,
                                 GroupService groupService,
                                 GroupMessageService groupMessageService,
                                 NettyProperties nettyProperties,
                                 DeliveryMapper deliveryMapper,
                                 MetricsService metricsService) {
        this(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, metricsService, null, null);
    }

    public WebSocketFrameHandler(ChannelUserManager channelUserManager,
                                 MessageService messageService,
                                 ContactService contactService,
                                 GroupService groupService,
                                 GroupMessageService groupMessageService,
                                 NettyProperties nettyProperties,
                                 DeliveryMapper deliveryMapper,
                                 MetricsService metricsService,
                                 Executor groupPushExecutor) {
        this(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, metricsService, groupPushExecutor, null);
    }

    public WebSocketFrameHandler(ChannelUserManager channelUserManager,
                                 MessageService messageService,
                                 ContactService contactService,
                                 GroupService groupService,
                                 GroupMessageService groupMessageService,
                                 NettyProperties nettyProperties,
                                 DeliveryMapper deliveryMapper,
                                 MetricsService metricsService,
                                 Executor groupPushExecutor,
                                 GroupPushCoordinator groupPushCoordinator) {
        this.channelUserManager = channelUserManager;
        this.messageService = messageService;
        this.contactService = contactService;
        this.groupService = groupService;
        this.groupMessageService = groupMessageService;
        this.nettyProperties = nettyProperties;
        this.deliveryMapper = deliveryMapper;
        this.metricsService = metricsService;
        this.groupPushExecutor = groupPushExecutor;
        this.groupPushCoordinator = groupPushCoordinator;
    }

    /**
     * 单元测试兼容构造函数。
     */
    public WebSocketFrameHandler(ChannelUserManager channelUserManager,
                                 MessageService messageService,
                                 NettyProperties nettyProperties,
                                 DeliveryMapper deliveryMapper) {
        this(channelUserManager, messageService, null, null, null, nettyProperties, deliveryMapper, null, null, null);
    }

    @Override
    /**
     * Handler 被加入 pipeline 时触发，仅用于日志观测。
     */
    
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        logger.info("handlerAdded: {}", ctx.channel());
        super.handlerAdded(ctx);
    }

    @Override
    /**
     * Handler 移除时做用户解绑，防止连接映射泄漏。
     */
    
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
        if (userId != null) {
            channelUserManager.unbindUser(ctx.channel(), userId);
            logger.info("channel removed and unbound user {}", userId);
        }
        super.handlerRemoved(ctx);
    }

    @Override
    /**
     * 连接激活事件（此处不做绑定，等待握手鉴权完成后再绑定）。
     */
    
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("channel active: {} (waiting handshake)", ctx.channel());
        super.channelActive(ctx);
    }

    @Override
    /**
     * 处理用户事件，主要监听 WebSocket 握手完成事件并触发重连同步。
     */
    
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.HandshakeComplete) {
            Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
            Boolean auth = ctx.channel().attr(NettyAttr.AUTH_OK).get();
            if (Boolean.TRUE.equals(auth) && userId != null) {
                Boolean bound = ctx.channel().attr(NettyAttr.BOUND).get();
                if (!Boolean.TRUE.equals(bound)) {
                    channelUserManager.bindUser(ctx.channel(), userId);
                    ctx.channel().attr(NettyAttr.BOUND).set(Boolean.TRUE);
                    logger.info("handshake complete and bound user {} to channel {}", userId, ctx.channel().id());
                    triggerSyncAfterHandshake(ctx, userId);
                } else {
                    logger.debug("handshake complete but channel already bound (ignored): user={} channel={}", userId, ctx.channel().id());
                }
            } else {
                logger.warn("handshake complete but no auth info found on channel {}, closing", ctx.channel().id());
                ctx.close();
            }
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    /**
     * WebSocket 帧入口：
     * - Text: 交给业务命令分发
     * - Ping: 回 Pong
     * - Pong: 忽略
     * - Close: 关闭连接
     */
    
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        Channel ch = ctx.channel();
        if (frame instanceof TextWebSocketFrame text) {
            processTextFrame(ch, text.text());
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        if (frame instanceof PongWebSocketFrame) {
            return;
        }
        if (frame instanceof CloseWebSocketFrame) {
            ctx.close();
            return;
        }
        logger.warn("unsupported frame: {}", frame.getClass().getName());
    }
    /**
     * 分发文本业务命令到对应处理函数。
     */
    private void processTextFrame(Channel ch, String textMsg) {
        Long fromUserId = ch.attr(NettyAttr.USER_ID).get();
        logger.debug("recv text from userId={} channel={} msg={}", fromUserId, ch.id(), textMsg);
        try {
            JsonNode node = mapper.readTree(textMsg);
            if (node == null || !node.has("type")) {
                sendError(ch, "INVALID_PARAM", "missing type field");
                return;
            }
            String type = node.path("type").asText();
            switch (type) {
                case "CHAT" -> handleChat(ch, fromUserId, node);
                case "ACK_REPORT" -> handleAckReport(ch, node);
                case "PULL_OFFLINE" -> handlePullOffline(ch, fromUserId, node);
                case "CONTACT_ADD" -> handleContactAdd(ch, fromUserId, node);
                case "CONTACT_REMOVE" -> handleContactRemove(ch, fromUserId, node);
                case "CONTACT_LIST" -> handleContactList(ch, fromUserId, node);
                case "GROUP_JOIN" -> handleGroupJoin(ch, fromUserId, node);
                case "GROUP_QUIT" -> handleGroupQuit(ch, fromUserId, node);
                case "GROUP_MEMBER_LIST" -> handleGroupMemberList(ch, fromUserId, node);
                case "GROUP_CHAT" -> handleGroupChat(ch, fromUserId, node);
                case "GROUP_PULL_OFFLINE" -> handleGroupPullOffline(ch, fromUserId, node);
                default -> sendError(ch, "UNSUPPORTED_CMD", "unsupported command: " + type);
            }
        } catch (GroupBizException ex) {
            sendError(ch, ex.getCode().name(), ex.getMessage());
        } catch (SensitiveWordHitException ex) {
            sendError(ch, ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            logger.error("process text frame error", ex);
            sendError(ch, "INTERNAL_ERROR", "internal error");
        }
    }

    private void handleContactAdd(Channel ch, Long userId, JsonNode node) throws Exception {
        if (userId == null) {
            sendError(ch, "UNAUTHORIZED", "user not bound");
            return;
        }
        if (contactService == null) {
            sendError(ch, "INTERNAL_ERROR", "contact service unavailable");
            return;
        }

        long peerUserId = node.path("peerUserId").asLong(0L);
        if (!isValidContactPeer(userId, peerUserId)) {
            sendError(ch, "INVALID_PARAM", "peerUserId must be greater than 0 and different from self");
            return;
        }

        ContactService.Result result = contactService.addOrActivateContact(userId, peerUserId);
        ObjectNode resp = mapper.createObjectNode();
        resp.put("type", "CONTACT_ADD_RESULT");
        resp.put("peerUserId", peerUserId);
        resp.put("success", result.isSuccess());
        resp.put("idempotent", result.isIdempotent());
        ch.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(resp)));
    }

    private void handleContactRemove(Channel ch, Long userId, JsonNode node) throws Exception {
        if (userId == null) {
            sendError(ch, "UNAUTHORIZED", "user not bound");
            return;
        }
        if (contactService == null) {
            sendError(ch, "INTERNAL_ERROR", "contact service unavailable");
            return;
        }

        long peerUserId = node.path("peerUserId").asLong(0L);
        if (!isValidContactPeer(userId, peerUserId)) {
            sendError(ch, "INVALID_PARAM", "peerUserId must be greater than 0 and different from self");
            return;
        }

        ContactService.Result result = contactService.removeOrDeactivateContact(userId, peerUserId);
        ObjectNode resp = mapper.createObjectNode();
        resp.put("type", "CONTACT_REMOVE_RESULT");
        resp.put("peerUserId", peerUserId);
        resp.put("success", result.isSuccess());
        resp.put("idempotent", result.isIdempotent());
        ch.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(resp)));
    }

    private void handleContactList(Channel ch, Long userId, JsonNode node) throws Exception {
        if (userId == null) {
            sendError(ch, "UNAUTHORIZED", "user not bound");
            return;
        }
        if (contactService == null) {
            sendError(ch, "INTERNAL_ERROR", "contact service unavailable");
            return;
        }

        int defaultLimit = DEFAULT_CONTACT_LIST_LIMIT;
        int maxLimit = nettyProperties.getOfflinePullMaxLimit() > 0 ? nettyProperties.getOfflinePullMaxLimit() : DEFAULT_PULL_MAX_LIMIT;
        int limit = node.has("limit") ? node.path("limit").asInt(defaultLimit) : defaultLimit;
        if (limit < 1 || limit > maxLimit) {
            sendError(ch, "INVALID_PARAM", "limit must be between 1 and " + maxLimit);
            return;
        }

        Long cursorPeerUserId = node.has("cursorPeerUserId") && !node.get("cursorPeerUserId").isNull()
                ? node.get("cursorPeerUserId").asLong()
                : null;
        if (cursorPeerUserId != null && cursorPeerUserId < 0L) {
            sendError(ch, "INVALID_PARAM", "cursorPeerUserId must be greater than or equal to 0");
            return;
        }

        ContactService.ContactPageResult page = contactService.listActiveContacts(userId, cursorPeerUserId, limit);
        ObjectNode resp = mapper.createObjectNode();
        resp.put("type", "CONTACT_LIST_RESULT");
        resp.put("success", true);
        resp.put("hasMore", page.isHasMore());
        if (page.getNextCursor() == null) {
            resp.putNull("nextCursor");
        } else {
            resp.put("nextCursor", page.getNextCursor());
        }

        ArrayNode items = mapper.createArrayNode();
        for (ContactDO contact : page.getItems()) {
            ObjectNode item = mapper.createObjectNode();
            item.put("peerUserId", contact.getPeerUserId());
            item.put("relationStatus", contact.getRelationStatus());
            item.put("createdAt", formatAsInstant(contact.getCreatedAt()));
            item.put("updatedAt", formatAsInstant(contact.getUpdatedAt()));
            items.add(item);
        }
        resp.set("items", items);
        ch.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(resp)));
    }

    private void handleGroupJoin(Channel ch, Long userId, JsonNode node) throws Exception {
        if (userId == null) {
            sendError(ch, "UNAUTHORIZED", "user not bound");
            return;
        }
        long groupId = node.path("groupId").asLong(0L);
        if (groupId <= 0) {
            sendError(ch, "INVALID_PARAM", "groupId must be greater than 0");
            return;
        }
        if (groupService == null) {
            sendError(ch, "INTERNAL_ERROR", "group service unavailable");
            return;
        }

        GroupService.JoinGroupResult result = groupService.joinGroup(groupId, userId);
        ObjectNode resp = mapper.createObjectNode();
        resp.put("type", "GROUP_JOIN_RESULT");
        resp.put("groupId", groupId);
        resp.put("success", true);
        resp.put("joined", result.isJoined());
        resp.put("idempotent", result.isIdempotent());
        ch.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(resp)));
    }

    private void handleGroupQuit(Channel ch, Long userId, JsonNode node) throws Exception {
        if (userId == null) {
            sendError(ch, "UNAUTHORIZED", "user not bound");
            return;
        }
        long groupId = node.path("groupId").asLong(0L);
        if (groupId <= 0) {
            sendError(ch, "INVALID_PARAM", "groupId must be greater than 0");
            return;
        }
        if (groupService == null) {
            sendError(ch, "INTERNAL_ERROR", "group service unavailable");
            return;
        }

        GroupService.QuitGroupResult result = groupService.quitGroup(groupId, userId);
        ObjectNode resp = mapper.createObjectNode();
        resp.put("type", "GROUP_QUIT_RESULT");
        resp.put("groupId", groupId);
        resp.put("success", true);
        resp.put("quit", result.isQuit());
        resp.put("idempotent", result.isIdempotent());
        ch.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(resp)));
    }

    private void handleGroupMemberList(Channel ch, Long userId, JsonNode node) throws Exception {
        if (userId == null) {
            sendError(ch, "UNAUTHORIZED", "user not bound");
            return;
        }
        long groupId = node.path("groupId").asLong(0L);
        if (groupId <= 0) {
            sendError(ch, "INVALID_PARAM", "groupId must be greater than 0");
            return;
        }
        int limit = node.has("limit") ? node.path("limit").asInt(50) : 50;
        if (limit <= 0) {
            sendError(ch, "INVALID_PARAM", "limit must be greater than 0");
            return;
        }
        Long cursorUserId = node.has("cursorUserId") && !node.get("cursorUserId").isNull()
                ? node.get("cursorUserId").asLong()
                : null;
        if (groupService == null) {
            sendError(ch, "INTERNAL_ERROR", "group service unavailable");
            return;
        }

        GroupService.MemberPageResult page = groupService.listMembers(groupId, cursorUserId, limit);
        ObjectNode resp = mapper.createObjectNode();
        resp.put("type", "GROUP_MEMBER_LIST_RESULT");
        resp.put("groupId", groupId);
        resp.put("success", true);
        resp.put("hasMore", page.isHasMore());
        if (page.getNextCursor() == null) {
            resp.putNull("nextCursor");
        } else {
            resp.put("nextCursor", page.getNextCursor());
        }

        ArrayNode items = mapper.createArrayNode();
        for (GroupMemberDO member : page.getItems()) {
            ObjectNode item = mapper.createObjectNode();
            item.put("userId", member.getUserId());
            item.put("role", member.getRole());
            item.put("memberStatus", member.getMemberStatus());
            items.add(item);
        }
        resp.set("items", items);
        ch.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(resp)));
    }

    private void handleGroupChat(Channel ch, Long fromUserId, JsonNode node) throws Exception {
        if (fromUserId == null) {
            sendError(ch, "UNAUTHORIZED", "user not bound");
            return;
        }
        long groupId = node.path("groupId").asLong(0L);
        if (groupId <= 0) {
            sendError(ch, "INVALID_PARAM", "groupId must be greater than 0");
            return;
        }
        String content = node.path("content").asText("");
        if (content == null || content.trim().isEmpty()) {
            sendError(ch, "INVALID_PARAM", "content must not be blank");
            return;
        }
        if (groupService == null || groupMessageService == null) {
            sendError(ch, "INTERNAL_ERROR", "group message service unavailable");
            return;
        }
        if (!groupService.isActiveMember(groupId, fromUserId)) {
            sendError(ch, "FORBIDDEN", "sender is not active group member");
            return;
        }

        String clientMsgId = normalizeClientMsgId(node.path("clientMsgId").asText(null));
        GroupMessageService.PersistResult persistResult = groupMessageService.persistTextMessage(groupId, fromUserId, clientMsgId, content);
        GroupMessageDO message = persistResult.getMessage();

        dispatchGroupPush(groupId, message);
    }

    private void handleGroupPullOffline(Channel ch, Long fromUserId, JsonNode node) throws Exception {
        if (fromUserId == null) {
            sendError(ch, "UNAUTHORIZED", "user not bound");
            return;
        }
        long groupId = node.path("groupId").asLong(0L);
        if (groupId <= 0) {
            sendError(ch, "INVALID_PARAM", "groupId must be greater than 0");
            return;
        }
        if (groupService == null || groupMessageService == null) {
            sendError(ch, "INTERNAL_ERROR", "group message service unavailable");
            return;
        }
        if (!groupService.isActiveMember(groupId, fromUserId)) {
            sendError(ch, "FORBIDDEN", "user is not active group member");
            return;
        }

        int defaultLimit = nettyProperties.getSyncBatchSize() > 0 ? nettyProperties.getSyncBatchSize() : DEFAULT_GROUP_PULL_LIMIT;
        int limit = node.has("limit") ? node.path("limit").asInt(defaultLimit) : defaultLimit;
        int maxLimit = nettyProperties.getOfflinePullMaxLimit() > 0 ? nettyProperties.getOfflinePullMaxLimit() : DEFAULT_PULL_MAX_LIMIT;
        if (limit < 1 || limit > maxLimit) {
            sendError(ch, "INVALID_PARAM", "limit must be between 1 and " + maxLimit);
            return;
        }
        Long cursorSeq = node.has("cursorSeq") && !node.get("cursorSeq").isNull() ? node.get("cursorSeq").asLong() : null;
        if (cursorSeq != null && cursorSeq < 0L) {
            sendError(ch, "INVALID_PARAM", "cursorSeq must be greater than or equal to 0");
            return;
        }

        GroupMessageService.PullResult pullResult = groupMessageService.pullOffline(groupId, fromUserId, cursorSeq, limit);

        ObjectNode resp = mapper.createObjectNode();
        resp.put("type", "GROUP_PULL_OFFLINE_RESULT");
        resp.put("groupId", groupId);
        resp.put("hasMore", pullResult.isHasMore());
        resp.put("nextCursorSeq", pullResult.getNextCursorSeq());
        ArrayNode messages = mapper.createArrayNode();
        for (GroupMessageDO message : pullResult.getMessages()) {
            ObjectNode item = mapper.createObjectNode();
            item.put("type", "TEXT");
            item.put("groupId", message.getGroupId());
            item.put("seq", message.getSeq());
            item.put("serverMsgId", message.getServerMsgId());
            item.put("fromUserId", message.getFromUserId());
            item.put("content", message.getContent());
            item.put("createdAt", formatAsInstant(message.getCreatedAt()));
            messages.add(item);
        }
        resp.set("messages", messages);
        ch.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(resp)));
    }

    private String buildGroupPushPayload(GroupMessageDO message) throws Exception {
        ObjectNode push = mapper.createObjectNode();
        push.put("type", "GROUP_MSG_PUSH");
        push.put("groupId", message.getGroupId());
        push.put("seq", message.getSeq());
        push.put("serverMsgId", message.getServerMsgId());
        push.put("fromUserId", message.getFromUserId());
        push.put("content", message.getContent());
        push.put("createdAt", formatAsInstant(message.getCreatedAt()));
        return mapper.writeValueAsString(push);
    }

    /**
     * 处理聊天发送请求。
     * <p>
     * 先做参数校验与“消息+outbox”事务落库，再直接回发 SERVER_ACK。
     * MQ relay 后续异步分发，不阻塞 Netty I/O 链路。
     */
    private void handleChat(Channel ch, Long fromUserId, JsonNode node) {
        if (fromUserId == null) {
            sendError(ch, "UNAUTHORIZED", "user not bound");
            return;
        }
        long target = node.path("targetUserId").asLong(0L);
        if (target <= 0) {
            sendError(ch, "INVALID_PARAM", "targetUserId must be greater than 0");
            return;
        }
        String content = node.path("content").asText("");
        if (content == null || content.trim().isEmpty()) {
            sendError(ch, "INVALID_PARAM", "content must not be blank");
            return;
        }
        String normalizedClientMsgId = normalizeClientMsgId(node.path("clientMsgId").asText(null));
        if (nettyProperties.isSingleChatRequireActiveContact() && !isSingleChatAllowedByContact(fromUserId, target)) {
            sendError(ch, "FORBIDDEN", "single chat requires active bilateral contacts");
            return;
        }

        MessageDO msg = new MessageDO();
        msg.setClientMsgId(normalizedClientMsgId);
        msg.setFromUserId(fromUserId);
        msg.setToUserId(target);
        msg.setContent(content);
        msg.setStatus("SENT");

        MessageService.PersistResult persistResult = messageService.persistMessage(msg);
        String serverMsgId = persistResult.getServerMsgId();
        sendServerAck(ch, normalizedClientMsgId, serverMsgId, persistResult.isCreatedNew());
    }

    /**
     * 处理接收端上报的送达/已读 ACK，并通知发送端消息状态变化。
     */
    private void handleAckReport(Channel ch, JsonNode node) throws Exception {
        Long reporterUserId = ch.attr(NettyAttr.USER_ID).get();
        String serverMsgId = node.path("serverMsgId").asText(null);
        if (serverMsgId == null || serverMsgId.isBlank()) {
            sendError(ch, "INVALID_PARAM", "serverMsgId required");
            return;
        }
        String targetStatus = node.path("status").asText(null);
        if (!"DELIVERED".equals(targetStatus) && !"ACKED".equals(targetStatus)) {
            sendError(ch, "INVALID_PARAM", "status must be DELIVERED or ACKED");
            return;
        }
        MessageDO m = messageService.findByServerMsgId(serverMsgId);
        if (m == null) {
            sendError(ch, "INVALID_PARAM", "serverMsgId not found");
            return;
        }
        if (reporterUserId == null || !reporterUserId.equals(m.getToUserId())) {
            sendError(ch, "FORBIDDEN", "not message recipient");
            return;
        }
        int updated = messageService.updateStatusByServerMsgId(serverMsgId, targetStatus);

        ObjectNode resp = mapper.createObjectNode();
        resp.put("type", "ACK_REPORT_RESULT");
        resp.put("serverMsgId", serverMsgId);
        resp.put("status", targetStatus);
        resp.put("updated", updated);
        ch.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(resp)));
        if (updated > 0) {
            Date now = new Date();
            if ("ACKED".equals(targetStatus)) {
                deliveryMapper.upsertAck(m.getId(), reporterUserId, now, now);
                recordAckLatency(targetStatus, m.getCreatedAt(), now);
            } else {
                deliveryMapper.upsertAck(m.getId(), reporterUserId, now, null);
                recordAckLatency(targetStatus, m.getCreatedAt(), now);
            }
            ObjectNode notify = mapper.createObjectNode();
            notify.put("type", "MSG_STATUS_NOTIFY");
            notify.put("serverMsgId", serverMsgId);
            notify.put("status", targetStatus);
            notify.put("toUserId", m.getToUserId());
            String payload = mapper.writeValueAsString(notify);
            for (Channel c : channelUserManager.getChannels(m.getFromUserId())) {
                c.writeAndFlush(new TextWebSocketFrame(payload));
            }
        }
    }

    private void recordAckLatency(String ackType, Date createdAt, Date ackAt) {
        if (metricsService == null || createdAt == null || ackAt == null) {
            return;
        }
        metricsService.observeAckLatency(ackType, createdAt.getTime(), ackAt.getTime());
    }

    private void dispatchGroupPush(Long groupId, GroupMessageDO message) throws Exception {
        List<Long> memberUserIds = groupService.listActiveMemberUserIds(groupId);
        List<Channel> targetChannels = new ArrayList<>();
        for (Long userId : memberUserIds) {
            targetChannels.addAll(channelUserManager.getChannels(userId));
        }

        String pushPayload = buildGroupPushPayload(message);
        int attemptedChannels = targetChannels.size();
        if (metricsService != null) {
            metricsService.incrementGroupPushAttempt(attemptedChannels);
        }
        if (attemptedChannels == 0) {
            logger.info("group push skipped, no online channels groupId={} serverMsgId={}", groupId, message.getServerMsgId());
            return;
        }

        int batchSize = nettyProperties.getGroupPushBatchSize() > 0 ? nettyProperties.getGroupPushBatchSize() : DEFAULT_GROUP_PUSH_BATCH_SIZE;
        int batchCount = (attemptedChannels + batchSize - 1) / batchSize;
        if (groupPushCoordinator == null) {
            dispatchGroupPushInOrder(groupId, message.getServerMsgId(), targetChannels, pushPayload, attemptedChannels, batchSize, batchCount);
            return;
        }
        logger.debug("group push enqueued in-order groupId={} serverMsgId={} channels={} batches={}",
                groupId, message.getServerMsgId(), attemptedChannels, batchCount);
        groupPushCoordinator.enqueue(groupId,
                () -> dispatchGroupPushInOrder(groupId, message.getServerMsgId(), targetChannels, pushPayload, attemptedChannels, batchSize, batchCount));
    }

    private CompletableFuture<Void> dispatchGroupPushInOrder(Long groupId,
                                                             String serverMsgId,
                                                             List<Channel> targetChannels,
                                                             String pushPayload,
                                                             int attemptedChannels,
                                                             int batchSize,
                                                             int totalBatchCount) {
        if (groupPushExecutor == null) {
            pushGroupMessageInBatches(groupId, serverMsgId, targetChannels, pushPayload, batchSize);
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Void>> batchFutures = new ArrayList<>();
        int submittedBatchCount = 0;
        for (int start = 0; start < attemptedChannels; start += batchSize) {
            int end = Math.min(start + batchSize, attemptedChannels);
            int batchStart = start;
            int batchEnd = end;
            int currentBatchNo = ++submittedBatchCount;
            List<Channel> batchChannels = new ArrayList<>(targetChannels.subList(batchStart, batchEnd));
            CompletableFuture<Void> batchFuture = new CompletableFuture<>();
            Runnable dispatchTask = () -> {
                try {
                    pushGroupMessageBatch(groupId, serverMsgId, currentBatchNo, batchChannels, pushPayload);
                } finally {
                    batchFuture.complete(null);
                }
            };
            try {
                groupPushExecutor.execute(dispatchTask);
                batchFutures.add(batchFuture);
            } catch (RejectedExecutionException ex) {
                if (metricsService != null) {
                    metricsService.incrementGroupPushReject();
                }
                logger.warn("group push executor rejected, skip realtime batch groupId={} serverMsgId={} batchNo={} batchSize={} attemptedChannels={}",
                        groupId, serverMsgId, currentBatchNo, batchChannels.size(), attemptedChannels, ex);
            }
        }
        logger.info("group push scheduled in-order groupId={} serverMsgId={} channels={} batches={} batchSize={}",
                groupId, serverMsgId, attemptedChannels, totalBatchCount, batchSize);
        return batchFutures.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]));
    }

    private void pushGroupMessageInBatches(Long groupId, String serverMsgId, List<Channel> targetChannels, String payload, int batchSize) {
        int total = targetChannels.size();
        int batchCount = 0;
        for (int start = 0; start < total; start += batchSize) {
            int end = Math.min(start + batchSize, total);
            batchCount++;
            pushGroupMessageBatch(groupId, serverMsgId, batchCount, new ArrayList<>(targetChannels.subList(start, end)), payload);
        }
        logger.info("group push dispatched groupId={} serverMsgId={} channels={} batches={} batchSize={}",
                groupId, serverMsgId, total, batchCount, batchSize);
    }

    private void pushGroupMessageBatch(Long groupId, String serverMsgId, int batchNo, List<Channel> batchChannels, String payload) {
        for (Channel targetChannel : batchChannels) {
            try {
                targetChannel.writeAndFlush(new TextWebSocketFrame(payload)).addListener(future -> {
                    if (!future.isSuccess()) {
                        recordGroupPushFailure(groupId, serverMsgId, targetChannel, future.cause());
                    }
                });
            } catch (Exception ex) {
                recordGroupPushFailure(groupId, serverMsgId, targetChannel, ex);
            }
        }
        logger.debug("group push batch dispatched groupId={} serverMsgId={} batchNo={} channels={}",
                groupId, serverMsgId, batchNo, batchChannels.size());
    }

    private void recordGroupPushFailure(Long groupId, String serverMsgId, Channel channel, Throwable cause) {
        if (metricsService != null) {
            metricsService.incrementGroupPushFail();
        }
        logger.warn("group push failed groupId={} serverMsgId={} channel={}",
                groupId, serverMsgId, channel == null ? "null" : channel.id(), cause);
    }

    private boolean isSingleChatAllowedByContact(Long fromUserId, Long toUserId) {
        if (contactService == null) {
            return true;
        }
        return contactService.isActiveContact(fromUserId, toUserId)
                && contactService.isActiveContact(toUserId, fromUserId);
    }

    private boolean isValidContactPeer(Long ownerUserId, long peerUserId) {
        return peerUserId > 0L && ownerUserId != null && !ownerUserId.equals(peerUserId);
    }

    /**
     * 处理离线消息拉取请求（支持首屏 recent 和游标增量拉取）。
     */
    private void handlePullOffline(Channel ch, Long fromUserId, JsonNode node) throws Exception {
        if (fromUserId == null) {
            sendError(ch, "UNAUTHORIZED", "user not bound");
            return;
        }

        int limit = node.has("limit") ? node.get("limit").asInt(nettyProperties.getSyncBatchSize()) : nettyProperties.getSyncBatchSize();
        int maxLimit = nettyProperties.getOfflinePullMaxLimit() > 0 ? nettyProperties.getOfflinePullMaxLimit() : DEFAULT_PULL_MAX_LIMIT;
        if (limit < 1 || limit > maxLimit) {
            sendError(ch, "INVALID_PARAM", "limit must be between 1 and " + maxLimit);
            return;
        }

        String cursorCreatedAtStr = node.has("cursorCreatedAt") && !node.get("cursorCreatedAt").isNull() ? node.get("cursorCreatedAt").asText() : null;
        Long cursorId = node.has("cursorId") && !node.get("cursorId").isNull() ? node.get("cursorId").asLong() : null;

        boolean hasCursorCreatedAt = cursorCreatedAtStr != null && !cursorCreatedAtStr.isBlank();
        boolean hasCursorId = cursorId != null;
        if (hasCursorCreatedAt != hasCursorId) {
            sendError(ch, "INVALID_PARAM", "cursorCreatedAt and cursorId must be provided together");
            return;
        }

        Date cursorCreatedAt = null;
        if (hasCursorCreatedAt) {
            try {
                cursorCreatedAt = Date.from(Instant.parse(cursorCreatedAtStr));
            } catch (Exception ex) {
                sendError(ch, "INVALID_PARAM", "cursorCreatedAt must be ISO-8601 instant");
                return;
            }
        }

        MessageService.CursorPageResult pageResult = !hasCursorCreatedAt
                ? messageService.pullRecent(fromUserId, limit)
                : messageService.pullOfflineByCursor(fromUserId, cursorCreatedAt, cursorId, limit);
        List<MessageDO> retList = pageResult.getMessages();

        ObjectNode resp = mapper.createObjectNode();
        resp.put("type", "PULL_OFFLINE_RESULT");
        resp.put("hasMore", pageResult.isHasMore());
        if (pageResult.getNextCursorCreatedAt() != null && pageResult.getNextCursorId() != null) {
            resp.put("nextCursorCreatedAt", formatAsInstant(pageResult.getNextCursorCreatedAt()));
            resp.put("nextCursorId", pageResult.getNextCursorId());
        }

        ArrayNode arr = mapper.createArrayNode();
        for (MessageDO m : retList) {
            ObjectNode item = mapper.createObjectNode();
            item.put("serverMsgId", m.getServerMsgId());
            item.put("clientMsgId", m.getClientMsgId());
            item.put("fromUserId", m.getFromUserId());
            item.put("toUserId", m.getToUserId());
            item.put("content", m.getContent());
            item.put("status", m.getStatus());
            item.put("createdAt", formatAsInstant(m.getCreatedAt()));
            arr.add(item);
        }
        resp.set("messages", arr);
        ch.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(resp)));
    }

    /**
     * 规范化 clientMsgId：空白字符串转 null，统一幂等键语义。
     */
    private String normalizeClientMsgId(String clientMsgId) {
        if (clientMsgId == null) {
            return null;
        }
        String trimmed = clientMsgId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 将 Date 序列化为 ISO-8601 字符串（UTC instant）。
     */
    private String formatAsInstant(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().toString();
    }

    /**
     * 发送统一错误响应帧。
     */
    private void sendError(Channel ch, String code, String msg) {
        try {
            ObjectNode err = mapper.createObjectNode();
            err.put("type", "ERROR");
            err.put("code", code);
            err.put("msg", msg);
            ch.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(err)));
        } catch (Exception ex) {
            logger.error("sendError failed", ex);
            ch.writeAndFlush(new TextWebSocketFrame("{\"type\":\"ERROR\",\"code\":\"INTERNAL_ERROR\",\"msg\":\"internal error\"}"));
        }
    }

    /**
     * 发送发送端投递确认（DELIVER_ACK）。
     */
    private void sendServerAck(Channel ch, String clientMsgId, String serverMsgId, boolean createdNew) {
        try {
            ObjectNode ack = mapper.createObjectNode();
            ack.put("type", "SERVER_ACK");
            if (clientMsgId != null) {
                ack.put("clientMsgId", clientMsgId);
            } else {
                ack.putNull("clientMsgId");
            }
            ack.put("serverMsgId", serverMsgId);
            ack.put("status", "PERSISTED");
            ack.put("createdNew", createdNew);
            ch.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(ack)));
        } catch (Exception ex) {
            logger.error("sendServerAck failed", ex);
        }
    }

    /**
     * 握手成功后的最小同步策略：
     * - 先发 SYNC_START
     * - 再发一批 SYNC_BATCH（最近消息）
     * - 最后发 SYNC_END + hasMore
     */
    private void triggerSyncAfterHandshake(ChannelHandlerContext ctx, Long userId) throws Exception {
        ObjectNode start = mapper.createObjectNode();
        start.put("type", "SYNC_START");
        start.put("userId", userId);
        ctx.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(start)));

        int batch = nettyProperties.getSyncBatchSize();
        MessageService.CursorPageResult pageResult = messageService.pullRecent(userId, batch);
        List<MessageDO> list = pageResult.getMessages();

        ObjectNode batchNode = mapper.createObjectNode();
        batchNode.put("type", "SYNC_BATCH");
        ArrayNode arr = mapper.createArrayNode();
        for (MessageDO m : list) {
            ObjectNode mi = mapper.createObjectNode();
            mi.put("serverMsgId", m.getServerMsgId());
            mi.put("clientMsgId", m.getClientMsgId());
            mi.put("fromUserId", m.getFromUserId());
            mi.put("toUserId", m.getToUserId());
            mi.put("content", m.getContent());
            mi.put("status", m.getStatus());
            mi.put("createdAt", formatAsInstant(m.getCreatedAt()));
            arr.add(mi);
        }
        batchNode.set("messages", arr);
        ctx.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(batchNode)));

        ObjectNode end = mapper.createObjectNode();
        end.put("type", "SYNC_END");
        end.put("hasMore", pageResult.isHasMore());
        ctx.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(end)));
    }

    @Override
    /**
     * 业务处理异常兜底：记录日志、解绑并关闭连接。
     */
    
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("websocket handler exception", cause);
        Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
        logger.warn("exception on channel {} userId={}, closing", ctx.channel().id(), userId);
        channelUserManager.unbindByChannel(ctx.channel());
        ctx.close();
    }

    @Override
    /**
     * 连接断开时清理用户绑定关系。
     */
    
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Long userId = ctx.channel().attr(NettyAttr.USER_ID).get();
        logger.info("channel inactive {} userId={}", ctx.channel().id(), userId);
        channelUserManager.unbindByChannel(ctx.channel());
        super.channelInactive(ctx);
    }
}
