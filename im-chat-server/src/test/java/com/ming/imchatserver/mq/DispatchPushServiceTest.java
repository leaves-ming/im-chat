package com.ming.imchatserver.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.config.RedisStateProperties;
import com.ming.imchatserver.netty.ChannelUserManager;
import com.ming.imchatserver.service.GroupMessageService;
import com.ming.imchatserver.service.GroupService;
import com.ming.imchatserver.service.MessageService;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DispatchPushServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void dispatchSingleShouldHandleExplicitRecallEvent() throws Exception {
        ChannelUserManager channelUserManager = mock(ChannelUserManager.class);
        MessageService messageService = mock(MessageService.class);
        GroupMessageService groupMessageService = mock(GroupMessageService.class);
        GroupService groupService = mock(GroupService.class);
        EmbeddedChannel recipient = new EmbeddedChannel();
        EmbeddedChannel senderOther = new EmbeddedChannel();
        when(channelUserManager.getChannels(2L)).thenReturn(List.of(recipient));
        when(channelUserManager.getChannels(1L)).thenReturn(List.of(senderOther));

        RedisStateProperties redisStateProperties = new RedisStateProperties();
        redisStateProperties.setServerId("node-b");
        DispatchPushService service = new DispatchPushService(channelUserManager, messageService, groupMessageService, groupService, redisStateProperties);
        DispatchMessagePayload payload = new DispatchMessagePayload();
        payload.setEventType(DispatchMessagePayload.EVENT_TYPE_RECALL);
        payload.setOriginServerId("node-a");
        payload.setServerMsgId("srv-r1");
        payload.setClientMsgId("cid-r1");
        payload.setFromUserId(1L);
        payload.setToUserId(2L);
        payload.setMsgType("TEXT");
        payload.setStatus("RETRACTED");
        payload.setCreatedAt("2026-03-25T00:00:00Z");
        payload.setRetractedAt(new Date().toInstant().toString());
        payload.setRetractedBy(1L);

        service.dispatchSingle(payload);

        TextWebSocketFrame frame = recipient.readOutbound();
        JsonNode notify = mapper.readTree(frame.text());
        assertEquals("MSG_RECALL_NOTIFY", notify.get("type").asText());
        assertEquals("cid-r1", notify.get("clientMsgId").asText());
        assertEquals(1L, notify.get("fromUserId").asLong());
        assertEquals(2L, notify.get("toUserId").asLong());
        assertEquals("TEXT", notify.get("msgType").asText());
        assertEquals("RETRACTED", notify.get("status").asText());
        assertEquals("2026-03-25T00:00:00Z", notify.get("createdAt").asText());
        assertNull(notify.get("content").textValue());

        TextWebSocketFrame senderFrame = senderOther.readOutbound();
        JsonNode senderNotify = mapper.readTree(senderFrame.text());
        assertEquals("MSG_RECALL_NOTIFY", senderNotify.get("type").asText());
        assertEquals(notify, senderNotify);
    }

    @Test
    void dispatchSingleShouldSkipSenderOnOriginNodeForRecallEvent() throws Exception {
        ChannelUserManager channelUserManager = mock(ChannelUserManager.class);
        MessageService messageService = mock(MessageService.class);
        GroupMessageService groupMessageService = mock(GroupMessageService.class);
        GroupService groupService = mock(GroupService.class);
        EmbeddedChannel recipient = new EmbeddedChannel();
        EmbeddedChannel senderOther = new EmbeddedChannel();
        when(channelUserManager.getChannels(2L)).thenReturn(List.of(recipient));
        when(channelUserManager.getChannels(1L)).thenReturn(List.of(senderOther));

        RedisStateProperties redisStateProperties = new RedisStateProperties();
        redisStateProperties.setServerId("node-a");
        DispatchPushService service = new DispatchPushService(channelUserManager, messageService, groupMessageService, groupService, redisStateProperties);

        DispatchMessagePayload payload = new DispatchMessagePayload();
        payload.setEventType(DispatchMessagePayload.EVENT_TYPE_RECALL);
        payload.setOriginServerId("node-a");
        payload.setServerMsgId("srv-r2");
        payload.setClientMsgId("cid-r2");
        payload.setFromUserId(1L);
        payload.setToUserId(2L);
        payload.setMsgType("TEXT");
        payload.setStatus("RETRACTED");
        payload.setCreatedAt("2026-03-25T00:00:00Z");
        payload.setRetractedAt("2026-03-25T00:01:00Z");
        payload.setRetractedBy(1L);

        service.dispatchSingle(payload);

        JsonNode recipientNotify = mapper.readTree(((TextWebSocketFrame) recipient.readOutbound()).text());
        assertEquals("MSG_RECALL_NOTIFY", recipientNotify.get("type").asText());
        assertNull(senderOther.readOutbound());
    }
}
