package com.ming.imchatserver.service.remote;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.message.GetGroupMessageResponse;
import com.ming.imapicontract.message.GroupMessageDTO;
import com.ming.imapicontract.message.PersistGroupMessageResponse;
import com.ming.imapicontract.message.PullGroupOfflineResponse;
import com.ming.imapicontract.message.RecallGroupMessageResponse;
import com.ming.imchatserver.application.model.GroupMessagePage;
import com.ming.imchatserver.application.model.GroupMessagePersistResult;
import com.ming.imchatserver.application.model.GroupMessageView;
import com.ming.imchatserver.remote.message.MessageServiceClient;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteGroupMessageServiceTest {

    @Test
    void shouldMapRemoteResponses() {
        MessageServiceClient messageServiceClient = mock(MessageServiceClient.class);
        RemoteGroupMessageService service = new RemoteGroupMessageService(messageServiceClient);
        GroupMessageDTO dto = new GroupMessageDTO(
                1L, 101L, 8L, "srv-1", "c-1", 1L, "TEXT", "hello", 1, new Date(), null, null);

        when(messageServiceClient.persistGroupMessage(any()))
                .thenReturn(ApiResponse.success(new PersistGroupMessageResponse(dto)));
        when(messageServiceClient.pullGroupOffline(any()))
                .thenReturn(ApiResponse.success(new PullGroupOfflineResponse(List.of(dto), false, 8L)));
        when(messageServiceClient.getGroupMessage(any()))
                .thenReturn(ApiResponse.success(new GetGroupMessageResponse(dto)));
        when(messageServiceClient.recallGroupMessage(any()))
                .thenReturn(ApiResponse.success(new RecallGroupMessageResponse(dto)));

        GroupMessagePersistResult persistResult = service.persistMessage(101L, 1L, "c-1", "TEXT", "hello");
        GroupMessagePage pullResult = service.pullOffline(101L, 1L, null, 20);
        GroupMessageView queried = service.findByServerMsgId("srv-1");
        GroupMessageView recalled = service.recallMessage(1L, "srv-1", 120L);

        assertEquals(101L, persistResult.message().groupId());
        assertEquals(1, pullResult.messages().size());
        assertEquals(8L, pullResult.nextCursorSeq());
        assertNotNull(queried);
        assertEquals("srv-1", queried.serverMsgId());
        assertEquals("srv-1", recalled.serverMsgId());
    }
}
