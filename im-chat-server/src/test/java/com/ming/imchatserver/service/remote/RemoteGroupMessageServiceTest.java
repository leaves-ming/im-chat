package com.ming.imchatserver.service.remote;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.message.GetGroupMessageResponse;
import com.ming.imapicontract.message.GroupMessageDTO;
import com.ming.imapicontract.message.PersistGroupMessageResponse;
import com.ming.imapicontract.message.PullGroupOfflineResponse;
import com.ming.imapicontract.message.RecallGroupMessageResponse;
import com.ming.imchatserver.dao.GroupMessageDO;
import com.ming.imchatserver.remote.message.MessageServiceClient;
import com.ming.imchatserver.service.GroupMessageService;
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

        GroupMessageService.PersistResult persistResult = service.persistMessage(101L, 1L, "c-1", "TEXT", "hello");
        GroupMessageService.PullResult pullResult = service.pullOffline(101L, 1L, null, 20);
        GroupMessageDO queried = service.findByServerMsgId("srv-1");
        GroupMessageDO recalled = service.recallMessage(1L, "srv-1", 120L);

        assertEquals(101L, persistResult.getMessage().getGroupId());
        assertEquals(1, pullResult.getMessages().size());
        assertEquals(8L, pullResult.getNextCursorSeq());
        assertNotNull(queried);
        assertEquals("srv-1", queried.getServerMsgId());
        assertEquals("srv-1", recalled.getServerMsgId());
    }
}
