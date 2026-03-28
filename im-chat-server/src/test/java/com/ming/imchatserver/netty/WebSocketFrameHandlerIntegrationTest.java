package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.dao.ContactDO;
import com.ming.imchatserver.dao.GroupMessageDO;
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.mapper.DeliveryMapper;
import com.ming.imchatserver.metrics.MetricsService;
import com.ming.imchatserver.netty.GroupPushCoordinator;
import com.ming.imchatserver.sensitive.SensitiveWordHitException;
import com.ming.imchatserver.service.ContactService;
import com.ming.imchatserver.service.GroupMessageService;
import com.ming.imchatserver.service.GroupService;
import com.ming.imchatserver.service.MessageService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private MessageService messageService;
    private ContactService contactService;
    private GroupService groupService;
    private GroupMessageService groupMessageService;
    private DeliveryMapper deliveryMapper;
    private MetricsService metricsService;
    private NettyProperties nettyProperties;

    @BeforeEach
    /**
     * 方法说明。
     */
    void setUp() {
        channelUserManager = mock(ChannelUserManager.class);
        messageService = mock(MessageService.class);
        contactService = mock(ContactService.class);
        groupService = mock(GroupService.class);
        groupMessageService = mock(GroupMessageService.class);
        deliveryMapper = mock(DeliveryMapper.class);
        metricsService = mock(MetricsService.class);
        nettyProperties = new NettyProperties();
        nettyProperties.setSyncBatchSize(2);
        nettyProperties.setOfflinePullMaxLimit(200);
    }

    @Test
    /**
     * 方法说明。
     */
    void ackShouldBeIdempotentAndOnlyNotifyOnce() throws Exception {
        EmbeddedChannel senderChannel = new EmbeddedChannel();
        when(channelUserManager.getChannels(10L)).thenReturn(List.of(senderChannel));

        MessageDO saved = new MessageDO();
        saved.setFromUserId(10L);
        saved.setToUserId(20L);
        when(messageService.findByServerMsgId("srv-1")).thenReturn(saved);
        when(messageService.updateStatusByServerMsgId("srv-1", "ACKED")).thenReturn(1, 0);

        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
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
        when(messageService.findByServerMsgId("srv-d")).thenReturn(saved);
        when(messageService.updateStatusByServerMsgId("srv-d", "DELIVERED")).thenReturn(1);

        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
        channel.attr(NettyAttr.USER_ID).set(20L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"ACK_REPORT\",\"serverMsgId\":\"srv-d\",\"status\":\"DELIVERED\"}"));
        JsonNode result = readOutboundJson(channel).get(0);
        JsonNode notify = readOutboundJson(senderChannel).get(0);

        assertEquals("ACK_REPORT_RESULT", result.get("type").asText());
        assertEquals("DELIVERED", result.get("status").asText());
        assertEquals("MSG_STATUS_NOTIFY", notify.get("type").asText());
        assertEquals("DELIVERED", notify.get("status").asText());
    }

    @Test
    void ackReportShouldRejectInvalidStatus() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, metricsService));
        channel.attr(NettyAttr.USER_ID).set(20L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"ACK_REPORT\",\"serverMsgId\":\"srv-bad\",\"status\":\"READ\"}"));
        JsonNode error = readOutboundJson(channel).get(0);

        assertEquals("ERROR", error.get("type").asText());
        assertEquals("INVALID_PARAM", error.get("code").asText());
        verify(messageService, never()).findByServerMsgId(any());
    }

    @Test
    /**
     * 方法说明。
     */
    void pullOfflineShouldSupportStableCursorPaginationAndValidateParams() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
        channel.attr(NettyAttr.USER_ID).set(2L);

        Instant base = Instant.parse("2026-03-25T00:00:00Z");
        MessageDO m1 = msg(1L, "s1", Date.from(base), "c1");
        MessageDO m2 = msg(2L, "s2", Date.from(base), "c2");
        MessageDO m3 = msg(3L, "s3", Date.from(base.plusSeconds(1)), "c3");

        when(messageService.pullRecent(2L, 2)).thenReturn(pageResult(List.of(m1, m2), true));

        String req1 = "{\"type\":\"PULL_OFFLINE\",\"limit\":2}";
        channel.writeInbound(new TextWebSocketFrame(req1));
        JsonNode resp1 = readOutboundJson(channel).get(0);

        assertEquals("PULL_OFFLINE_RESULT", resp1.get("type").asText());
        assertTrue(resp1.get("hasMore").asBoolean());
        assertEquals(2, resp1.get("messages").size());
        assertEquals("s1", resp1.get("messages").get(0).get("serverMsgId").asText());
        assertEquals("s2", resp1.get("messages").get(1).get("serverMsgId").asText());

        String cursorCreatedAt = resp1.get("nextCursorCreatedAt").asText();
        long cursorId = resp1.get("nextCursorId").asLong();
        when(messageService.pullOfflineByCursor(eq(2L), any(Date.class), eq(cursorId), eq(2))).thenReturn(pageResult(List.of(m3), false));

        String req2 = "{\"type\":\"PULL_OFFLINE\",\"limit\":2,\"cursorCreatedAt\":\"" + cursorCreatedAt + "\",\"cursorId\":" + cursorId + "}";
        channel.writeInbound(new TextWebSocketFrame(req2));
        JsonNode resp2 = readOutboundJson(channel).get(0);

        assertEquals("PULL_OFFLINE_RESULT", resp2.get("type").asText());
        assertFalse(resp2.get("hasMore").asBoolean());
        assertEquals(1, resp2.get("messages").size());
        assertEquals("s3", resp2.get("messages").get(0).get("serverMsgId").asText());

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
    /**
     * 方法说明。
     */
    void reconnectSyncShouldUseAccurateHasMoreAndOnlyTriggerOnce() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
        channel.attr(NettyAttr.USER_ID).set(100L);
        channel.attr(NettyAttr.AUTH_OK).set(Boolean.TRUE);

        MessageDO m1 = msg(11L, "s11", Date.from(Instant.parse("2026-03-25T00:00:01Z")), "c11");
        MessageDO m2 = msg(12L, "s12", Date.from(Instant.parse("2026-03-25T00:00:02Z")), "c12");
        when(messageService.pullRecent(100L, 2)).thenReturn(pageResult(List.of(m1, m2), false));

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

        verify(channelUserManager, times(1)).bindUser(any(Channel.class), eq(100L));
    }

    @Test
    /**
     * 方法说明。
     */
    void chatShouldNotPublishEventForIdempotentReplay() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        when(messageService.persistMessage(any(MessageDO.class))).thenReturn(new MessageService.PersistResult("existing-1", false));

        String req = "{\"type\":\"CHAT\",\"targetUserId\":2,\"content\":\"hello\",\"clientMsgId\":\"cid-1\"}";
        channel.writeInbound(new TextWebSocketFrame(req));

        JsonNode resp = readOutboundJson(channel).get(0);
        assertEquals("SERVER_ACK", resp.get("type").asText());
        assertEquals("existing-1", resp.get("serverMsgId").asText());
    }

    @Test
    void chatShouldRejectWhenContactGuardEnabledAndNoBilateralActiveRelation() throws Exception {
        nettyProperties.setSingleChatRequireActiveContact(true);
        when(contactService.isActiveContact(1L, 2L)).thenReturn(true);
        when(contactService.isActiveContact(2L, 1L)).thenReturn(false);
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CHAT\",\"targetUserId\":2,\"content\":\"hello\"}"));
        JsonNode error = readOutboundJson(channel).get(0);

        assertEquals("ERROR", error.get("type").asText());
        assertEquals("FORBIDDEN", error.get("code").asText());
        verify(messageService, never()).persistMessage(any(MessageDO.class));
    }

    @Test
    void chatShouldAllowWhenContactGuardDisabled() throws Exception {
        nettyProperties.setSingleChatRequireActiveContact(false);
        when(messageService.persistMessage(any(MessageDO.class))).thenReturn(new MessageService.PersistResult("srv-allow", true));

        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CHAT\",\"targetUserId\":2,\"content\":\"hello\"}"));
        JsonNode resp = readOutboundJson(channel).get(0);

        assertEquals("SERVER_ACK", resp.get("type").asText());
        assertEquals("srv-allow", resp.get("serverMsgId").asText());
        verify(contactService, never()).isActiveContact(anyLong(), anyLong());
    }

    @Test
    void chatShouldRejectWhenSensitiveWordHit() throws Exception {
        when(messageService.persistMessage(any(MessageDO.class))).thenThrow(new SensitiveWordHitException());

        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(
                channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, metricsService));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CHAT\",\"targetUserId\":2,\"content\":\"badword message\"}"));
        JsonNode error = readOutboundJson(channel).get(0);

        assertEquals("ERROR", error.get("type").asText());
        assertEquals("SENSITIVE_WORD_HIT", error.get("code").asText());
        verify(messageService).persistMessage(any(MessageDO.class));
    }

    @Test
    void chatShouldAllowWhenSensitiveSwitchDisabled() throws Exception {
        when(messageService.persistMessage(any(MessageDO.class))).thenReturn(new MessageService.PersistResult("srv-sensitive-off", true));

        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(
                channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, metricsService));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CHAT\",\"targetUserId\":2,\"content\":\"badword message\"}"));
        JsonNode resp = readOutboundJson(channel).get(0);

        assertEquals("SERVER_ACK", resp.get("type").asText());
        assertEquals("srv-sensitive-off", resp.get("serverMsgId").asText());
    }

    @Test
    void contactAddShouldReturnResultAndValidateParams() throws Exception {
        when(contactService.addOrActivateContact(1L, 2L)).thenReturn(new ContactService.Result(true, false));
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
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

        EmbeddedChannel unauthorized = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
        unauthorized.writeInbound(new TextWebSocketFrame("{\"type\":\"CONTACT_ADD\",\"peerUserId\":2}"));
        JsonNode unauthorizedError = readOutboundJson(unauthorized).get(0);
        assertEquals("UNAUTHORIZED", unauthorizedError.get("code").asText());
    }

    @Test
    void contactRemoveShouldReturnIdempotentResult() throws Exception {
        when(contactService.removeOrDeactivateContact(1L, 2L)).thenReturn(new ContactService.Result(true, true));
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
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
        ContactDO c1 = contact(1L, 2L, 1, "2026-03-25T00:00:00Z");
        ContactDO c2 = contact(1L, 3L, 1, "2026-03-25T00:00:01Z");
        when(contactService.listActiveContacts(1L, 0L, 2))
                .thenReturn(new ContactService.ContactPageResult(List.of(c1, c2), 3L, true));
        when(contactService.listActiveContacts(1L, 3L, 2))
                .thenReturn(new ContactService.ContactPageResult(List.of(), null, false));

        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
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
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CHAT\",\"targetUserId\":0,\"content\":\"hello\"}"));
        JsonNode invalidTarget = readOutboundJson(channel).get(0);
        assertEquals("ERROR", invalidTarget.get("type").asText());
        assertEquals("INVALID_PARAM", invalidTarget.get("code").asText());

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"CHAT\",\"targetUserId\":2,\"content\":\"   \"}"));
        JsonNode blankContent = readOutboundJson(channel).get(0);
        assertEquals("ERROR", blankContent.get("type").asText());
        assertEquals("INVALID_PARAM", blankContent.get("code").asText());

        verify(messageService, never()).persistMessage(any(MessageDO.class));
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
        when(messageService.findByServerMsgId("srv-x")).thenReturn(saved);

        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
        channel.attr(NettyAttr.USER_ID).set(30L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"ACK_REPORT\",\"serverMsgId\":\"srv-x\",\"status\":\"ACKED\"}"));

        List<JsonNode> out = readOutboundJson(channel);
        assertEquals(1, out.size());
        assertEquals("ERROR", out.get(0).get("type").asText());
        assertEquals("FORBIDDEN", out.get(0).get("code").asText());

        verify(messageService, never()).updateStatusByServerMsgId(any(), any());
        verify(deliveryMapper, never()).upsertAck(any(), any(), any(), any());
        verify(channelUserManager, never()).getChannels(anyLong());
        assertTrue(readOutboundJson(senderChannel).isEmpty());
    }

    @Test
    /**
     * 方法说明。
     */
    void groupJoinShouldReturnIdempotentResult() throws Exception {
        when(groupService.joinGroup(101L, 1L)).thenReturn(new GroupService.JoinGroupResult(true, true));
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
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
        when(groupService.quitGroup(101L, 1L)).thenReturn(new GroupService.QuitGroupResult(true, true));
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
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

        GroupMessageDO saved = new GroupMessageDO();
        saved.setGroupId(101L);
        saved.setSeq(7L);
        saved.setServerMsgId("g-srv-7");
        saved.setFromUserId(1L);
        saved.setContent("hello-group");
        saved.setCreatedAt(Date.from(Instant.parse("2026-03-25T00:00:00Z")));
        when(groupMessageService.persistTextMessage(101L, 1L, "c-1", "hello-group"))
                .thenReturn(new GroupMessageService.PersistResult(saved));

        EmbeddedChannel senderChannel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, metricsService));
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
    void groupChatShouldRejectWhenSensitiveWordHit() throws Exception {
        when(groupService.isActiveMember(101L, 1L)).thenReturn(true);
        when(groupMessageService.persistTextMessage(101L, 1L, null, "testban group")).thenThrow(new SensitiveWordHitException());

        EmbeddedChannel senderChannel = new EmbeddedChannel(new WebSocketFrameHandler(
                channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, metricsService));
        senderChannel.attr(NettyAttr.USER_ID).set(1L);

        senderChannel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_CHAT\",\"groupId\":101,\"content\":\"testban group\"}"));
        JsonNode error = readOutboundJson(senderChannel).get(0);

        assertEquals("ERROR", error.get("type").asText());
        assertEquals("SENSITIVE_WORD_HIT", error.get("code").asText());
        verify(groupMessageService).persistTextMessage(101L, 1L, null, "testban group");
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

        GroupMessageDO saved = new GroupMessageDO();
        saved.setGroupId(101L);
        saved.setSeq(10L);
        saved.setServerMsgId("g-srv-10");
        saved.setFromUserId(1L);
        saved.setContent("parallel-batch");
        saved.setCreatedAt(Date.from(Instant.parse("2026-03-25T00:00:00Z")));
        when(groupMessageService.persistTextMessage(101L, 1L, null, "parallel-batch"))
                .thenReturn(new GroupMessageService.PersistResult(saved));

        List<Runnable> submittedTasks = new ArrayList<>();
        EmbeddedChannel senderChannel = new EmbeddedChannel(new WebSocketFrameHandler(
                channelUserManager,
                messageService,
                contactService,
                groupService,
                groupMessageService,
                nettyProperties,
                deliveryMapper,
                metricsService,
                submittedTasks::add
        ));
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

        GroupMessageDO first = new GroupMessageDO();
        first.setGroupId(101L);
        first.setSeq(11L);
        first.setServerMsgId("g-srv-11");
        first.setFromUserId(1L);
        first.setContent("m1");
        first.setCreatedAt(Date.from(Instant.parse("2026-03-25T00:00:00Z")));

        GroupMessageDO second = new GroupMessageDO();
        second.setGroupId(101L);
        second.setSeq(12L);
        second.setServerMsgId("g-srv-12");
        second.setFromUserId(1L);
        second.setContent("m2");
        second.setCreatedAt(Date.from(Instant.parse("2026-03-25T00:00:01Z")));

        when(groupMessageService.persistTextMessage(101L, 1L, null, "m1"))
                .thenReturn(new GroupMessageService.PersistResult(first));
        when(groupMessageService.persistTextMessage(101L, 1L, null, "m2"))
                .thenReturn(new GroupMessageService.PersistResult(second));

        List<Runnable> submittedTasks = new ArrayList<>();
        GroupPushCoordinator coordinator = new GroupPushCoordinator();
        EmbeddedChannel senderChannel = new EmbeddedChannel(new WebSocketFrameHandler(
                channelUserManager,
                messageService,
                contactService,
                groupService,
                groupMessageService,
                nettyProperties,
                deliveryMapper,
                metricsService,
                submittedTasks::add,
                coordinator
        ));
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

        GroupMessageDO saved = new GroupMessageDO();
        saved.setGroupId(101L);
        saved.setSeq(8L);
        saved.setServerMsgId("g-srv-8");
        saved.setFromUserId(1L);
        saved.setContent("boom");
        saved.setCreatedAt(Date.from(Instant.parse("2026-03-25T00:00:00Z")));
        when(groupMessageService.persistTextMessage(101L, 1L, null, "boom"))
                .thenReturn(new GroupMessageService.PersistResult(saved));

        EmbeddedChannel senderChannel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, metricsService, Runnable::run));
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

        GroupMessageDO saved = new GroupMessageDO();
        saved.setGroupId(101L);
        saved.setSeq(9L);
        saved.setServerMsgId("g-srv-9");
        saved.setFromUserId(1L);
        saved.setContent("degrade");
        saved.setCreatedAt(Date.from(Instant.parse("2026-03-25T00:00:00Z")));
        when(groupMessageService.persistTextMessage(101L, 1L, null, "degrade"))
                .thenReturn(new GroupMessageService.PersistResult(saved));

        List<Runnable> acceptedTasks = new ArrayList<>();
        AtomicInteger executeCount = new AtomicInteger(0);

        EmbeddedChannel senderChannel = new EmbeddedChannel(new WebSocketFrameHandler(
                channelUserManager,
                messageService,
                contactService,
                groupService,
                groupMessageService,
                nettyProperties,
                deliveryMapper,
                metricsService,
                command -> {
                    if (executeCount.getAndIncrement() == 0) {
                        acceptedTasks.add(command);
                        return;
                    }
                    throw new RejectedExecutionException("queue full");
                }
        ));
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
        when(groupService.isActiveMember(101L, 1L)).thenReturn(false);
        EmbeddedChannel senderChannel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
        senderChannel.attr(NettyAttr.USER_ID).set(1L);

        senderChannel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_CHAT\",\"groupId\":101,\"content\":\"hello\"}"));
        JsonNode error = readOutboundJson(senderChannel).get(0);

        assertEquals("ERROR", error.get("type").asText());
        assertEquals("FORBIDDEN", error.get("code").asText());
        verify(groupMessageService, never()).persistTextMessage(any(), any(), any(), any());
    }

    @Test
    void groupQuitThenChatShouldBeRejected() throws Exception {
        when(groupService.quitGroup(101L, 1L)).thenReturn(new GroupService.QuitGroupResult(true, false));
        when(groupService.isActiveMember(101L, 1L)).thenReturn(false);

        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_QUIT\",\"groupId\":101}"));
        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_CHAT\",\"groupId\":101,\"content\":\"after-quit\"}"));

        List<JsonNode> out = readOutboundJson(channel);
        assertEquals(2, out.size());
        assertEquals("GROUP_QUIT_RESULT", out.get(0).get("type").asText());
        assertEquals("ERROR", out.get(1).get("type").asText());
        assertEquals("FORBIDDEN", out.get(1).get("code").asText());
        verify(groupMessageService, never()).persistTextMessage(any(), any(), any(), any());
    }

    @Test
    void groupPullOfflineShouldUseCursorAndReturnResult() throws Exception {
        when(groupService.isActiveMember(101L, 1L)).thenReturn(true);
        GroupMessageDO m1 = new GroupMessageDO();
        m1.setGroupId(101L);
        m1.setSeq(11L);
        m1.setServerMsgId("g11");
        m1.setFromUserId(2L);
        m1.setContent("a");
        m1.setCreatedAt(Date.from(Instant.parse("2026-03-25T00:00:00Z")));
        GroupMessageDO m2 = new GroupMessageDO();
        m2.setGroupId(101L);
        m2.setSeq(12L);
        m2.setServerMsgId("g12");
        m2.setFromUserId(3L);
        m2.setContent("b");
        m2.setCreatedAt(Date.from(Instant.parse("2026-03-25T00:00:01Z")));
        when(groupMessageService.pullOffline(101L, 1L, null, 2))
                .thenReturn(new GroupMessageService.PullResult(List.of(m1, m2), false, 12L));

        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_PULL_OFFLINE\",\"groupId\":101,\"limit\":2}"));
        JsonNode result = readOutboundJson(channel).get(0);

        assertEquals("GROUP_PULL_OFFLINE_RESULT", result.get("type").asText());
        assertEquals(101L, result.get("groupId").asLong());
        assertEquals(2, result.get("messages").size());
        assertEquals(12L, result.get("nextCursorSeq").asLong());
    }

    @Test
    void groupPullOfflineShouldKeepAscOrderAndCursorWhenHasMore() throws Exception {
        when(groupService.isActiveMember(101L, 1L)).thenReturn(true);
        GroupMessageDO m1 = new GroupMessageDO();
        m1.setGroupId(101L);
        m1.setSeq(21L);
        m1.setServerMsgId("g21");
        m1.setFromUserId(2L);
        m1.setContent("x");
        m1.setCreatedAt(Date.from(Instant.parse("2026-03-25T00:00:00Z")));
        GroupMessageDO m2 = new GroupMessageDO();
        m2.setGroupId(101L);
        m2.setSeq(22L);
        m2.setServerMsgId("g22");
        m2.setFromUserId(3L);
        m2.setContent("y");
        m2.setCreatedAt(Date.from(Instant.parse("2026-03-25T00:00:01Z")));
        when(groupMessageService.pullOffline(101L, 1L, 20L, 2))
                .thenReturn(new GroupMessageService.PullResult(List.of(m1, m2), true, 22L));

        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, contactService, groupService, groupMessageService, nettyProperties, deliveryMapper, null));
        channel.attr(NettyAttr.USER_ID).set(1L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"GROUP_PULL_OFFLINE\",\"groupId\":101,\"limit\":2,\"cursorSeq\":20}"));
        JsonNode result = readOutboundJson(channel).get(0);

        assertEquals("GROUP_PULL_OFFLINE_RESULT", result.get("type").asText());
        assertTrue(result.get("hasMore").asBoolean());
        assertEquals(22L, result.get("nextCursorSeq").asLong());
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
        m.setContent("hi");
        m.setStatus("SENT");
        m.setCreatedAt(createdAt);
        return m;
    }
    /**
     * 方法说明。
     */
    private MessageService.CursorPageResult pageResult(List<MessageDO> messages, boolean hasMore) {
        Date cursorCreatedAt = null;
        Long cursorId = null;
        if (!messages.isEmpty()) {
            MessageDO last = messages.get(messages.size() - 1);
            cursorCreatedAt = last.getCreatedAt();
            cursorId = last.getId();
        }
        return new MessageService.CursorPageResult(new ArrayList<>(messages), hasMore, cursorCreatedAt, cursorId);
    }

    private ContactDO contact(Long ownerUserId, Long peerUserId, Integer relationStatus, String updatedAt) {
        ContactDO contact = new ContactDO();
        contact.setOwnerUserId(ownerUserId);
        contact.setPeerUserId(peerUserId);
        contact.setRelationStatus(relationStatus);
        contact.setCreatedAt(Date.from(Instant.parse(updatedAt)));
        contact.setUpdatedAt(Date.from(Instant.parse(updatedAt)));
        return contact;
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
