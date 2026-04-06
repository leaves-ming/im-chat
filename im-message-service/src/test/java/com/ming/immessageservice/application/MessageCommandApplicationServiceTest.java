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
import com.ming.immessageservice.sensitive.SensitiveWordHitException;
import com.ming.immessageservice.sensitive.SensitiveWordService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageCommandApplicationServiceTest {

    @Test
    void persistSingleMessageShouldConsumeUploadTokenWhenMsgTypeIsFile() {
        SingleMessageDomainService singleMessageDomainService = mock(SingleMessageDomainService.class);
        GroupMessageDomainService groupMessageDomainService = mock(GroupMessageDomainService.class);
        MessageServiceProperties messageServiceProperties = new MessageServiceProperties();
        FileServiceClient fileServiceClient = mock(FileServiceClient.class);
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);

        when(fileServiceClient.consumeUploadToken(any()))
                .thenReturn(ApiResponse.success(new ConsumeUploadTokenResponse("{\"fileId\":\"f_1\"}")));
        when(sensitiveWordService.filterText("{\"fileId\":\"f_1\"}")).thenReturn("{\"fileId\":\"f_1\"}");
        when(singleMessageDomainService.persistSingleMessage(1L, 2L, "c1", "FILE", "{\"fileId\":\"f_1\"}"))
                .thenReturn(new SingleMessageDomainService.PersistResult("s1", true));

        MessageCommandApplicationService service = new MessageCommandApplicationService(
                singleMessageDomainService, groupMessageDomainService, messageServiceProperties, fileServiceClient, sensitiveWordService);

        PersistSingleMessageResponse response = service.persistSingleMessage(
                new PersistSingleMessageRequest(1L, 2L, "c1", "FILE", "{\"uploadToken\":\"up-1\"}"));

        assertEquals("s1", response.serverMsgId());
        verify(fileServiceClient).consumeUploadToken(any());
        verify(sensitiveWordService).filterText("{\"fileId\":\"f_1\"}");
    }

    @Test
    void persistGroupMessageShouldConsumeUploadTokenWhenMsgTypeIsFile() {
        SingleMessageDomainService singleMessageDomainService = mock(SingleMessageDomainService.class);
        GroupMessageDomainService groupMessageDomainService = mock(GroupMessageDomainService.class);
        MessageServiceProperties messageServiceProperties = new MessageServiceProperties();
        FileServiceClient fileServiceClient = mock(FileServiceClient.class);
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);

        when(fileServiceClient.consumeUploadToken(any()))
                .thenReturn(ApiResponse.success(new ConsumeUploadTokenResponse("{\"fileId\":\"f_2\"}")));
        when(sensitiveWordService.filterText("{\"fileId\":\"f_2\"}")).thenReturn("{\"fileId\":\"f_2\"}");
        when(groupMessageDomainService.persistMessage(10L, 1L, "c2", "FILE", "{\"fileId\":\"f_2\"}"))
                .thenReturn(mock(com.ming.imapicontract.message.GroupMessageDTO.class));

        MessageCommandApplicationService service = new MessageCommandApplicationService(
                singleMessageDomainService, groupMessageDomainService, messageServiceProperties, fileServiceClient, sensitiveWordService);

        PersistGroupMessageResponse response = service.persistGroupMessage(
                new PersistGroupMessageRequest(10L, 1L, "c2", "FILE", "{\"uploadToken\":\"up-2\"}"));

        assertNotNull(response.message());
        verify(fileServiceClient).consumeUploadToken(any());
        verify(sensitiveWordService).filterText("{\"fileId\":\"f_2\"}");
    }

    @Test
    void persistSingleMessageShouldRejectSensitiveWordAfterNormalizeAndBeforeDomainService() {
        SingleMessageDomainService singleMessageDomainService = mock(SingleMessageDomainService.class);
        GroupMessageDomainService groupMessageDomainService = mock(GroupMessageDomainService.class);
        MessageServiceProperties messageServiceProperties = new MessageServiceProperties();
        FileServiceClient fileServiceClient = mock(FileServiceClient.class);
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);

        when(sensitiveWordService.filterText("badword text")).thenThrow(new SensitiveWordHitException("badword"));

        MessageCommandApplicationService service = new MessageCommandApplicationService(
                singleMessageDomainService, groupMessageDomainService, messageServiceProperties, fileServiceClient, sensitiveWordService);

        assertThrows(SensitiveWordHitException.class, () -> service.persistSingleMessage(
                new PersistSingleMessageRequest(1L, 2L, "c3", "TEXT", "badword text")));

        verify(sensitiveWordService).filterText("badword text");
        verify(singleMessageDomainService, never()).persistSingleMessage(any(), any(), any(), any(), any());
    }

    @Test
    void persistGroupMessageShouldRejectSensitiveWordAfterNormalizeAndBeforeDomainService() {
        SingleMessageDomainService singleMessageDomainService = mock(SingleMessageDomainService.class);
        GroupMessageDomainService groupMessageDomainService = mock(GroupMessageDomainService.class);
        MessageServiceProperties messageServiceProperties = new MessageServiceProperties();
        FileServiceClient fileServiceClient = mock(FileServiceClient.class);
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);

        when(sensitiveWordService.filterText("badword group")).thenThrow(new SensitiveWordHitException("badword"));

        MessageCommandApplicationService service = new MessageCommandApplicationService(
                singleMessageDomainService, groupMessageDomainService, messageServiceProperties, fileServiceClient, sensitiveWordService);

        assertThrows(SensitiveWordHitException.class, () -> service.persistGroupMessage(
                new PersistGroupMessageRequest(10L, 1L, "c4", "TEXT", "badword group")));

        verify(sensitiveWordService).filterText("badword group");
        verify(groupMessageDomainService, never()).persistMessage(any(), any(), any(), any(), any());
    }

    @Test
    void persistSingleMessageShouldPersistReplacedContent() {
        SingleMessageDomainService singleMessageDomainService = mock(SingleMessageDomainService.class);
        GroupMessageDomainService groupMessageDomainService = mock(GroupMessageDomainService.class);
        MessageServiceProperties messageServiceProperties = new MessageServiceProperties();
        FileServiceClient fileServiceClient = mock(FileServiceClient.class);
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);

        when(sensitiveWordService.filterText("badword text")).thenReturn("******* text");
        when(singleMessageDomainService.persistSingleMessage(1L, 2L, "c5", "TEXT", "******* text"))
                .thenReturn(new SingleMessageDomainService.PersistResult("s5", true));

        MessageCommandApplicationService service = new MessageCommandApplicationService(
                singleMessageDomainService, groupMessageDomainService, messageServiceProperties, fileServiceClient, sensitiveWordService);

        PersistSingleMessageResponse response = service.persistSingleMessage(
                new PersistSingleMessageRequest(1L, 2L, "c5", "TEXT", "badword text"));

        assertEquals("s5", response.serverMsgId());
        verify(sensitiveWordService).filterText("badword text");
        verify(singleMessageDomainService).persistSingleMessage(1L, 2L, "c5", "TEXT", "******* text");
    }

    @Test
    void persistGroupMessageShouldPersistReplacedContent() {
        SingleMessageDomainService singleMessageDomainService = mock(SingleMessageDomainService.class);
        GroupMessageDomainService groupMessageDomainService = mock(GroupMessageDomainService.class);
        MessageServiceProperties messageServiceProperties = new MessageServiceProperties();
        FileServiceClient fileServiceClient = mock(FileServiceClient.class);
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);

        when(sensitiveWordService.filterText("badword group")).thenReturn("******* group");
        when(groupMessageDomainService.persistMessage(10L, 1L, "c6", "TEXT", "******* group"))
                .thenReturn(mock(com.ming.imapicontract.message.GroupMessageDTO.class));

        MessageCommandApplicationService service = new MessageCommandApplicationService(
                singleMessageDomainService, groupMessageDomainService, messageServiceProperties, fileServiceClient, sensitiveWordService);

        PersistGroupMessageResponse response = service.persistGroupMessage(
                new PersistGroupMessageRequest(10L, 1L, "c6", "TEXT", "badword group"));

        assertNotNull(response.message());
        verify(sensitiveWordService).filterText("badword group");
        verify(groupMessageDomainService).persistMessage(10L, 1L, "c6", "TEXT", "******* group");
    }

    @Test
    void persistSingleMessageShouldFilterCanonicalFileContent() {
        SingleMessageDomainService singleMessageDomainService = mock(SingleMessageDomainService.class);
        GroupMessageDomainService groupMessageDomainService = mock(GroupMessageDomainService.class);
        MessageServiceProperties messageServiceProperties = new MessageServiceProperties();
        FileServiceClient fileServiceClient = mock(FileServiceClient.class);
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);

        when(fileServiceClient.consumeUploadToken(any()))
                .thenReturn(ApiResponse.success(new ConsumeUploadTokenResponse("{\"fileId\":\"badword\"}")));
        when(sensitiveWordService.filterText("{\"fileId\":\"badword\"}")).thenThrow(new SensitiveWordHitException("badword"));

        MessageCommandApplicationService service = new MessageCommandApplicationService(
                singleMessageDomainService, groupMessageDomainService, messageServiceProperties, fileServiceClient, sensitiveWordService);

        assertThrows(SensitiveWordHitException.class, () -> service.persistSingleMessage(
                new PersistSingleMessageRequest(1L, 2L, "c7", "FILE", "{\"uploadToken\":\"up-7\"}")));

        verify(fileServiceClient).consumeUploadToken(any());
        verify(sensitiveWordService).filterText("{\"fileId\":\"badword\"}");
        verify(singleMessageDomainService, never()).persistSingleMessage(any(), any(), any(), any(), any());
    }
}
