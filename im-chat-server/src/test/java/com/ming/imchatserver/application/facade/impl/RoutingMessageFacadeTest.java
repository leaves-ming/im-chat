package com.ming.imchatserver.application.facade.impl;

import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.service.MessageService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingMessageFacadeTest {

    @Test
    void shouldDelegateAllCallsToRemoteFacade() {
        RemoteMessageFacade remoteMessageFacade = mock(RemoteMessageFacade.class);
        RoutingMessageFacade routingMessageFacade = new RoutingMessageFacade(remoteMessageFacade);
        MessageDO message = new MessageDO();
        MessageFacade.ChatPersistResult persistResult = new MessageFacade.ChatPersistResult("c-1", "srv-1", true);
        MessageFacade.AckReportResult ackResult = new MessageFacade.AckReportResult(message, "ACKED", 1, null);
        MessageService.CursorPageResult pageResult = new MessageService.CursorPageResult(List.of(message), false, null, null);

        when(remoteMessageFacade.sendChat(1L, 2L, "c-1", "TEXT", "hello")).thenReturn(persistResult);
        when(remoteMessageFacade.reportAck(2L, "srv-1", "ACKED")).thenReturn(ackResult);
        when(remoteMessageFacade.enqueueStatusNotify(message, "ACKED")).thenReturn(true);
        when(remoteMessageFacade.pullOffline(2L, "ios-1", null, 20)).thenReturn(pageResult);
        when(remoteMessageFacade.loadInitialSync(2L, "ios-1", 20)).thenReturn(pageResult);
        when(remoteMessageFacade.recallMessage(1L, "srv-1", 120L)).thenReturn(message);

        assertSame(persistResult, routingMessageFacade.sendChat(1L, 2L, "c-1", "TEXT", "hello"));
        assertSame(ackResult, routingMessageFacade.reportAck(2L, "srv-1", "ACKED"));
        assertTrue(routingMessageFacade.enqueueStatusNotify(message, "ACKED"));
        assertSame(pageResult, routingMessageFacade.pullOffline(2L, "ios-1", null, 20));
        assertSame(pageResult, routingMessageFacade.loadInitialSync(2L, "ios-1", 20));
        routingMessageFacade.advanceSyncCursor(2L, "ios-1", pageResult);
        assertSame(message, routingMessageFacade.recallMessage(1L, "srv-1", 120L));

        verify(remoteMessageFacade).sendChat(1L, 2L, "c-1", "TEXT", "hello");
        verify(remoteMessageFacade).reportAck(2L, "srv-1", "ACKED");
        verify(remoteMessageFacade).enqueueStatusNotify(message, "ACKED");
        verify(remoteMessageFacade).pullOffline(2L, "ios-1", null, 20);
        verify(remoteMessageFacade).loadInitialSync(2L, "ios-1", 20);
        verify(remoteMessageFacade).advanceSyncCursor(2L, "ios-1", pageResult);
        verify(remoteMessageFacade).recallMessage(1L, "srv-1", 120L);
    }
}
