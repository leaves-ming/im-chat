package com.ming.imchatserver.netty;

import com.ming.imapicontract.message.MessageContentCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.application.facade.SocialFacade;
import com.ming.imchatserver.application.model.ContactOperationResult;
import com.ming.imchatserver.application.model.ContactPage;
import com.ming.imchatserver.application.model.ContactView;
import com.ming.imchatserver.application.model.GroupJoinResult;
import com.ming.imchatserver.application.model.GroupMemberPage;
import com.ming.imchatserver.application.model.GroupMemberView;
import com.ming.imchatserver.application.model.GroupMessagePage;
import com.ming.imchatserver.application.model.GroupMessagePersistResult;
import com.ming.imchatserver.application.model.GroupMessageView;
import com.ming.imchatserver.application.model.GroupQuitResult;
import com.ming.imchatserver.application.model.SingleMessagePage;
import com.ming.imchatserver.application.model.SingleMessageView;
import com.ming.imchatserver.application.model.SingleSyncCursor;
import com.ming.imchatserver.application.facade.impl.SocialFacadeImpl;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.metrics.MetricsService;
import com.ming.imchatserver.service.FileTokenBizException;
import com.ming.imchatserver.service.MessageRecallException;
import com.ming.imchatserver.service.remote.RemoteContactService;
import com.ming.imchatserver.service.remote.RemoteGroupService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebSocketFrameHandlerIntegrationTest - 代码模块说明。
 * <p>
 * 本类注释由统一规范补充，描述职责、边界与使用语义。
 */
class WebSocketFrameHandlerIntegrationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ChannelUserManager channelUserManager;
    private MessageFacade messageFacade;
    private RemoteContactService contactService;
    private RemoteGroupService groupService;
    private MetricsService metricsService;
    private NettyProperties nettyProperties;

    @BeforeEach
    /**
     * 方法说明。
     */
    void setUp() {
        channelUserManager = mock(ChannelUserManager.class);
        messageFacade = mock(MessageFacade.class);
        contactService = mock(RemoteContactService.class);
        groupService = mock(RemoteGroupService.class);
        metricsService = mock(MetricsService.class);
        nettyProperties = new NettyProperties();
        nettyProperties.setSyncBatchSize(2);
        nettyProperties.setOfflinePullMaxLimit(200);
    }

    private WebSocketFrameHandler newHandler(MetricsService metricsService) {
        return newHandlerFixture(metricsService).create();
    }

    private WebSocketFrameHandler createHandler(HandlerFixture fixture) {
        return fixture.create();
    }

    private HandlerFixture newHandlerFixture(MetricsService metricsService) {
        return new HandlerFixture(metricsService);
    }

    private final class HandlerFixture {

        private final MetricsService metricsService;
        private Executor groupPushExecutor;
        private GroupPushCoordinator groupPushCoordinator;
        private Executor businessExecutor;

        private HandlerFixture(MetricsService metricsService) {
            this.metricsService = metricsService;
        }

        private HandlerFixture groupPushExecutor(Executor groupPushExecutor) {
            this.groupPushExecutor = groupPushExecutor;
            return this;
        }

        private HandlerFixture groupPushCoordinator(GroupPushCoordinator groupPushCoordinator) {
            this.groupPushCoordinator = groupPushCoordinator;
            return this;
        }

        private HandlerFixture businessExecutor(Executor businessExecutor) {
            this.businessExecutor = businessExecutor;
            return this;
        }

        private WebSocketFrameHandler create() {
            SocialFacade socialFacade = new SocialFacadeImpl(contactService, groupService);
            GroupPushDispatcher groupPushDispatcher = new NettyGroupPushDispatcher(
                    groupService,
                    channelUserManager,
                    groupPushExecutor,
                    groupPushCoordinator,
                    metricsService,
                    nettyProperties
            );
            return new WebSocketFrameHandler(
                    channelUserManager,
                    socialFacade,
                    nettyProperties,
                    groupPushDispatcher,
                    messageFacade,
                    businessExecutor
            );
        }
    }

    private SingleMessageView toSingleMessageView(MessageDO message) {
        if (message == null) {
            return null;
        }
        return new SingleMessageView(message.getId(), message.getServerMsgId(), message.getClientMsgId(), message.getFromUserId(),
                message.getToUserId(), message.getMsgType(), message.getContent(), message.getStatus(), message.getCreatedAt(),
                message.getDeliveredAt(), message.getAckedAt(), message.getRetractedAt(), message.getRetractedBy());
    }

    private MessageFacade.AckReportResult ackResult(MessageDO message, String status, int updated, boolean statusNotifyAppended) {
        return new MessageFacade.AckReportResult(
                toSingleMessageView(message),
                status,
                updated,
                updated > 0 ? new Date() : null,
                statusNotifyAppended
        );
    }


    @Test
    /**
     * 方法说明。
     */
    void ackShouldBeIdempotentAndOnlyNotifyOnce() throws Exception {
        EmbeddedChannel senderChannel = new EmbeddedChannel();
        when(channelUserManager.getChannels(10L)).thenReturn(List.of(senderChannel));

        MessageDO saved = new MessageDO();
        saved.setId(1L);
        saved.setFromUserId(10L);
        saved.setToUserId(20L);
        when(messageFacade.reportAck(20L, "srv-1", "ACKED"))
                .thenReturn(ackResult(saved, "ACKED", 1, false))
                .thenReturn(ackResult(saved, "ACKED", 0, false));

        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(20L);

        String req = "{\"type\":\"ACK_REPORT\",\"serverMsgId\":\"srv-1\",\"status\":\"ACKED\"}";
        channel.writeInbound(new TextWebSocketFrame(req));
        channel.writeInbound(new TextWebSocketFrame(req));

        List<JsonNode> selfOut = readOutboundJson(channel);
        assertEquals(2, selfOut.size());
        assertEquals("ACK_REPORT_RESULT", selfOut.get(0).get("type").asText());
        assertEquals("ACKED", selfOut.get(0).get("status").asText());
        assertEquals(1, selfOut.get(0).get("updated").asInt());
        assertEquals(0, selfOut.get(1).get("updated").asInt());

        List<JsonNode> notifyOut = readOutboundJson(senderChannel);
        assertEquals(1, notifyOut.size());
        assertEquals("MSG_STATUS_NOTIFY", notifyOut.get(0).get("type").asText());
        assertEquals("ACKED", notifyOut.get(0).get("status").asText());
        verify(messageFacade, times(2)).reportAck(20L, "srv-1", "ACKED");
    }

    @Test
    void ackReportShouldMapToDeliveredStatus() throws Exception {
        EmbeddedChannel senderChannel = new EmbeddedChannel();
        when(channelUserManager.getChannels(10L)).thenReturn(List.of(senderChannel));

        MessageDO saved = new MessageDO();
        saved.setId(7L);
        saved.setFromUserId(10L);
        saved.setToUserId(20L);
        saved.setCreatedAt(Date.from(Instant.parse("2026-03-25T00:00:00Z")));
        when(messageFacade.reportAck(20L, "srv-d", "DELIVERED"))
                .thenReturn(ackResult(saved, "DELIVERED", 1, false));

        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(20L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"ACK_REPORT\",\"serverMsgId\":\"srv-d\",\"status\":\"DELIVERED\"}"));
        JsonNode result = readOutboundJson(channel).get(0);
        JsonNode notify = readOutboundJson(senderChannel).get(0);

        assertEquals("ACK_REPORT_RESULT", result.get("type").asText());
        assertEquals("DELIVERED", result.get("status").asText());
        assertEquals("MSG_STATUS_NOTIFY", notify.get("type").asText());
        assertEquals("DELIVERED", notify.get("status").asText());
        verify(messageFacade).reportAck(20L, "srv-d", "DELIVERED");
    }

    @Test
    void ackReportShouldUseDistributedNotifyWhenEnqueueSucceeded() throws Exception {
        EmbeddedChannel senderChannel = new EmbeddedChannel();
        when(channelUserManager.getChannels(10L)).thenReturn(List.of(senderChannel));

        MessageDO saved = new MessageDO();
        saved.setId(8L);
        saved.setFromUserId(10L);
        saved.setToUserId(20L);
        when(messageFacade.reportAck(20L, "srv-mq", "ACKED"))
                .thenReturn(ackResult(saved, "ACKED", 1, true));

        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(20L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"ACK_REPORT\",\"serverMsgId\":\"srv-mq\",\"status\":\"ACKED\"}"));
        JsonNode result = readOutboundJson(channel).get(0);

        assertEquals("ACK_REPORT_RESULT", result.get("type").asText());
        assertNull(senderChannel.readOutbound());
        verify(messageFacade).reportAck(20L, "srv-mq", "ACKED");
    }

    @Test
    void ackReportShouldRejectInvalidStatus() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newHandler(metricsService));
        channel.attr(NettyAttr.USER_ID).set(20L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"ACK_REPORT\",\"serverMsgId\":\"srv-bad\",\"status\":\"READ\"}"));
        JsonNode error = readOutboundJson(channel).get(0);

        assertEquals("ERROR", error.get("type").asText());
        assertEquals("INVALID_PARAM", error.get("code").asText());
        verify(messageFacade, never()).reportAck(any(), any(), any());
    }

    @Test
    /**
     * 方法说明。
     */
    void pullOfflineShouldSupportStableCursorPaginationAndValidateParams() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(2L);

        Instant base = Instant.parse("2026-03-25T00:00:00Z");
        MessageDO m1 = msg(1L, "s1", Date.from(base), "c1");
        MessageDO m2 = msg(2L, "s2", Date.from(base), "c2");
        MessageDO m3 = msg(3L, "s3", Date.from(base.plusSeconds(1)), "c3");

        when(messageFacade.pullOffline(2L, "default", null, 2)).thenReturn(pageResult(List.of(m1, m2), true));

        String req1 = "{\"type\":\"PULL_OFFLINE\",\"limit\":2}";
        channel.writeInbound(new TextWebSocketFrame(req1));
        JsonNode resp1 = readOutboundJson(channel).get(0);

        assertEquals("PULL_OFFLINE_RESULT", resp1.get("type").asText());
        assertTrue(resp1.get("hasMore").asBoolean());
        assertEquals(2, resp1.get("messages").size());
        assertEquals("s1", resp1.get("messages").get(0).get("serverMsgId").asText());
        assertEquals("s2", resp1.get("messages").get(1).get("serverMsgId").asText());
        assertEquals("SINGLE", resp1.get("nextSyncToken").get("chatType").asText());
        verify(messageFacade).advanceSyncCursor(eq(2L), eq("default"), any(SingleMessagePage.class));

        String cursorCreatedAt = resp1.get("nextCursorCreatedAt").asText();
        long cursorId = resp1.get("nextCursorId").asLong();
        when(messageFacade.pullOffline(eq(2L), eq("default"), any(SingleSyncCursor.class), eq(2))).thenReturn(pageResult(List.of(m3), false));

        String req2 = "{\"type\":\"PULL_OFFLINE\",\"limit\":2,\"cursorCreatedAt\":\"" + cursorCreatedAt + "\",\"cursorId\":" + cursorId + "}";
        channel.writeInbound(new TextWebSocketFrame(req2));
        JsonNode resp2 = readOutboundJson(channel).get(0);

        assertEquals("PULL_OFFLINE_RESULT", resp2.get("type").asText());
        assertFalse(resp2.get("hasMore").asBoolean());
        assertEquals(1, resp2.get("messages").size());
        assertEquals("s3", resp2.get("messages").get(0).get("serverMsgId").asText());
        assertEquals(3L, resp2.get("nextSyncToken").get("cursorId").asLong());

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"PULL_OFFLINE\",\"limit\":0}"));
        JsonNode invalidLimit = readOutboundJson(channel).get(0);
        assertEquals("ERROR", invalidLimit.get("type").asText());
        assertEquals("INVALID_PARAM", invalidLimit.get("code").asText());

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"PULL_OFFLINE\",\"limit\":2,\"cursorId\":1}"));
        JsonNode halfCursor = readOutboundJson(channel).get(0);
        assertEquals("INVALID_PARAM", halfCursor.get("code").asText());

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"PULL_OFFLINE\",\"limit\":2,\"cursorCreatedAt\":\"bad-time\",\"cursorId\":1}"));
        JsonNode badDate = readOutboundJson(channel).get(0);
        assertEquals("INVALID_PARAM", badDate.get("code").asText());
    }

    @Test
    void pullOfflineShouldReturnNextSyncTokenOnEmptyPageAndAdvanceOnlyAfterSuccessfulWrite() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(
                newHandler(null)
        );
        channel.attr(NettyAttr.USER_ID).set(2L);

        Date baseCursorTime = Date.from(Instant.parse("2026-03-25T00:00:00Z"));
        when(messageFacade.pullOffline(eq(2L), eq("default"), any(SingleSyncCursor.class), eq(2)))
                .thenReturn(new SingleMessagePage(List.of(), false, baseCursorTime, 99L));

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"PULL_OFFLINE\",\"limit\":2,\"cursorCreatedAt\":\"2026-03-25T00:00:00Z\",\"cursorId\":99}"));
        JsonNode resp = readOutboundJson(channel).get(0);

        assertTrue(resp.get("messages").isEmpty());
        assertEquals("default", resp.get("nextSyncToken").get("deviceId").asText());
        assertEquals(99L, resp.get("nextSyncToken").get("cursorId").asLong());
        verify(messageFacade).advanceSyncCursor(eq(2L), eq("default"), any(SingleMessagePage.class));
    }

    @Test
    void pullOfflineShouldReturnReusableInitSyncTokenWhenNoCheckpointAndNoMessages() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(
                newHandler(null)
        );
        channel.attr(NettyAttr.USER_ID).set(2L);

        when(messageFacade.pullOffline(2L, "default", null, 2))
                .thenReturn(new SingleMessagePage(List.of(), false, null, 0L));

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"PULL_OFFLINE\",\"limit\":2}"));
        JsonNode resp = readOutboundJson(channel).get(0);

        assertTrue(resp.get("messages").isEmpty());
        assertTrue(resp.get("nextCursorCreatedAt").isNull());
        assertEquals(0L, resp.get("nextCursorId").asLong());
        assertEquals("default", resp.get("nextSyncToken").get("deviceId").asText());
        assertEquals(0L, resp.get("nextSyncToken").get("cursorId").asLong());
        verify(messageFacade).advanceSyncCursor(eq(2L), eq("default"), any(SingleMessagePage.class));
    }

    @Test
    void pullOfflineShouldNotAdvanceCheckpointWhenWriteFails() {
        EmbeddedChannel channel = new EmbeddedChannel(
                newHandler(null),
                new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(io.netty.channel.ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                        ReferenceCountUtil.release(msg);
                        promise.setFailure(new RuntimeException("write failed"));
                    }
                }
        );
        channel.attr(NettyAttr.USER_ID).set(2L);

        Date baseCursorTime = Date.from(Instant.parse("2026-03-25T00:00:00Z"));
        when(messageFacade.pullOffline(2L, "default", null, 2))
                .thenReturn(new SingleMessagePage(List.of(), false, baseCursorTime, 99L));

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"PULL_OFFLINE\",\"limit\":2}"));

        verify(messageFacade, never()).advanceSyncCursor(eq(2L), eq("default"), any(SingleMessagePage.class));
    }

    @Test
    void reconnectSyncShouldNotAdvanceCheckpointWhenBatchWriteFails() {
        AtomicInteger writeCount = new AtomicInteger();
        EmbeddedChannel channel = new EmbeddedChannel(
                newHandler(null),
                new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(io.netty.channel.ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                        int current = writeCount.incrementAndGet();
                        if (current == 3) {
                            ReferenceCountUtil.release(msg);
                            promise.setFailure(new RuntimeException("sync batch failed"));
                            return;
                        }
                        ctx.write(msg, promise);
                    }
                }
        );
        channel.attr(NettyAttr.USER_ID).set(100L);
        channel.attr(NettyAttr.AUTH_OK).set(Boolean.TRUE);
        channel.attr(NettyAttr.DEVICE_ID).set("ios-1");

        when(messageFacade.loadInitialSync(100L, "ios-1", 2))
                .thenReturn(pageResult(List.of(), false));

        WebSocketServerProtocolHandler.HandshakeComplete handshake =
                new WebSocketServerProtocolHandler.HandshakeComplete("/ws", new DefaultHttpHeaders(), null);

        channel.pipeline().fireUserEventTriggered(handshake);

        verify(messageFacade, never()).advanceSyncCursor(eq(100L), eq("ios-1"), any(SingleMessagePage.class));
    }

    @Test
    void pullOfflineShouldReturnStructuredFileMessage() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(2L);

        MessageDO fileMessage = msg(10L, "sf-1", Date.from(Instant.parse("2026-03-25T00:00:00Z")), "cf-1");
        fileMessage.setMsgType("FILE");
        fileMessage.setContent("{\"fileId\":\"f10\",\"fileName\":\"spec.pdf\",\"size\":256,\"contentType\":\"application/pdf\",\"url\":\"/files/f10/spec.pdf\"}");
        when(messageFacade.pullOffline(2L, "default", null, 1)).thenReturn(pageResult(List.of(fileMessage), false));

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"PULL_OFFLINE\",\"limit\":1}"));
        JsonNode resp = readOutboundJson(channel).get(0);

        assertEquals("PULL_OFFLINE_RESULT", resp.get("type").asText());
        assertEquals("FILE", resp.get("messages").get(0).get("msgType").asText());
        assertEquals("f10", resp.get("messages").get(0).get("content").get("fileId").asText());
    }

    @Test
    void pullOfflineShouldMaskRetractedMessageContent() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(2L);

        MessageDO recalled = msg(20L, "srv-r1", Date.from(Instant.parse("2026-03-25T00:00:00Z")), "cid-r1");
        recalled.setStatus("RETRACTED");
        recalled.setContent(null);
        recalled.setRetractedAt(Date.from(Instant.parse("2026-03-25T00:01:00Z")));
        recalled.setRetractedBy(1L);
        when(messageFacade.pullOffline(2L, "default", null, 1)).thenReturn(pageResult(List.of(recalled), false));

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"PULL_OFFLINE\",\"limit\":1}"));
        JsonNode resp = readOutboundJson(channel).get(0);

        assertEquals("RETRACTED", resp.get("messages").get(0).get("status").asText());
        assertTrue(resp.get("messages").get(0).get("content").isNull());
        assertEquals(1L, resp.get("messages").get(0).get("retractedBy").asLong());
    }

    @Test
    /**
     * 方法说明。
     */
    void reconnectSyncShouldUseAccurateHasMoreAndOnlyTriggerOnce() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(100L);
        channel.attr(NettyAttr.AUTH_OK).set(Boolean.TRUE);

        MessageDO m1 = msg(11L, "s11", Date.from(Instant.parse("2026-03-25T00:00:01Z")), "c11");
        MessageDO m2 = msg(12L, "s12", Date.from(Instant.parse("2026-03-25T00:00:02Z")), "c12");
        when(messageFacade.loadInitialSync(100L, "default", 2)).thenReturn(pageResult(List.of(m1, m2), false));

        WebSocketServerProtocolHandler.HandshakeComplete handshake =
                new WebSocketServerProtocolHandler.HandshakeComplete("/ws", new DefaultHttpHeaders(), null);

        channel.pipeline().fireUserEventTriggered(handshake);
        channel.pipeline().fireUserEventTriggered(handshake);

        List<JsonNode> out = readOutboundJson(channel);
        assertEquals(3, out.size());
        assertEquals("SYNC_START", out.get(0).get("type").asText());
        assertEquals("SYNC_BATCH", out.get(1).get("type").asText());
        assertEquals(2, out.get(1).get("messages").size());
        assertEquals("SYNC_END", out.get(2).get("type").asText());
        assertFalse(out.get(2).get("hasMore").asBoolean());
        assertEquals("default", out.get(2).get("nextSyncToken").get("deviceId").asText());
        assertEquals("SINGLE", out.get(2).get("nextSyncToken").get("chatType").asText());
        verify(messageFacade).advanceSyncCursor(eq(100L), eq("default"), any(SingleMessagePage.class));

        verify(channelUserManager, times(1)).bindUser(any(Channel.class), eq(100L));
    }

    @Test
    /**
     * 方法说明。
     */
    void chatShouldNotPublishEventForIdempotentReplay() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        when(messageFacade.sendChat(1L, 2L, "cid-1", "TEXT", "hello"))
                .thenReturn(new MessageFacade.ChatPersistResult("cid-1", "existing-1", false));

        String req = "{\"type\":\"CHAT\",\"targetUserId\":2,\"content\":\"hello\",\"clientMsgId\":\"cid-1\"}";
        channel.writeInbound(new TextWebSocketFrame(req));

        JsonNode resp = readOutboundJson(channel).get(0);
        assertEquals("SERVER_ACK", resp.get("type").asText());
        assertEquals("existing-1", resp.get("serverMsgId").asText());
    }

    @Test
    void chatShouldRejectWhenContactGuardEnabledAndNoBilateralActiveRelation() throws Exception {
        nettyProperties.setSingleChatRequireActiveContact(true);
        when(contactService.isSingleChatAllowed(1L, 2L)).thenReturn(false);
        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CHAT\",\"targetUserId\":2,\"content\":\"hello\"}"));
        JsonNode error = readOutboundJson(channel).get(0);

        assertEquals("ERROR", error.get("type").asText());
        assertEquals("FORBIDDEN", error.get("code").asText());
        verify(messageFacade, never()).sendChat(any(), any(), any(), any(), any());
    }

    @Test
    void chatShouldClaimAfterContactValidationAndReleaseOnPersistFailure() throws Exception {
        nettyProperties.setSingleChatRequireActiveContact(true);
        when(contactService.isSingleChatAllowed(1L, 2L)).thenReturn(false);

        EmbeddedChannel forbiddenChannel = new EmbeddedChannel(createHandler(
                newHandlerFixture(metricsService)));
        forbiddenChannel.attr(NettyAttr.USER_ID).set(1L);

        forbiddenChannel.writeInbound(new TextWebSocketFrame("{\"type\":\"CHAT\",\"targetUserId\":2,\"clientMsgId\":\"cid-contact\",\"content\":\"hello\"}"));
        JsonNode forbidden = readOutboundJson(forbiddenChannel).get(0);

        assertEquals("FORBIDDEN", forbidden.get("code").asText());

        when(contactService.isSingleChatAllowed(1L, 2L)).thenReturn(true);
        when(messageFacade.sendChat(1L, 2L, "cid-fail", "TEXT", "hello"))
                .thenThrow(new RuntimeException("db down"));

        EmbeddedChannel persistFailChannel = new EmbeddedChannel(createHandler(
                newHandlerFixture(metricsService)));
        persistFailChannel.attr(NettyAttr.USER_ID).set(1L);

        persistFailChannel.writeInbound(new TextWebSocketFrame("{\"type\":\"CHAT\",\"targetUserId\":2,\"clientMsgId\":\"cid-fail\",\"content\":\"hello\"}"));
        JsonNode error = readOutboundJson(persistFailChannel).get(0);

        assertEquals("INTERNAL_ERROR", error.get("code").asText());
        verify(messageFacade).sendChat(1L, 2L, "cid-fail", "TEXT", "hello");
    }

    @Test
    void chatShouldAllowWhenContactGuardDisabled() throws Exception {
        nettyProperties.setSingleChatRequireActiveContact(false);
        when(messageFacade.sendChat(1L, 2L, null, "TEXT", "hello"))
                .thenReturn(new MessageFacade.ChatPersistResult(null, "srv-allow", true));

        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CHAT\",\"targetUserId\":2,\"content\":\"hello\"}"));
        JsonNode resp = readOutboundJson(channel).get(0);

        assertEquals("SERVER_ACK", resp.get("type").asText());
        assertEquals("srv-allow", resp.get("serverMsgId").asText());
        verify(contactService, never()).isSingleChatAllowed(anyLong(), anyLong());
    }

    @Test
    void chatShouldRejectWhenSensitiveWordHit() throws Exception {
        when(messageFacade.sendChat(1L, 2L, null, "TEXT", "badword message"))
                .thenThrow(new MessageRecallException("SENSITIVE_WORD_HIT", "message contains sensitive words"));

        EmbeddedChannel channel = new EmbeddedChannel(newHandler(metricsService));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CHAT\",\"targetUserId\":2,\"content\":\"badword message\"}"));
        JsonNode error = readOutboundJson(channel).get(0);

        assertEquals("ERROR", error.get("type").asText());
        assertEquals("SENSITIVE_WORD_HIT", error.get("code").asText());
        verify(messageFacade).sendChat(1L, 2L, null, "TEXT", "badword message");
    }

    @Test
    void chatShouldAllowWhenSensitiveSwitchDisabled() throws Exception {
        when(messageFacade.sendChat(1L, 2L, null, "TEXT", "badword message"))
                .thenReturn(new MessageFacade.ChatPersistResult(null, "srv-sensitive-off", true));

        EmbeddedChannel channel = new EmbeddedChannel(newHandler(metricsService));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CHAT\",\"targetUserId\":2,\"content\":\"badword message\"}"));
        JsonNode resp = readOutboundJson(channel).get(0);

        assertEquals("SERVER_ACK", resp.get("type").asText());
        assertEquals("srv-sensitive-off", resp.get("serverMsgId").asText());
    }

    @Test
    void chatShouldSupportFileMessage() throws Exception {
        when(messageFacade.sendChat(eq(1L), eq(2L), eq(null), eq("FILE"), any()))
                .thenReturn(new MessageFacade.ChatPersistResult(null, "srv-file-1", true));

        EmbeddedChannel channel = new EmbeddedChannel(newHandler(metricsService));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("""
                {"type":"CHAT","targetUserId":2,"msgType":"FILE","content":{"uploadToken":"up-1"}}
                """));
        JsonNode resp = readOutboundJson(channel).get(0);

        assertEquals("SERVER_ACK", resp.get("type").asText());
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageFacade).sendChat(eq(1L), eq(2L), eq(null), eq(MessageContentCodec.MSG_TYPE_FILE), contentCaptor.capture());
        assertTrue(contentCaptor.getValue().contains("\"uploadToken\":\"up-1\""));
    }

    @Test
    void chatShouldRejectFileMessageWithExtraFields() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newHandler(metricsService));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("""
                {"type":"CHAT","targetUserId":2,"msgType":"FILE","content":{"uploadToken":"up-1","fileName":"a.txt"}}
                """));
        JsonNode error = readOutboundJson(channel).get(0);

        assertEquals("ERROR", error.get("type").asText());
        assertEquals("INVALID_PARAM", error.get("code").asText());
        verify(messageFacade, never()).sendChat(any(), any(), any(), any(), any());
    }

    @Test
    void chatShouldReturnTokenAlreadyBoundWhenUploadTokenReused() throws Exception {
        when(messageFacade.sendChat(eq(1L), eq(2L), eq(null), eq("FILE"), any()))
                .thenThrow(new FileTokenBizException("TOKEN_ALREADY_BOUND", "uploadToken already bound"));

        EmbeddedChannel channel = new EmbeddedChannel(newHandler(metricsService));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("""
                {"type":"CHAT","targetUserId":2,"msgType":"FILE","content":{"uploadToken":"up-1"}}
                """));
        JsonNode error = readOutboundJson(channel).get(0);

        assertEquals("ERROR", error.get("type").asText());
        assertEquals("TOKEN_ALREADY_BOUND", error.get("code").asText());
    }

    @Test
    void contactAddShouldReturnResultAndValidateParams() throws Exception {
        when(contactService.addOrActivateContact(1L, 2L)).thenReturn(new ContactOperationResult(true, false));
        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CONTACT_ADD\",\"peerUserId\":2}"));
        JsonNode result = readOutboundJson(channel).get(0);
        assertEquals("CONTACT_ADD_RESULT", result.get("type").asText());
        assertTrue(result.get("success").asBoolean());
        assertFalse(result.get("idempotent").asBoolean());
        assertEquals(2L, result.get("peerUserId").asLong());

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CONTACT_ADD\",\"peerUserId\":1}"));
        JsonNode selfError = readOutboundJson(channel).get(0);
        assertEquals("ERROR", selfError.get("type").asText());
        assertEquals("INVALID_PARAM", selfError.get("code").asText());

        EmbeddedChannel unauthorized = new EmbeddedChannel(newHandler(null));
        unauthorized.writeInbound(new TextWebSocketFrame("{\"type\":\"CONTACT_ADD\",\"peerUserId\":2}"));
        JsonNode unauthorizedError = readOutboundJson(unauthorized).get(0);
        assertEquals("UNAUTHORIZED", unauthorizedError.get("code").asText());
    }

    @Test
    void contactRemoveShouldReturnIdempotentResult() throws Exception {
        when(contactService.removeOrDeactivateContact(1L, 2L)).thenReturn(new ContactOperationResult(true, true));
        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CONTACT_REMOVE\",\"peerUserId\":2}"));
        JsonNode result = readOutboundJson(channel).get(0);

        assertEquals("CONTACT_REMOVE_RESULT", result.get("type").asText());
        assertTrue(result.get("success").asBoolean());
        assertTrue(result.get("idempotent").asBoolean());
        assertEquals(2L, result.get("peerUserId").asLong());
    }

    @Test
    void contactListShouldReturnPageAndValidateParams() throws Exception {
        ContactView c1 = contact(1L, 2L, 1, "2026-03-25T00:00:00Z");
        ContactView c2 = contact(1L, 3L, 1, "2026-03-25T00:00:01Z");
        when(contactService.listActiveContacts(1L, 0L, 2))
                .thenReturn(new ContactPage(List.of(c1, c2), 3L, true));
        when(contactService.listActiveContacts(1L, 3L, 2))
                .thenReturn(new ContactPage(List.of(), null, false));

        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CONTACT_LIST\",\"limit\":2,\"cursorPeerUserId\":0}"));
        JsonNode first = readOutboundJson(channel).get(0);
        assertEquals("CONTACT_LIST_RESULT", first.get("type").asText());
        assertTrue(first.get("success").asBoolean());
        assertTrue(first.get("hasMore").asBoolean());
        assertEquals(3L, first.get("nextCursor").asLong());
        assertEquals(2, first.get("items").size());
        assertEquals(2L, first.get("items").get(0).get("peerUserId").asLong());
        assertEquals(1, first.get("items").get(0).get("relationStatus").asInt());

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CONTACT_LIST\",\"limit\":2,\"cursorPeerUserId\":3}"));
        JsonNode second = readOutboundJson(channel).get(0);
        assertFalse(second.get("hasMore").asBoolean());
        assertTrue(second.get("items").isEmpty());
        assertTrue(second.get("nextCursor").isNull());

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CONTACT_LIST\",\"limit\":0}"));
        JsonNode invalidLimit = readOutboundJson(channel).get(0);
        assertEquals("INVALID_PARAM", invalidLimit.get("code").asText());

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CONTACT_LIST\",\"limit\":2,\"cursorPeerUserId\":-1}"));
        JsonNode invalidCursor = readOutboundJson(channel).get(0);
        assertEquals("INVALID_PARAM", invalidCursor.get("code").asText());
    }

    @Test
    /**
     * 方法说明。
     */
    void chatValidationShouldRejectInvalidTargetAndBlankContent() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CHAT\",\"targetUserId\":0,\"content\":\"hello\"}"));
        JsonNode invalidTarget = readOutboundJson(channel).get(0);
        assertEquals("ERROR", invalidTarget.get("type").asText());
        assertEquals("INVALID_PARAM", invalidTarget.get("code").asText());

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CHAT\",\"targetUserId\":2,\"content\":\"   \"}"));
        JsonNode blankContent = readOutboundJson(channel).get(0);
        assertEquals("ERROR", blankContent.get("type").asText());
        assertEquals("INVALID_PARAM", blankContent.get("code").asText());

        verify(messageFacade, never()).sendChat(any(), any(), any(), any(), any());
    }

    @Test
    /**
     * 方法说明。
     */
    void ackReportShouldRejectNonRecipientAndNoSideEffect() throws Exception {
        EmbeddedChannel senderChannel = new EmbeddedChannel();
        when(channelUserManager.getChannels(10L)).thenReturn(List.of(senderChannel));

        MessageDO saved = new MessageDO();
        saved.setId(1L);
        saved.setFromUserId(10L);
        saved.setToUserId(20L);
        saved.setCreatedAt(Date.from(Instant.parse("2026-03-25T00:00:00Z")));
        when(messageFacade.reportAck(30L, "srv-x", "ACKED")).thenThrow(new SecurityException("not message recipient"));

        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(30L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"ACK_REPORT\",\"serverMsgId\":\"srv-x\",\"status\":\"ACKED\"}"));

        List<JsonNode> out = readOutboundJson(channel);
        assertEquals(1, out.size());
        assertEquals("ERROR", out.get(0).get("type").asText());
        assertEquals("FORBIDDEN", out.get(0).get("code").asText());

        verify(messageFacade).reportAck(30L, "srv-x", "ACKED");
        verify(channelUserManager, never()).getChannels(anyLong());
        assertTrue(readOutboundJson(senderChannel).isEmpty());
    }

    @Test
    /**
     * 方法说明。
     */
    void groupJoinShouldReturnIdempotentResult() throws Exception {
        when(groupService.joinGroup(101L, 1L)).thenReturn(new GroupJoinResult(true, true));
        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_JOIN\",\"groupId\":101}"));
        JsonNode resp = readOutboundJson(channel).get(0);

        assertEquals("GROUP_JOIN_RESULT", resp.get("type").asText());
        assertEquals(101L, resp.get("groupId").asLong());
        assertTrue(resp.get("joined").asBoolean());
        assertTrue(resp.get("idempotent").asBoolean());
    }

    @Test
    /**
     * 方法说明。
     */
    void groupQuitShouldReturnIdempotentResult() throws Exception {
        when(groupService.quitGroup(101L, 1L)).thenReturn(new GroupQuitResult(true, true));
        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_QUIT\",\"groupId\":101}"));
        JsonNode resp = readOutboundJson(channel).get(0);

        assertEquals("GROUP_QUIT_RESULT", resp.get("type").asText());
        assertEquals(101L, resp.get("groupId").asLong());
        assertTrue(resp.get("quit").asBoolean());
        assertTrue(resp.get("idempotent").asBoolean());
    }

    @Test
    void groupChatShouldPushToAllOnlineActiveMembers() throws Exception {
        EmbeddedChannel user1Channel = new EmbeddedChannel();
        EmbeddedChannel user2Channel = new EmbeddedChannel();
        when(channelUserManager.getChannels(1L)).thenReturn(List.of(user1Channel));
        when(channelUserManager.getChannels(2L)).thenReturn(List.of(user2Channel));
        when(groupService.isActiveMember(101L, 1L)).thenReturn(true);
        when(groupService.listActiveMemberUserIds(101L)).thenReturn(List.of(1L, 2L));

        GroupMessageView saved = groupMessage(101L, 7L, "g-srv-7", "c-1", 1L,
                "TEXT", "hello-group", 1, "2026-03-25T00:00:00Z");
        when(messageFacade.sendGroupChat(101L, 1L, "c-1", "TEXT", "hello-group"))
                .thenReturn(new GroupMessagePersistResult(saved));

        EmbeddedChannel senderChannel = new EmbeddedChannel(newHandler(metricsService));
        senderChannel.attr(NettyAttr.USER_ID).set(1L);

        senderChannel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_CHAT\",\"groupId\":101,\"clientMsgId\":\"c-1\",\"content\":\"hello-group\"}"));

        List<JsonNode> pushed1 = readOutboundJson(user1Channel);
        List<JsonNode> pushed2 = readOutboundJson(user2Channel);
        assertEquals(1, pushed1.size());
        assertEquals(1, pushed2.size());
        assertEquals("GROUP_MSG_PUSH", pushed1.get(0).get("type").asText());
        assertEquals(101L, pushed1.get(0).get("groupId").asLong());
        assertEquals(7L, pushed1.get(0).get("seq").asLong());
        assertEquals("hello-group", pushed1.get(0).get("content").asText());
        verify(metricsService).incrementGroupPushAttempt(2L);
    }

    @Test
    void groupChatShouldReleaseClientMsgIdOnPersistFailure() throws Exception {
        when(messageFacade.sendGroupChat(101L, 1L, "gc-fail", "TEXT", "hello"))
                .thenThrow(new RuntimeException("db down"));

        EmbeddedChannel senderChannel = new EmbeddedChannel(newHandler(metricsService));
        senderChannel.attr(NettyAttr.USER_ID).set(1L);

        senderChannel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_CHAT\",\"groupId\":101,\"clientMsgId\":\"gc-fail\",\"content\":\"hello\"}"));
        JsonNode error = readOutboundJson(senderChannel).get(0);

        assertEquals("INTERNAL_ERROR", error.get("code").asText());
        verify(messageFacade).sendGroupChat(101L, 1L, "gc-fail", "TEXT", "hello");
    }

    @Test
    void groupChatShouldPushStructuredFileMessage() throws Exception {
        EmbeddedChannel user2Channel = new EmbeddedChannel();
        when(channelUserManager.getChannels(2L)).thenReturn(List.of(user2Channel));
        when(groupService.isActiveMember(101L, 1L)).thenReturn(true);
        when(groupService.listActiveMemberUserIds(101L)).thenReturn(List.of(2L));

        GroupMessageView saved = groupMessage(101L, 13L, "g-srv-file", "c-file", 1L,
                "FILE", "{\"fileId\":\"f1\",\"fileName\":\"a.txt\",\"size\":12,\"contentType\":\"text/plain\",\"url\":\"/files/f1/a.txt\"}",
                1, "2026-03-25T00:00:00Z");
        when(messageFacade.sendGroupChat(eq(101L), eq(1L), eq("c-file"), eq("FILE"), any()))
                .thenReturn(new GroupMessagePersistResult(saved));

        EmbeddedChannel senderChannel = new EmbeddedChannel(newHandler(metricsService));
        senderChannel.attr(NettyAttr.USER_ID).set(1L);

        senderChannel.writeInbound(new TextWebSocketFrame("""
                {"type":"GROUP_CHAT","groupId":101,"clientMsgId":"c-file","msgType":"FILE","content":{"uploadToken":"up-1"}}
                """));

        JsonNode pushed = readOutboundJson(user2Channel).get(0);
        assertEquals("GROUP_MSG_PUSH", pushed.get("type").asText());
        assertEquals("FILE", pushed.get("msgType").asText());
        assertEquals("f1", pushed.get("content").get("fileId").asText());
    }

    @Test
    void msgRecallShouldNotifySingleChatParticipants() throws Exception {
        EmbeddedChannel senderSelf = new EmbeddedChannel();
        EmbeddedChannel recipient = new EmbeddedChannel();
        when(channelUserManager.getChannels(1L)).thenReturn(List.of(senderSelf));
        when(channelUserManager.getChannels(2L)).thenReturn(List.of(recipient));

        MessageDO existing = msg(30L, "srv-recall-1", Date.from(Instant.parse("2026-03-25T00:00:00Z")), "cid-recall-1");
        MessageDO recalled = msg(30L, "srv-recall-1", Date.from(Instant.parse("2026-03-25T00:00:00Z")), "cid-recall-1");
        recalled.setStatus("RETRACTED");
        recalled.setContent(null);
        recalled.setRetractedAt(Date.from(Instant.parse("2026-03-25T00:01:00Z")));
        recalled.setRetractedBy(1L);
        when(messageFacade.recallMessage(1L, "srv-recall-1", 120L)).thenReturn(toSingleMessageView(recalled));

        EmbeddedChannel requester = new EmbeddedChannel(newHandler(null));
        requester.attr(NettyAttr.USER_ID).set(1L);

        requester.writeInbound(new TextWebSocketFrame("{\"type\":\"MSG_RECALL\",\"serverMsgId\":\"srv-recall-1\"}"));

        JsonNode result = readOutboundJson(requester).get(0);

        assertEquals("MSG_RECALL_RESULT", result.get("type").asText());
        assertEquals("cid-recall-1", result.get("clientMsgId").asText());
        assertEquals(1L, result.get("fromUserId").asLong());
        assertEquals(2L, result.get("toUserId").asLong());
        assertEquals("TEXT", result.get("msgType").asText());
        assertEquals("RETRACTED", result.get("status").asText());
        assertTrue(result.get("content").isNull());
        assertEquals("2026-03-25T00:00:00Z", result.get("createdAt").asText());
        assertEquals("2026-03-25T00:01:00Z", result.get("retractedAt").asText());
        JsonNode senderNotify = readOutboundJson(senderSelf).get(0);
        JsonNode recipientNotify = readOutboundJson(recipient).get(0);
        assertEquals("MSG_RECALL_NOTIFY", senderNotify.get("type").asText());
        assertEquals("MSG_RECALL_NOTIFY", recipientNotify.get("type").asText());
        assertEquals("srv-recall-1", senderNotify.get("serverMsgId").asText());
        assertEquals("srv-recall-1", recipientNotify.get("serverMsgId").asText());
        assertEquals(result.get("clientMsgId").asText(), senderNotify.get("clientMsgId").asText());
        assertEquals(result.get("fromUserId").asLong(), senderNotify.get("fromUserId").asLong());
        assertEquals(result.get("toUserId").asLong(), senderNotify.get("toUserId").asLong());
        assertEquals(result.get("msgType").asText(), senderNotify.get("msgType").asText());
        assertEquals(result.get("status").asText(), senderNotify.get("status").asText());
        assertEquals(result.get("createdAt").asText(), senderNotify.get("createdAt").asText());
        assertEquals(result.get("retractedAt").asText(), senderNotify.get("retractedAt").asText());
        assertEquals(result.get("retractedBy").asLong(), senderNotify.get("retractedBy").asLong());
        assertEquals(result.get("clientMsgId").asText(), recipientNotify.get("clientMsgId").asText());
        assertEquals(result.get("fromUserId").asLong(), recipientNotify.get("fromUserId").asLong());
        assertEquals(result.get("toUserId").asLong(), recipientNotify.get("toUserId").asLong());
        assertEquals(result.get("msgType").asText(), recipientNotify.get("msgType").asText());
        assertEquals(result.get("status").asText(), recipientNotify.get("status").asText());
        assertEquals(result.get("createdAt").asText(), recipientNotify.get("createdAt").asText());
        assertEquals(result.get("retractedAt").asText(), recipientNotify.get("retractedAt").asText());
        assertEquals(result.get("retractedBy").asLong(), recipientNotify.get("retractedBy").asLong());
    }

    @Test
    void msgRecallShouldNotifyGroupMembers() throws Exception {
        EmbeddedChannel member2 = new EmbeddedChannel();
        EmbeddedChannel member3 = new EmbeddedChannel();
        when(channelUserManager.getChannels(2L)).thenReturn(List.of(member2));
        when(channelUserManager.getChannels(3L)).thenReturn(List.of(member3));
        when(groupService.listActiveMemberUserIds(101L)).thenReturn(List.of(2L, 3L));

        GroupMessageView existing = groupMessage(101L, 61L, "g-recall-1", null, 1L,
                "TEXT", "hello", 1, "2026-03-25T00:00:00Z");
        GroupMessageView recalled = new GroupMessageView(
                existing.id(),
                existing.groupId(),
                existing.seq(),
                existing.serverMsgId(),
                existing.clientMsgId(),
                existing.fromUserId(),
                existing.msgType(),
                null,
                2,
                existing.createdAt(),
                Date.from(Instant.parse("2026-03-25T00:01:00Z")),
                1L
        );

        when(messageFacade.recallGroupMessage(1L, "g-recall-1", 120L)).thenReturn(recalled);

        EmbeddedChannel requester = new EmbeddedChannel(newHandler(null));
        requester.attr(NettyAttr.USER_ID).set(1L);

        requester.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_MSG_RECALL\",\"serverMsgId\":\"g-recall-1\"}"));

        JsonNode result = readOutboundJson(requester).get(0);
        JsonNode notify2 = readOutboundJson(member2).get(0);
        JsonNode notify3 = readOutboundJson(member3).get(0);

        assertEquals("GROUP_MSG_RECALL_RESULT", result.get("type").asText());
        assertEquals("g-recall-1", result.get("serverMsgId").asText());
        assertTrue(result.get("clientMsgId").isNull());
        assertEquals(101L, result.get("groupId").asLong());
        assertEquals(61L, result.get("seq").asLong());
        assertEquals(1L, result.get("fromUserId").asLong());
        assertEquals("TEXT", result.get("msgType").asText());
        assertEquals("RETRACTED", result.get("status").asText());
        assertTrue(result.get("content").isNull());
        assertEquals("2026-03-25T00:00:00Z", result.get("createdAt").asText());
        assertEquals("2026-03-25T00:01:00Z", result.get("retractedAt").asText());
        assertEquals("GROUP_MSG_RECALL_NOTIFY", notify2.get("type").asText());
        assertEquals("GROUP_MSG_RECALL_NOTIFY", notify3.get("type").asText());
        assertEquals(result.get("serverMsgId").asText(), notify2.get("serverMsgId").asText());
        assertEquals(result.get("groupId").asLong(), notify2.get("groupId").asLong());
        assertEquals(result.get("seq").asLong(), notify2.get("seq").asLong());
        assertEquals(result.get("fromUserId").asLong(), notify2.get("fromUserId").asLong());
        assertEquals(result.get("msgType").asText(), notify2.get("msgType").asText());
        assertEquals(result.get("status").asText(), notify2.get("status").asText());
        assertEquals(result.get("createdAt").asText(), notify2.get("createdAt").asText());
        assertEquals(result.get("retractedAt").asText(), notify2.get("retractedAt").asText());
        assertEquals(result.get("retractedBy").asLong(), notify2.get("retractedBy").asLong());
        assertEquals(result.get("serverMsgId").asText(), notify3.get("serverMsgId").asText());
        assertEquals(result.get("groupId").asLong(), notify3.get("groupId").asLong());
        assertEquals(result.get("seq").asLong(), notify3.get("seq").asLong());
        assertEquals(result.get("fromUserId").asLong(), notify3.get("fromUserId").asLong());
        assertEquals(result.get("msgType").asText(), notify3.get("msgType").asText());
        assertEquals(result.get("status").asText(), notify3.get("status").asText());
        assertEquals(result.get("createdAt").asText(), notify3.get("createdAt").asText());
        assertEquals(result.get("retractedAt").asText(), notify3.get("retractedAt").asText());
        assertEquals(result.get("retractedBy").asLong(), notify3.get("retractedBy").asLong());
    }

    @Test
    void groupChatShouldRejectWhenSensitiveWordHit() throws Exception {
        when(groupService.isActiveMember(101L, 1L)).thenReturn(true);
        when(messageFacade.sendGroupChat(101L, 1L, null, "TEXT", "testban group"))
                .thenThrow(new MessageRecallException("SENSITIVE_WORD_HIT", "message contains sensitive words"));

        EmbeddedChannel senderChannel = new EmbeddedChannel(newHandler(metricsService));
        senderChannel.attr(NettyAttr.USER_ID).set(1L);

        senderChannel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_CHAT\",\"groupId\":101,\"content\":\"testban group\"}"));
        JsonNode error = readOutboundJson(senderChannel).get(0);

        assertEquals("ERROR", error.get("type").asText());
        assertEquals("SENSITIVE_WORD_HIT", error.get("code").asText());
        verify(messageFacade).sendGroupChat(101L, 1L, null, "TEXT", "testban group");
    }

    @Test
    void groupChatShouldSubmitBatchesSeparatelyToExecutor() throws Exception {
        nettyProperties.setGroupPushBatchSize(2);

        EmbeddedChannel user1Channel = new EmbeddedChannel();
        EmbeddedChannel user2Channel = new EmbeddedChannel();
        EmbeddedChannel user3Channel = new EmbeddedChannel();
        when(channelUserManager.getChannels(1L)).thenReturn(List.of(user1Channel));
        when(channelUserManager.getChannels(2L)).thenReturn(List.of(user2Channel));
        when(channelUserManager.getChannels(3L)).thenReturn(List.of(user3Channel));
        when(groupService.isActiveMember(101L, 1L)).thenReturn(true);
        when(groupService.listActiveMemberUserIds(101L)).thenReturn(List.of(1L, 2L, 3L));

        GroupMessageView saved = groupMessage(101L, 10L, "g-srv-10", null, 1L,
                "TEXT", "parallel-batch", 1, "2026-03-25T00:00:00Z");
        when(messageFacade.sendGroupChat(101L, 1L, null, "TEXT", "parallel-batch"))
                .thenReturn(new GroupMessagePersistResult(saved));

        List<Runnable> submittedTasks = new ArrayList<>();
        EmbeddedChannel senderChannel = new EmbeddedChannel(createHandler(
                newHandlerFixture(metricsService)
                        .groupPushExecutor(submittedTasks::add)));
        senderChannel.attr(NettyAttr.USER_ID).set(1L);

        senderChannel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_CHAT\",\"groupId\":101,\"content\":\"parallel-batch\"}"));

        assertEquals(2, submittedTasks.size());
        assertTrue(readOutboundJson(user1Channel).isEmpty());
        assertTrue(readOutboundJson(user2Channel).isEmpty());
        assertTrue(readOutboundJson(user3Channel).isEmpty());

        submittedTasks.forEach(Runnable::run);

        assertEquals(1, readOutboundJson(user1Channel).size());
        assertEquals(1, readOutboundJson(user2Channel).size());
        assertEquals(1, readOutboundJson(user3Channel).size());
        verify(metricsService).incrementGroupPushAttempt(3L);
    }

    @Test
    void groupChatShouldKeepRealtimeOrderWithinSameGroup() throws Exception {
        nettyProperties.setGroupPushBatchSize(1);

        EmbeddedChannel user2Channel = new EmbeddedChannel();
        when(channelUserManager.getChannels(2L)).thenReturn(List.of(user2Channel));
        when(groupService.isActiveMember(101L, 1L)).thenReturn(true);
        when(groupService.listActiveMemberUserIds(101L)).thenReturn(List.of(2L));

        GroupMessageView first = groupMessage(101L, 11L, "g-srv-11", null, 1L,
                "TEXT", "m1", 1, "2026-03-25T00:00:00Z");
        GroupMessageView second = groupMessage(101L, 12L, "g-srv-12", null, 1L,
                "TEXT", "m2", 1, "2026-03-25T00:00:01Z");

        when(messageFacade.sendGroupChat(101L, 1L, null, "TEXT", "m1"))
                .thenReturn(new GroupMessagePersistResult(first));
        when(messageFacade.sendGroupChat(101L, 1L, null, "TEXT", "m2"))
                .thenReturn(new GroupMessagePersistResult(second));

        List<Runnable> submittedTasks = new ArrayList<>();
        GroupPushCoordinator coordinator = new GroupPushCoordinator();
        EmbeddedChannel senderChannel = new EmbeddedChannel(createHandler(
                newHandlerFixture(metricsService)
                        .groupPushExecutor(submittedTasks::add)
                        .groupPushCoordinator(coordinator)));
        senderChannel.attr(NettyAttr.USER_ID).set(1L);

        senderChannel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_CHAT\",\"groupId\":101,\"content\":\"m1\"}"));
        senderChannel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_CHAT\",\"groupId\":101,\"content\":\"m2\"}"));

        assertEquals(1, submittedTasks.size());
        submittedTasks.remove(0).run();
        assertEquals(1, submittedTasks.size());
        submittedTasks.remove(0).run();

        List<JsonNode> pushed = readOutboundJson(user2Channel);
        assertEquals(2, pushed.size());
        assertEquals(11L, pushed.get(0).get("seq").asLong());
        assertEquals(12L, pushed.get(1).get("seq").asLong());
    }

    @Test
    void groupChatShouldCountFailedPushes() throws Exception {
        Channel failedChannel = mock(Channel.class);
        ChannelId failedChannelId = mock(ChannelId.class);
        when(failedChannelId.asShortText()).thenReturn("failed-channel");
        when(failedChannelId.asLongText()).thenReturn("failed-channel");
        when(failedChannel.id()).thenReturn(failedChannelId);
        when(failedChannel.writeAndFlush(any())).thenThrow(new RuntimeException("push failed"));

        when(channelUserManager.getChannels(2L)).thenReturn(List.of(failedChannel));
        when(groupService.isActiveMember(101L, 1L)).thenReturn(true);
        when(groupService.listActiveMemberUserIds(101L)).thenReturn(List.of(2L));

        GroupMessageView saved = groupMessage(101L, 8L, "g-srv-8", null, 1L,
                "TEXT", "boom", 1, "2026-03-25T00:00:00Z");
        when(messageFacade.sendGroupChat(101L, 1L, null, "TEXT", "boom"))
                .thenReturn(new GroupMessagePersistResult(saved));

        EmbeddedChannel senderChannel = new EmbeddedChannel(createHandler(
                newHandlerFixture(metricsService)
                        .groupPushExecutor(Runnable::run)));
        senderChannel.attr(NettyAttr.USER_ID).set(1L);

        senderChannel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_CHAT\",\"groupId\":101,\"content\":\"boom\"}"));

        verify(metricsService).incrementGroupPushAttempt(1L);
        verify(metricsService).incrementGroupPushFail();
    }

    @Test
    void groupChatShouldSkipRealtimeFanoutWhenExecutorRejects() throws Exception {
        nettyProperties.setGroupPushBatchSize(2);

        EmbeddedChannel user2Channel = new EmbeddedChannel();
        EmbeddedChannel user3Channel = new EmbeddedChannel();
        EmbeddedChannel user4Channel = new EmbeddedChannel();
        when(channelUserManager.getChannels(2L)).thenReturn(List.of(user2Channel));
        when(channelUserManager.getChannels(3L)).thenReturn(List.of(user3Channel));
        when(channelUserManager.getChannels(4L)).thenReturn(List.of(user4Channel));
        when(groupService.isActiveMember(101L, 1L)).thenReturn(true);
        when(groupService.listActiveMemberUserIds(101L)).thenReturn(List.of(2L, 3L, 4L));

        GroupMessageView saved = groupMessage(101L, 9L, "g-srv-9", null, 1L,
                "TEXT", "degrade", 1, "2026-03-25T00:00:00Z");
        when(messageFacade.sendGroupChat(101L, 1L, null, "TEXT", "degrade"))
                .thenReturn(new GroupMessagePersistResult(saved));

        List<Runnable> acceptedTasks = new ArrayList<>();
        AtomicInteger executeCount = new AtomicInteger(0);

        EmbeddedChannel senderChannel = new EmbeddedChannel(createHandler(
                newHandlerFixture(metricsService)
                        .groupPushExecutor(command -> {
                    if (executeCount.getAndIncrement() == 0) {
                        acceptedTasks.add(command);
                        return;
                    }
                    throw new RejectedExecutionException("queue full");
                })));
        senderChannel.attr(NettyAttr.USER_ID).set(1L);

        senderChannel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_CHAT\",\"groupId\":101,\"content\":\"degrade\"}"));
        acceptedTasks.forEach(Runnable::run);

        verify(metricsService).incrementGroupPushAttempt(3L);
        verify(metricsService).incrementGroupPushReject();
        assertEquals(1, readOutboundJson(user2Channel).size());
        assertEquals(1, readOutboundJson(user3Channel).size());
        assertTrue(readOutboundJson(user4Channel).isEmpty());
    }

    @Test
    void groupChatShouldRejectNonMember() throws Exception {
        when(messageFacade.sendGroupChat(101L, 1L, null, "TEXT", "hello"))
                .thenThrow(new SecurityException("sender is not active group member"));
        EmbeddedChannel senderChannel = new EmbeddedChannel(newHandler(null));
        senderChannel.attr(NettyAttr.USER_ID).set(1L);

        senderChannel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_CHAT\",\"groupId\":101,\"content\":\"hello\"}"));
        JsonNode error = readOutboundJson(senderChannel).get(0);

        assertEquals("ERROR", error.get("type").asText());
        assertEquals("FORBIDDEN", error.get("code").asText());
        verify(messageFacade).sendGroupChat(101L, 1L, null, "TEXT", "hello");
    }

    @Test
    void groupQuitThenChatShouldBeRejected() throws Exception {
        when(groupService.quitGroup(101L, 1L)).thenReturn(new GroupQuitResult(true, false));
        when(messageFacade.sendGroupChat(101L, 1L, null, "TEXT", "after-quit"))
                .thenThrow(new SecurityException("sender is not active group member"));

        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_QUIT\",\"groupId\":101}"));
        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_CHAT\",\"groupId\":101,\"content\":\"after-quit\"}"));

        List<JsonNode> out = readOutboundJson(channel);
        assertEquals(2, out.size());
        assertEquals("GROUP_QUIT_RESULT", out.get(0).get("type").asText());
        assertEquals("ERROR", out.get(1).get("type").asText());
        assertEquals("FORBIDDEN", out.get(1).get("code").asText());
        verify(messageFacade).sendGroupChat(101L, 1L, null, "TEXT", "after-quit");
    }

    @Test
    void groupPullOfflineShouldUseCursorAndReturnResult() throws Exception {
        when(groupService.isActiveMember(101L, 1L)).thenReturn(true);
        GroupMessageView m1 = groupMessage(101L, 11L, "g11", null, 2L,
                "TEXT", "a", 1, "2026-03-25T00:00:00Z");
        GroupMessageView m2 = groupMessage(101L, 12L, "g12", null, 3L,
                "TEXT", "b", 1, "2026-03-25T00:00:01Z");
        when(messageFacade.pullGroupOffline(101L, 1L, null, 2))
                .thenReturn(new GroupMessagePage(List.of(m1, m2), false, 12L));

        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_PULL_OFFLINE\",\"groupId\":101,\"limit\":2}"));
        JsonNode result = readOutboundJson(channel).get(0);

        assertEquals("GROUP_PULL_OFFLINE_RESULT", result.get("type").asText());
        assertEquals(101L, result.get("groupId").asLong());
        assertEquals(2, result.get("messages").size());
        assertEquals(12L, result.get("nextCursorSeq").asLong());
        assertEquals("GROUP", result.get("nextSyncToken").get("chatType").asText());
    }

    @Test
    void groupPullOfflineShouldReturnStructuredFileContent() throws Exception {
        when(groupService.isActiveMember(101L, 1L)).thenReturn(true);
        GroupMessageView fileMessage = groupMessage(101L, 31L, "g31", null, 2L,
                "FILE", "{\"fileId\":\"f31\",\"fileName\":\"report.pdf\",\"size\":1024,\"contentType\":\"application/pdf\",\"url\":\"/files/f31/report.pdf\"}",
                1, "2026-03-25T00:00:00Z");
        when(messageFacade.pullGroupOffline(101L, 1L, null, 1))
                .thenReturn(new GroupMessagePage(List.of(fileMessage), false, 31L));

        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_PULL_OFFLINE\",\"groupId\":101,\"limit\":1}"));
        JsonNode result = readOutboundJson(channel).get(0);

        assertEquals("FILE", result.get("messages").get(0).get("msgType").asText());
        assertEquals("f31", result.get("messages").get(0).get("content").get("fileId").asText());
    }

    @Test
    void groupPullOfflineShouldMaskRetractedMessageContent() throws Exception {
        when(groupService.isActiveMember(101L, 1L)).thenReturn(true);
        GroupMessageView recalled = new GroupMessageView(
                null, 101L, 41L, "g41", null, 2L, "TEXT", null, 2,
                Date.from(Instant.parse("2026-03-25T00:00:00Z")),
                Date.from(Instant.parse("2026-03-25T00:01:00Z")),
                2L
        );
        when(messageFacade.pullGroupOffline(101L, 1L, null, 1))
                .thenReturn(new GroupMessagePage(List.of(recalled), false, 41L));

        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_PULL_OFFLINE\",\"groupId\":101,\"limit\":1}"));
        JsonNode result = readOutboundJson(channel).get(0);

        assertEquals("RETRACTED", result.get("messages").get(0).get("status").asText());
        assertTrue(result.get("messages").get(0).get("content").isNull());
        assertEquals(2L, result.get("messages").get(0).get("retractedBy").asLong());
    }

    @Test
    void groupPullOfflineShouldKeepAscOrderAndCursorWhenHasMore() throws Exception {
        when(groupService.isActiveMember(101L, 1L)).thenReturn(true);
        GroupMessageView m1 = groupMessage(101L, 21L, "g21", null, 2L,
                "TEXT", "x", 1, "2026-03-25T00:00:00Z");
        GroupMessageView m2 = groupMessage(101L, 22L, "g22", null, 3L,
                "TEXT", "y", 1, "2026-03-25T00:00:01Z");
        when(messageFacade.pullGroupOffline(101L, 1L, 20L, 2))
                .thenReturn(new GroupMessagePage(List.of(m1, m2), true, 22L));

        EmbeddedChannel channel = new EmbeddedChannel(newHandler(null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_PULL_OFFLINE\",\"groupId\":101,\"limit\":2,\"cursorSeq\":20}"));
        JsonNode result = readOutboundJson(channel).get(0);

        assertEquals("GROUP_PULL_OFFLINE_RESULT", result.get("type").asText());
        assertTrue(result.get("hasMore").asBoolean());
        assertEquals(22L, result.get("nextCursorSeq").asLong());
        assertEquals(22L, result.get("nextSyncToken").get("cursorSeq").asLong());
        assertEquals(21L, result.get("messages").get(0).get("seq").asLong());
        assertEquals(22L, result.get("messages").get(1).get("seq").asLong());
    }

    /**
     * 方法说明。
     */
    private MessageDO msg(Long id, String serverMsgId, Date createdAt, String clientMsgId) {
        MessageDO m = new MessageDO();
        m.setId(id);
        m.setServerMsgId(serverMsgId);
        m.setClientMsgId(clientMsgId);
        m.setFromUserId(1L);
        m.setToUserId(2L);
        m.setMsgType("TEXT");
        m.setContent("hi");
        m.setStatus("SENT");
        m.setCreatedAt(createdAt);
        return m;
    }
    /**
     * 方法说明。
     */
    private SingleMessagePage pageResult(List<MessageDO> messages, boolean hasMore) {
        Date cursorCreatedAt = null;
        Long cursorId = null;
        List<SingleMessageView> items = new ArrayList<>();
        for (MessageDO message : messages) {
            items.add(toSingleMessageView(message));
        }
        if (!messages.isEmpty()) {
            MessageDO last = messages.get(messages.size() - 1);
            cursorCreatedAt = last.getCreatedAt();
            cursorId = last.getId();
        }
        return new SingleMessagePage(items, hasMore, cursorCreatedAt, cursorId);
    }

    private ContactView contact(Long ownerUserId, Long peerUserId, Integer relationStatus, String updatedAt) {
        Date instant = Date.from(Instant.parse(updatedAt));
        return new ContactView(ownerUserId, peerUserId, relationStatus, null, null, instant, instant);
    }

    private GroupMessageView groupMessage(Long groupId,
                                          Long seq,
                                          String serverMsgId,
                                          String clientMsgId,
                                          Long fromUserId,
                                          String msgType,
                                          String content,
                                          Integer status,
                                          String createdAt) {
        return new GroupMessageView(
                null,
                groupId,
                seq,
                serverMsgId,
                clientMsgId,
                fromUserId,
                msgType,
                content,
                status,
                Date.from(Instant.parse(createdAt)),
                null,
                null
        );
    }
    /**
     * 方法说明。
     */
    private List<JsonNode> readOutboundJson(EmbeddedChannel channel) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        Object frame;
        while ((frame = channel.readOutbound()) != null) {
            if (frame instanceof TextWebSocketFrame textFrame) {
                out.add(mapper.readTree(textFrame.text()));
            }
        }
        return out;
    }
}
