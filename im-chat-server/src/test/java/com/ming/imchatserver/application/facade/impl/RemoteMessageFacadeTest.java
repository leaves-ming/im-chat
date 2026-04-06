package com.ming.imchatserver.application.facade.impl;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.message.AckMessageStatusResponse;
import com.ming.imapicontract.message.GetGroupMessageResponse;
import com.ming.imapicontract.message.GroupMessageDTO;
import com.ming.imapicontract.message.MessageDTO;
import com.ming.imapicontract.message.PersistGroupMessageResponse;
import com.ming.imapicontract.message.PersistSingleMessageResponse;
import com.ming.imapicontract.message.PullGroupOfflineResponse;
import com.ming.imapicontract.message.PullOfflineResponse;
import com.ming.imapicontract.message.RecallGroupMessageResponse;
import com.ming.imapicontract.message.RecallSingleMessageResponse;
import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.application.model.GroupMessagePage;
import com.ming.imchatserver.application.model.SingleMessagePage;
import com.ming.imchatserver.config.RateLimitProperties;
import com.ming.imchatserver.config.RedisStateProperties;
import com.ming.imchatserver.remote.message.MessageServiceClient;
import com.ming.imchatserver.service.IdempotencyService;
import com.ming.imchatserver.service.RateLimitService;
import com.ming.imchatserver.service.remote.RemoteGroupService;
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
        RemoteMessageFacade facade = new RemoteMessageFacade(
                messageServiceClient,
                mock(RemoteGroupService.class),
                mock(IdempotencyService.class),
                mock(RateLimitService.class),
                new RateLimitProperties(),
                new RedisStateProperties());
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
        SingleMessagePage pageResult = facade.pullOffline(2L, "ios-1", null, 20);

        assertEquals("srv-1", persistResult.serverMsgId());
        assertSame("ACKED", ackResult.status());
        assertEquals(1, pageResult.messages().size());
        assertEquals("srv-1", facade.recallMessage(1L, "srv-1", 120L).serverMsgId());

        verify(messageServiceClient).persistSingleMessage(any());
        verify(messageServiceClient).ackMessageStatus(any());
        verify(messageServiceClient).pullOffline(any());
        verify(messageServiceClient).recallSingleMessage(any());
    }

    @Test
    void shouldCallMessageServiceClientForGroupOperations() {
        MessageServiceClient messageServiceClient = mock(MessageServiceClient.class);
        RemoteGroupService groupService = mock(RemoteGroupService.class);
        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        RateLimitProperties rateLimitProperties = new RateLimitProperties();
        RedisStateProperties redisStateProperties = new RedisStateProperties();
        RemoteMessageFacade facade = new RemoteMessageFacade(
                messageServiceClient,
                groupService,
                idempotencyService,
                rateLimitService,
                rateLimitProperties,
                redisStateProperties);
        GroupMessageDTO message = new GroupMessageDTO(
                1L, 101L, 8L, "srv-1", "c-1", 1L, "TEXT", "hello", 1, new Date(), null, null);

        when(groupService.isActiveMember(101L, 1L)).thenReturn(true);
        when(idempotencyService.claimClientMessage(any(), any(), any())).thenReturn(true);
        when(rateLimitService.checkAndIncrement(any(), any(), any(), any(), any()))
                .thenReturn(new RateLimitService.Decision(true, 1, 60));
        when(messageServiceClient.persistGroupMessage(any()))
                .thenReturn(ApiResponse.success(new PersistGroupMessageResponse(message)));
        when(messageServiceClient.pullGroupOffline(any()))
                .thenReturn(ApiResponse.success(new PullGroupOfflineResponse(List.of(message), false, 8L)));
        when(messageServiceClient.getGroupMessage(any()))
                .thenReturn(ApiResponse.success(new GetGroupMessageResponse(message)));
        when(messageServiceClient.recallGroupMessage(any()))
                .thenReturn(ApiResponse.success(new RecallGroupMessageResponse(message)));

        MessageFacade facadeView = facade;
        assertEquals(101L, facadeView.sendGroupChat(101L, 1L, "c-1", "TEXT", "hello").message().groupId());
        GroupMessagePage page = facadeView.pullGroupOffline(101L, 1L, null, 20);
        assertEquals(1, page.messages().size());
        assertEquals(8L, page.nextCursorSeq());
        assertEquals("srv-1", facadeView.recallGroupMessage(1L, "srv-1", 120L).serverMsgId());

        verify(messageServiceClient).persistGroupMessage(any());
        verify(messageServiceClient).pullGroupOffline(any());
        verify(messageServiceClient).getGroupMessage(any());
        verify(messageServiceClient).recallGroupMessage(any());
    }
}
