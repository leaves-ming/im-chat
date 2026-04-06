package com.ming.imchatserver.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.application.model.GroupMessageView;
import com.ming.imchatserver.config.RedisStateProperties;
import com.ming.imchatserver.netty.ChannelUserManager;
import com.ming.imchatserver.service.remote.RemoteGroupService;
import com.ming.imchatserver.service.query.GroupMessageQueryPort;
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
        GroupMessageQueryPort groupMessageQueryPort = mock(GroupMessageQueryPort.class);
        RemoteGroupService groupService = mock(RemoteGroupService.class);
        EmbeddedChannel recipient = new EmbeddedChannel();
        EmbeddedChannel senderOther = new EmbeddedChannel();
        when(channelUserManager.getChannels(2L)).thenReturn(List.of(recipient));
        when(channelUserManager.getChannels(1L)).thenReturn(List.of(senderOther));

        RedisStateProperties redisStateProperties = new RedisStateProperties();
        redisStateProperties.setServerId("node-b");
        DispatchPushService service = new DispatchPushService(channelUserManager, groupMessageQueryPort, groupService, redisStateProperties);
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
        GroupMessageQueryPort groupMessageQueryPort = mock(GroupMessageQueryPort.class);
        RemoteGroupService groupService = mock(RemoteGroupService.class);
        EmbeddedChannel recipient = new EmbeddedChannel();
        EmbeddedChannel senderOther = new EmbeddedChannel();
        when(channelUserManager.getChannels(2L)).thenReturn(List.of(recipient));
        when(channelUserManager.getChannels(1L)).thenReturn(List.of(senderOther));

        RedisStateProperties redisStateProperties = new RedisStateProperties();
        redisStateProperties.setServerId("node-a");
        DispatchPushService service = new DispatchPushService(channelUserManager, groupMessageQueryPort, groupService, redisStateProperties);

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

    @Test
    void dispatchSingleShouldDeliverStatusNotifyToSender() throws Exception {
        ChannelUserManager channelUserManager = mock(ChannelUserManager.class);
        GroupMessageQueryPort groupMessageQueryPort = mock(GroupMessageQueryPort.class);
        RemoteGroupService groupService = mock(RemoteGroupService.class);
        EmbeddedChannel sender = new EmbeddedChannel();
        when(channelUserManager.getChannels(1L)).thenReturn(List.of(sender));

        DispatchPushService service = new DispatchPushService(channelUserManager, groupMessageQueryPort, groupService, new RedisStateProperties());
        DispatchMessagePayload payload = new DispatchMessagePayload();
        payload.setEventType(DispatchMessagePayload.EVENT_TYPE_STATUS_NOTIFY);
        payload.setServerMsgId("srv-s1");
        payload.setNotifyUserId(1L);
        payload.setToUserId(2L);
        payload.setStatus("ACKED");

        service.dispatchSingle(payload);

        JsonNode notify = mapper.readTree(((TextWebSocketFrame) sender.readOutbound()).text());
        assertEquals("MSG_STATUS_NOTIFY", notify.get("type").asText());
        assertEquals("srv-s1", notify.get("serverMsgId").asText());
        assertEquals("ACKED", notify.get("status").asText());
        assertEquals(2L, notify.get("toUserId").asLong());
    }

    @Test
    void dispatchGroupRecallShouldNotSkipOriginNodeMembers() throws Exception {
        ChannelUserManager channelUserManager = mock(ChannelUserManager.class);
        GroupMessageQueryPort groupMessageQueryPort = mock(GroupMessageQueryPort.class);
        RemoteGroupService groupService = mock(RemoteGroupService.class);
        EmbeddedChannel member1 = new EmbeddedChannel();
        EmbeddedChannel member2 = new EmbeddedChannel();
        when(groupService.listActiveMemberUserIds(101L)).thenReturn(List.of(1L, 2L));
        when(channelUserManager.getChannels(1L)).thenReturn(List.of(member1));
        when(channelUserManager.getChannels(2L)).thenReturn(List.of(member2));

        RedisStateProperties redisStateProperties = new RedisStateProperties();
        redisStateProperties.setServerId("node-a");
        DispatchPushService service = new DispatchPushService(channelUserManager, groupMessageQueryPort, groupService, redisStateProperties);
        DispatchMessagePayload payload = new DispatchMessagePayload();
        payload.setEventType(DispatchMessagePayload.EVENT_TYPE_RECALL);
        payload.setOriginServerId("node-a");
        payload.setGroupId(101L);
        payload.setSeq(61L);
        payload.setServerMsgId("g-recall-1");
        payload.setFromUserId(1L);
        payload.setMsgType("TEXT");
        payload.setStatus("RETRACTED");
        payload.setCreatedAt("2026-03-25T00:00:00Z");
        payload.setRetractedAt("2026-03-25T00:01:00Z");
        payload.setRetractedBy(1L);

        service.dispatchGroup(payload);

        assertEquals("GROUP_MSG_RECALL_NOTIFY", mapper.readTree(((TextWebSocketFrame) member1.readOutbound()).text()).get("type").asText());
        assertEquals("GROUP_MSG_RECALL_NOTIFY", mapper.readTree(((TextWebSocketFrame) member2.readOutbound()).text()).get("type").asText());
    }

    @Test
    void dispatchGroupShouldUseQueryPortAndDowngradeToRecallWhenCurrentMessageRetracted() throws Exception {
        ChannelUserManager channelUserManager = mock(ChannelUserManager.class);
        GroupMessageQueryPort groupMessageQueryPort = mock(GroupMessageQueryPort.class);
        RemoteGroupService groupService = mock(RemoteGroupService.class);
        EmbeddedChannel member1 = new EmbeddedChannel();
        EmbeddedChannel member2 = new EmbeddedChannel();
        when(groupService.listActiveMemberUserIds(101L)).thenReturn(List.of(1L, 2L));
        when(channelUserManager.getChannels(1L)).thenReturn(List.of(member1));
        when(channelUserManager.getChannels(2L)).thenReturn(List.of(member2));
        when(groupMessageQueryPort.findByServerMsgId("g-msg-1")).thenReturn(new GroupMessageView(
                1L,
                101L,
                61L,
                "g-msg-1",
                "c-1",
                1L,
                "TEXT",
                null,
                2,
                new Date(),
                new Date(),
                1L
        ));

        DispatchPushService service = new DispatchPushService(channelUserManager, groupMessageQueryPort, groupService, new RedisStateProperties());
        DispatchMessagePayload payload = new DispatchMessagePayload();
        payload.setEventType(DispatchMessagePayload.EVENT_TYPE_MESSAGE);
        payload.setGroupId(101L);
        payload.setSeq(61L);
        payload.setServerMsgId("g-msg-1");
        payload.setFromUserId(1L);
        payload.setMsgType("TEXT");
        payload.setContent("hello");

        service.dispatchGroup(payload);

        assertEquals("GROUP_MSG_RECALL_NOTIFY", mapper.readTree(((TextWebSocketFrame) member1.readOutbound()).text()).get("type").asText());
        assertEquals("GROUP_MSG_RECALL_NOTIFY", mapper.readTree(((TextWebSocketFrame) member2.readOutbound()).text()).get("type").asText());
    }
}
