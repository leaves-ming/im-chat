package com.ming.imchatserver.service;

import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.mapper.MessageMapper;
import com.ming.imchatserver.message.MessageContentCodec;
import com.ming.imchatserver.sensitive.SensitiveWordFilterResult;
import com.ming.imchatserver.sensitive.SensitiveWordHitException;
import com.ming.imchatserver.sensitive.SensitiveWordMode;
import com.ming.imchatserver.sensitive.SensitiveWordService;
import com.ming.imchatserver.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MessageServiceImplTest - 代码模块说明。
 * <p>
 * 本类注释由统一规范补充，描述职责、边界与使用语义。
 */
class MessageServiceImplTest {

    @Test
    /**
     * 方法说明。
     */
    void blankClientMsgIdShouldBeNormalizedToNull() {
        MessageMapper mapper = mock(MessageMapper.class);
        MessageServiceImpl service = new MessageServiceImpl(mapper);

        MessageDO msg = new MessageDO();
        msg.setFromUserId(1L);
        msg.setToUserId(2L);
        msg.setContent("hello");
        msg.setClientMsgId("   ");

        MessageService.PersistResult result = service.persistMessage(msg);

        assertNotNull(result.getServerMsgId());
        assertEquals(true, result.isCreatedNew());
        assertNull(msg.getClientMsgId());
        verify(mapper, never()).findByFromUserIdAndClientMsgId(any(), any());
    }

    @Test
    /**
     * 方法说明。
     */
    void duplicateKeyShouldFallbackToExistingWhenClientMsgIdPresent() {
        MessageMapper mapper = mock(MessageMapper.class);
        MessageServiceImpl service = new MessageServiceImpl(mapper);

        MessageDO msg = new MessageDO();
        msg.setFromUserId(1L);
        msg.setToUserId(2L);
        msg.setContent("hello");
        msg.setClientMsgId("cid-1");

        doThrow(new DuplicateKeyException("dup")).when(mapper).insert(any(MessageDO.class));
        MessageDO exist = new MessageDO();
        exist.setServerMsgId("existing-1");
        when(mapper.findByFromUserIdAndClientMsgId(1L, "cid-1")).thenReturn(exist);

        MessageService.PersistResult result = service.persistMessage(msg);

        assertEquals("existing-1", result.getServerMsgId());
        assertEquals(false, result.isCreatedNew());
        verify(mapper).findByFromUserIdAndClientMsgId(1L, "cid-1");
    }

    @Test
    void statusMachineShouldRejectRollbackFromDeliveredToSent() {
        MessageMapper mapper = mock(MessageMapper.class);
        MessageServiceImpl service = new MessageServiceImpl(mapper);

        MessageDO exist = new MessageDO();
        exist.setServerMsgId("srv-1");
        exist.setStatus("DELIVERED");
        when(mapper.findByServerMsgId("srv-1")).thenReturn(exist);

        int updated = service.updateStatusByServerMsgId("srv-1", "SENT");

        assertEquals(0, updated);
        verify(mapper, never()).updateStatusByServerMsgId(eq("srv-1"), eq("SENT"), any(), any());
    }

    @Test
    void statusMachineShouldRejectRollbackFromAckedToDelivered() {
        MessageMapper mapper = mock(MessageMapper.class);
        MessageServiceImpl service = new MessageServiceImpl(mapper);

        MessageDO exist = new MessageDO();
        exist.setServerMsgId("srv-2");
        exist.setStatus("ACKED");
        when(mapper.findByServerMsgId("srv-2")).thenReturn(exist);

        int updated = service.updateStatusByServerMsgId("srv-2", "DELIVERED");

        assertEquals(0, updated);
        verify(mapper, never()).updateStatusByServerMsgId(eq("srv-2"), eq("DELIVERED"), any(), any());
    }

    @Test
    void statusMachineShouldRejectJumpFromSentToAcked() {
        MessageMapper mapper = mock(MessageMapper.class);
        MessageServiceImpl service = new MessageServiceImpl(mapper);

        MessageDO exist = new MessageDO();
        exist.setServerMsgId("srv-3");
        exist.setStatus("SENT");
        when(mapper.findByServerMsgId("srv-3")).thenReturn(exist);

        int updated = service.updateStatusByServerMsgId("srv-3", "ACKED");

        assertEquals(0, updated);
        verify(mapper, never()).updateStatusByServerMsgId(eq("srv-3"), eq("ACKED"), any(), any());
    }

