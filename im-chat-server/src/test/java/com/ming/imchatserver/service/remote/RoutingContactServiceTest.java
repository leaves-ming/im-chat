package com.ming.imchatserver.service.remote;

import com.ming.imchatserver.dao.ContactDO;
import com.ming.imchatserver.service.ContactService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingContactServiceTest {

    @Test
    void shouldDelegateAllCallsToRemoteService() {
        RemoteContactService remoteContactService = mock(RemoteContactService.class);
        RoutingContactService routingContactService = new RoutingContactService(remoteContactService);
        ContactService.Result operateResult = new ContactService.Result(true, false);
        ContactDO contact = new ContactDO();
        contact.setOwnerUserId(1L);
        contact.setPeerUserId(2L);
        ContactService.ContactPageResult pageResult = new ContactService.ContactPageResult(List.of(contact), 2L, false);

        when(remoteContactService.addOrActivateContact(1L, 2L)).thenReturn(operateResult);
        when(remoteContactService.removeOrDeactivateContact(1L, 2L)).thenReturn(operateResult);
        when(remoteContactService.listActiveContacts(1L, 0L, 20)).thenReturn(pageResult);
        when(remoteContactService.isActiveContact(1L, 2L)).thenReturn(true);
        when(remoteContactService.isSingleChatAllowed(1L, 2L)).thenReturn(true);

        assertSame(operateResult, routingContactService.addOrActivateContact(1L, 2L));
        assertSame(operateResult, routingContactService.removeOrDeactivateContact(1L, 2L));
        assertSame(pageResult, routingContactService.listActiveContacts(1L, 0L, 20));
        assertTrue(routingContactService.isActiveContact(1L, 2L));
        assertTrue(routingContactService.isSingleChatAllowed(1L, 2L));

        verify(remoteContactService).addOrActivateContact(1L, 2L);
        verify(remoteContactService).removeOrDeactivateContact(1L, 2L);
        verify(remoteContactService).listActiveContacts(1L, 0L, 20);
        verify(remoteContactService).isActiveContact(1L, 2L);
        verify(remoteContactService).isSingleChatAllowed(1L, 2L);
    }
}
