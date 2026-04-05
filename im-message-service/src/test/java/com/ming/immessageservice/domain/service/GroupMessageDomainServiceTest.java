package com.ming.immessageservice.domain.service;

import com.ming.imapicontract.message.GroupMessageDTO;
import com.ming.immessageservice.infrastructure.dao.GroupMessageDO;
import com.ming.immessageservice.infrastructure.mapper.GroupCursorMapper;
import com.ming.immessageservice.infrastructure.mapper.GroupMessageMapper;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupMessageDomainServiceTest {

    @Test
    void persistMessageShouldAllocateSeqAndAppendOutbox() {
        GroupMessageMapper groupMessageMapper = mock(GroupMessageMapper.class);
        GroupCursorMapper groupCursorMapper = mock(GroupCursorMapper.class);
        DispatchOutboxDomainService dispatchOutboxDomainService = mock(DispatchOutboxDomainService.class);
        GroupMessageDomainService service = new GroupMessageDomainService(
                groupMessageMapper, groupCursorMapper, dispatchOutboxDomainService);

        when(groupMessageMapper.findMaxSeq(101L)).thenReturn(7L);
        doAnswer(invocation -> {
            GroupMessageDO message = invocation.getArgument(0);
            message.setId(88L);
            message.setCreatedAt(new Date());
            return 1;
        }).when(groupMessageMapper).insert(any(GroupMessageDO.class));

        GroupMessageDTO result = service.persistMessage(101L, 1L, "c-1", "TEXT", "hello");

        assertEquals(101L, result.groupId());
        assertEquals(8L, result.seq());
        assertEquals(1L, result.fromUserId());
        assertEquals("TEXT", result.msgType());
        assertEquals("hello", result.content());
        assertNotNull(result.serverMsgId());
        verify(dispatchOutboxDomainService).appendGroupMessage(any(GroupMessageDO.class));
    }
}