    @Test
    void statusMachineShouldAllowSentToDeliveredAndDeliveredToAcked() {
        MessageMapper mapper = mock(MessageMapper.class);
        MessageServiceImpl service = new MessageServiceImpl(mapper);

        MessageDO sent = new MessageDO();
        sent.setServerMsgId("srv-4");
        sent.setStatus("SENT");
        when(mapper.findByServerMsgId("srv-4")).thenReturn(sent);
        when(mapper.updateStatusByServerMsgId(eq("srv-4"), eq("DELIVERED"), any(), any())).thenReturn(1);

        int deliveredUpdated = service.updateStatusByServerMsgId("srv-4", "DELIVERED");
        assertEquals(1, deliveredUpdated);
        verify(mapper).updateStatusByServerMsgId(eq("srv-4"), eq("DELIVERED"), any(), isNull());

        MessageDO delivered = new MessageDO();
        delivered.setServerMsgId("srv-5");
        delivered.setStatus("DELIVERED");
        when(mapper.findByServerMsgId("srv-5")).thenReturn(delivered);
        when(mapper.updateStatusByServerMsgId(eq("srv-5"), eq("ACKED"), any(), any())).thenReturn(1);

        int ackedUpdated = service.updateStatusByServerMsgId("srv-5", "ACKED");
        assertEquals(1, ackedUpdated);
        verify(mapper).updateStatusByServerMsgId(eq("srv-5"), eq("ACKED"), any(), any());
    }

    @Test
    void persistMessageShouldRejectSensitiveWordsBeforeInsert() {
        MessageMapper mapper = mock(MessageMapper.class);
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        MessageServiceImpl service = new MessageServiceImpl(mapper, null, null, sensitiveWordService);

        MessageDO msg = new MessageDO();
        msg.setFromUserId(1L);
        msg.setToUserId(2L);
        msg.setContent("badword");

        when(sensitiveWordService.filter("badword"))
                .thenReturn(new SensitiveWordFilterResult(true, SensitiveWordMode.REJECT, "badword", "badword"));

        org.junit.jupiter.api.Assertions.assertThrows(SensitiveWordHitException.class, () -> service.persistMessage(msg));
        verify(mapper, never()).insert(any(MessageDO.class));
    }

    @Test
    void persistMessageShouldReplaceSensitiveWordsBeforeInsert() {
        MessageMapper mapper = mock(MessageMapper.class);
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        MessageServiceImpl service = new MessageServiceImpl(mapper, null, null, sensitiveWordService);
        AtomicReference<String> insertedContent = new AtomicReference<>();

        MessageDO msg = new MessageDO();
        msg.setFromUserId(1L);
        msg.setToUserId(2L);
        msg.setContent("badword");

        when(sensitiveWordService.filter("badword"))
                .thenReturn(new SensitiveWordFilterResult(true, SensitiveWordMode.REPLACE, "badword", "*******"));
        org.mockito.Mockito.doAnswer(invocation -> {
            MessageDO saved = invocation.getArgument(0);
            insertedContent.set(saved.getContent());
            return 1;
        }).when(mapper).insert(any(MessageDO.class));

        service.persistMessage(msg);

        verify(mapper).insert(any(MessageDO.class));
        assertEquals("\"*******\"", insertedContent.get());
        assertEquals("*******", msg.getContent());
    }

    @Test
    void persistFileMessageShouldBypassSensitiveFilterAndStoreJsonObject() {
        MessageMapper mapper = mock(MessageMapper.class);
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        MessageServiceImpl service = new MessageServiceImpl(mapper, null, null, sensitiveWordService);

        MessageDO msg = new MessageDO();
        msg.setFromUserId(1L);
        msg.setToUserId(2L);
        msg.setMsgType("FILE");
        msg.setContent("{\"fileId\":\"f1\",\"fileName\":\"a.txt\",\"size\":12,\"contentType\":\"text/plain\",\"url\":\"/files/f1/a.txt\"}");

        service.persistMessage(msg);

        ArgumentCaptor<MessageDO> captor = ArgumentCaptor.forClass(MessageDO.class);
        verify(mapper).insert(captor.capture());
        verify(sensitiveWordService, never()).filter(any());
        assertEquals(MessageContentCodec.MSG_TYPE_FILE, captor.getValue().getMsgType());
        assertEquals(msg.getContent(), captor.getValue().getContent());
    }
}
