package com.ming.imchatserver.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.netty.ChannelUserManager;
import com.ming.imchatserver.service.MessageService;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DispatchPushServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void dispatchSingleShouldHandleExplicitRecallEvent() throws Exception {
        ChannelUserManager channelUserManager = mock(ChannelUserManager.class);
        MessageService messageService = mock(MessageService.class);
        EmbeddedChannel recipient = new EmbeddedChannel();
        when(channelUserManager.getChannels(2L)).thenReturn(List.of(recipient));

        DispatchPushService service = new DispatchPushService(channelUserManager, messageService);
        DispatchMessagePayload payload = new DispatchMessagePayload();
        payload.setEventType(DispatchMessagePayload.EVENT_TYPE_RECALL);
        payload.setServerMsgId("srv-r1");
        payload.setClientMsgId("cid-r1");
        payload.setFromUserId(1L);
        payload.setToUserId(2L);
        payload.setMsgType("TEXT");
        payload.setStatus("RETRACTED");
        payload.setRetractedAt(new Date().toInstant().toString());
        payload.setRetractedBy(1L);

        service.dispatchSingle(payload);

        TextWebSocketFrame frame = recipient.readOutbound();
        JsonNode notify = mapper.readTree(frame.text());
        assertEquals("MSG_RECALL_NOTIFY", notify.get("type").asText());
        assertEquals("RETRACTED", notify.get("status").asText());
        assertNull(notify.get("content").textValue());
    }
}
