package com.ming.imchatserver.application.facade.impl;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.message.AckMessageStatusResponse;
import com.ming.imapicontract.message.MessageDTO;
import com.ming.imapicontract.message.PersistSingleMessageResponse;
import com.ming.imapicontract.message.PullOfflineResponse;
import com.ming.imapicontract.message.RecallSingleMessageResponse;
import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.remote.message.MessageServiceClient;
import com.ming.imchatserver.service.MessageService;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteMessageFacadeTest {

    @Test
    void shouldCallMessageServiceClientForCoreMessageOperations() {
        MessageServiceClient messageServiceClient = mock(MessageServiceClient.class);
        RemoteMessageFacade facade = new RemoteMessageFacade(messageServiceClient);
        MessageDTO message = new MessageDTO(1L, "srv-1", "c-1", 1L, 2L, "TEXT", "hello", "ACKED",
                new Date(), null, null, null, null);

        when(messageServiceClient.persistSingleMessage(any()))
                .thenReturn(ApiResponse.success(new PersistSingleMessageResponse("c-1", "srv-1", true)));
        when(messageServiceClient.ackMessageStatus(any()))
                .thenReturn(ApiResponse.success(new AckMessageStatusResponse(message, "ACKED", 1, null)));
        when(messageServiceClient.pullOffline(any()))
                .thenReturn(ApiResponse.success(new PullOfflineResponse(
                        new com.ming.imapicontract.message.CursorPageDTO(List.of(message), false, null, null))));
        when(messageServiceClient.recallSingleMessage(any()))
                .thenReturn(ApiResponse.success(new RecallSingleMessageResponse(message)));

        MessageFacade.ChatPersistResult persistResult = facade.sendChat(1L, 2L, "c-1", "TEXT", "hello");
        MessageFacade.AckReportResult ackResult = facade.reportAck(2L, "srv-1", "ACKED");
        MessageService.CursorPageResult pageResult = facade.pullOffline(2L, "ios-1", null, 20);

        assertEquals("srv-1", persistResult.serverMsgId());
        assertSame("ACKED", ackResult.status());
        assertEquals(1, pageResult.getMessages().size());
        assertEquals("srv-1", facade.recallMessage(1L, "srv-1", 120L).getServerMsgId());

        verify(messageServiceClient).persistSingleMessage(any());
        verify(messageServiceClient).ackMessageStatus(any());
        verify(messageServiceClient).pullOffline(any());
        verify(messageServiceClient).recallSingleMessage(any());
    }
}
