package com.ming.immessageservice.application;

import com.ming.imapicontract.common.ApiResponse;
import com.ming.imapicontract.file.ConsumeUploadTokenResponse;
import com.ming.imapicontract.message.PersistGroupMessageRequest;
import com.ming.imapicontract.message.PersistGroupMessageResponse;
import com.ming.imapicontract.message.PersistSingleMessageRequest;
import com.ming.imapicontract.message.PersistSingleMessageResponse;
import com.ming.immessageservice.config.MessageServiceProperties;
import com.ming.immessageservice.domain.service.GroupMessageDomainService;
import com.ming.immessageservice.domain.service.SingleMessageDomainService;
import com.ming.immessageservice.remote.file.FileServiceClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageCommandApplicationServiceTest {

    @Test
    void persistSingleMessageShouldConsumeUploadTokenWhenMsgTypeIsFile() {
        SingleMessageDomainService singleMessageDomainService = mock(SingleMessageDomainService.class);
        GroupMessageDomainService groupMessageDomainService = mock(GroupMessageDomainService.class);
        MessageServiceProperties messageServiceProperties = new MessageServiceProperties();
        FileServiceClient fileServiceClient = mock(FileServiceClient.class);

        when(fileServiceClient.consumeUploadToken(any()))
                .thenReturn(ApiResponse.success(new ConsumeUploadTokenResponse("{\"fileId\":\"f_1\"}")));
        when(singleMessageDomainService.persistSingleMessage(1L, 2L, "c1", "FILE", "{\"fileId\":\"f_1\"}"))
                .thenReturn(new SingleMessageDomainService.PersistResult("s1", true));

        MessageCommandApplicationService service = new MessageCommandApplicationService(
                singleMessageDomainService, groupMessageDomainService, messageServiceProperties, fileServiceClient);

        PersistSingleMessageResponse response = service.persistSingleMessage(
                new PersistSingleMessageRequest(1L, 2L, "c1", "FILE", "{\"uploadToken\":\"up-1\"}"));

        assertEquals("s1", response.serverMsgId());
        verify(fileServiceClient).consumeUploadToken(any());
    }

    @Test
    void persistGroupMessageShouldConsumeUploadTokenWhenMsgTypeIsFile() {
        SingleMessageDomainService singleMessageDomainService = mock(SingleMessageDomainService.class);
        GroupMessageDomainService groupMessageDomainService = mock(GroupMessageDomainService.class);
        MessageServiceProperties messageServiceProperties = new MessageServiceProperties();
        FileServiceClient fileServiceClient = mock(FileServiceClient.class);

        when(fileServiceClient.consumeUploadToken(any()))
                .thenReturn(ApiResponse.success(new ConsumeUploadTokenResponse("{\"fileId\":\"f_2\"}")));
        when(groupMessageDomainService.persistMessage(10L, 1L, "c2", "FILE", "{\"fileId\":\"f_2\"}"))
                .thenReturn(mock(com.ming.imapicontract.message.GroupMessageDTO.class));

        MessageCommandApplicationService service = new MessageCommandApplicationService(
                singleMessageDomainService, groupMessageDomainService, messageServiceProperties, fileServiceClient);

        PersistGroupMessageResponse response = service.persistGroupMessage(
                new PersistGroupMessageRequest(10L, 1L, "c2", "FILE", "{\"uploadToken\":\"up-2\"}"));

        assertNotNull(response.message());
        verify(fileServiceClient).consumeUploadToken(any());
    }
}
