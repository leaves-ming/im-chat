package com.ming.imchatserver.service;

import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.mapper.MessageMapper;
import com.ming.imchatserver.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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
}
