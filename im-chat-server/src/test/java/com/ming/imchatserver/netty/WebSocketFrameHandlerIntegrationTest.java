package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.mapper.DeliveryMapper;
import com.ming.imchatserver.service.MessageService;
import io.netty.channel.Channel;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private DeliveryMapper deliveryMapper;
    private NettyProperties nettyProperties;

    @BeforeEach
    /**
     * 方法说明。
     */
    void setUp() {
        channelUserManager = mock(ChannelUserManager.class);
        messageService = mock(MessageService.class);
        deliveryMapper = mock(DeliveryMapper.class);
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
        when(messageService.updateStatusByServerMsgId("srv-1", "READ")).thenReturn(1, 0);

        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, nettyProperties, deliveryMapper));
        channel.attr(NettyAttr.USER_ID).set(20L);

        String req = "{\"type\":\"ACK_REPORT\",\"serverMsgId\":\"srv-1\"}";
        channel.writeInbound(new TextWebSocketFrame(req));
        channel.writeInbound(new TextWebSocketFrame(req));

        List<JsonNode> selfOut = readOutboundJson(channel);
        assertEquals(2, selfOut.size());
        assertEquals("ACK_REPORT_RESULT", selfOut.get(0).get("type").asText());
        assertEquals(1, selfOut.get(0).get("updated").asInt());
        assertEquals(0, selfOut.get(1).get("updated").asInt());

        List<JsonNode> notifyOut = readOutboundJson(senderChannel);
        assertEquals(1, notifyOut.size());
        assertEquals("MSG_STATUS_NOTIFY", notifyOut.get(0).get("type").asText());
    }

    @Test
    /**
     * 方法说明。
     */
    void pullOfflineShouldSupportStableCursorPaginationAndValidateParams() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, nettyProperties, deliveryMapper));
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
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, nettyProperties, deliveryMapper));
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
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, nettyProperties, deliveryMapper));
        channel.attr(NettyAttr.USER_ID).set(1L);

        when(messageService.persistMessage(any(MessageDO.class))).thenReturn(new MessageService.PersistResult("existing-1", false));

        String req = "{\"type\":\"CHAT\",\"targetUserId\":2,\"content\":\"hello\",\"clientMsgId\":\"cid-1\"}";
        channel.writeInbound(new TextWebSocketFrame(req));

        JsonNode resp = readOutboundJson(channel).get(0);
        assertEquals("SERVER_ACK", resp.get("type").asText());
        assertEquals("existing-1", resp.get("serverMsgId").asText());
    }

    @Test
    /**
     * 方法说明。
     */
    void chatValidationShouldRejectInvalidTargetAndBlankContent() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler(channelUserManager, messageService, nettyProperties, deliveryMapper));
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
