package com.ming.imchatserver.service;

import com.ming.imchatserver.dao.GroupMessageDO;
import com.ming.imchatserver.mapper.GroupCursorMapper;
import com.ming.imchatserver.mapper.GroupMessageMapper;
import com.ming.imchatserver.message.MessageContentCodec;
import com.ming.imchatserver.sensitive.SensitiveWordFilterResult;
import com.ming.imchatserver.sensitive.SensitiveWordHitException;
import com.ming.imchatserver.sensitive.SensitiveWordMode;
import com.ming.imchatserver.sensitive.SensitiveWordService;
import com.ming.imchatserver.service.impl.GroupMessageServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.dao.DuplicateKeyException;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GroupMessageServiceImplTest - 代码模块说明。
 * <p>
 * 本类注释由统一规范补充，描述职责、边界与使用语义。
 */
class GroupMessageServiceImplTest {

    @Test
    void persistMessageShouldRetryWhenSeqConflict() {
        GroupMessageMapper groupMessageMapper = mock(GroupMessageMapper.class);
        GroupCursorMapper groupCursorMapper = mock(GroupCursorMapper.class);
        GroupMessageServiceImpl service = new GroupMessageServiceImpl(groupMessageMapper, groupCursorMapper, null);

        when(groupMessageMapper.findMaxSeq(101L)).thenReturn(9L, 10L);
        when(groupMessageMapper.insert(any(GroupMessageDO.class)))
                .thenThrow(new DuplicateKeyException("dup"))
                .thenReturn(1);

        GroupMessageService.PersistResult result = service.persistMessage(101L, 1L, "cid-1", "TEXT", "hello");

        ArgumentCaptor<GroupMessageDO> captor = ArgumentCaptor.forClass(GroupMessageDO.class);
        verify(groupMessageMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<GroupMessageDO> attempts = captor.getAllValues();
        assertEquals(10L, attempts.get(0).getSeq());
        assertEquals(11L, attempts.get(1).getSeq());
        assertEquals(11L, result.getMessage().getSeq());
        assertEquals("hello", result.getMessage().getContent());
    }

    @Test
    void pullOfflineShouldKeepAscAndAdvanceCursorWithHasMore() {
        GroupMessageMapper groupMessageMapper = mock(GroupMessageMapper.class);
        GroupCursorMapper groupCursorMapper = mock(GroupCursorMapper.class);
        GroupMessageServiceImpl service = new GroupMessageServiceImpl(groupMessageMapper, groupCursorMapper, null);

        when(groupCursorMapper.findLastPullSeq(101L, 2L)).thenReturn(20L);
        when(groupMessageMapper.findAfterSeq(101L, 20L, 3)).thenReturn(List.of(
                msg(21L, "\"a\""),
                msg(22L, "\"b\""),
                msg(23L, "\"c\"")
        ));

        GroupMessageService.PullResult result = service.pullOffline(101L, 2L, null, 2);

        assertTrue(result.isHasMore());
        assertEquals(22L, result.getNextCursorSeq());
        assertEquals(2, result.getMessages().size());
        assertEquals(21L, result.getMessages().get(0).getSeq());
        assertEquals("a", result.getMessages().get(0).getContent());
        assertEquals(22L, result.getMessages().get(1).getSeq());
        assertEquals("b", result.getMessages().get(1).getContent());
        verify(groupCursorMapper).upsertLastPullSeq(101L, 2L, 22L);
    }

    @Test
    void pullOfflineShouldPersistBaseCursorWhenNoNewMessages() {
        GroupMessageMapper groupMessageMapper = mock(GroupMessageMapper.class);
        GroupCursorMapper groupCursorMapper = mock(GroupCursorMapper.class);
        GroupMessageServiceImpl service = new GroupMessageServiceImpl(groupMessageMapper, groupCursorMapper, null);

        when(groupCursorMapper.findLastPullSeq(101L, 2L)).thenReturn(35L);
        when(groupMessageMapper.findAfterSeq(101L, 35L, 3)).thenReturn(List.of());

        GroupMessageService.PullResult result = service.pullOffline(101L, 2L, null, 2);

        assertEquals(false, result.isHasMore());
        assertEquals(35L, result.getNextCursorSeq());
        assertEquals(0, result.getMessages().size());
        verify(groupCursorMapper).upsertLastPullSeq(101L, 2L, 35L);
    }

    @Test
    void persistMessageShouldRejectSensitiveWordsBeforeInsert() {
        GroupMessageMapper groupMessageMapper = mock(GroupMessageMapper.class);
        GroupCursorMapper groupCursorMapper = mock(GroupCursorMapper.class);
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        GroupMessageServiceImpl service = new GroupMessageServiceImpl(groupMessageMapper, groupCursorMapper, sensitiveWordService);

        when(sensitiveWordService.filter("badword"))
                .thenReturn(new SensitiveWordFilterResult(true, SensitiveWordMode.REJECT, "badword", "badword"));

        org.junit.jupiter.api.Assertions.assertThrows(SensitiveWordHitException.class,
                () -> service.persistMessage(101L, 1L, null, "TEXT", "badword"));
        verify(groupMessageMapper, never()).insert(any(GroupMessageDO.class));
    }

    @Test
    void persistMessageShouldReplaceSensitiveWordsBeforeInsert() {
        GroupMessageMapper groupMessageMapper = mock(GroupMessageMapper.class);
        GroupCursorMapper groupCursorMapper = mock(GroupCursorMapper.class);
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        GroupMessageServiceImpl service = new GroupMessageServiceImpl(groupMessageMapper, groupCursorMapper, sensitiveWordService);
        AtomicReference<String> insertedContent = new AtomicReference<>();

        when(groupMessageMapper.findMaxSeq(101L)).thenReturn(3L);
        when(sensitiveWordService.filter("hello badword"))
                .thenReturn(new SensitiveWordFilterResult(true, SensitiveWordMode.REPLACE, "badword", "hello *******"));
        doAnswer((Answer<Integer>) invocation -> {
            GroupMessageDO message = invocation.getArgument(0);
            insertedContent.set(message.getContent());
            return 1;
        }).when(groupMessageMapper).insert(any(GroupMessageDO.class));

        GroupMessageService.PersistResult result = service.persistMessage(101L, 1L, null, "TEXT", "hello badword");

        assertEquals("\"hello *******\"", insertedContent.get());
        assertEquals("hello *******", result.getMessage().getContent());
    }

    @Test
    void persistFileMessageShouldKeepStructuredJson() {
        GroupMessageMapper groupMessageMapper = mock(GroupMessageMapper.class);
        GroupCursorMapper groupCursorMapper = mock(GroupCursorMapper.class);
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        GroupMessageServiceImpl service = new GroupMessageServiceImpl(groupMessageMapper, groupCursorMapper, sensitiveWordService);
        AtomicReference<String> insertedContent = new AtomicReference<>();

        when(groupMessageMapper.findMaxSeq(101L)).thenReturn(7L);
        doAnswer((Answer<Integer>) invocation -> {
            GroupMessageDO message = invocation.getArgument(0);
            insertedContent.set(message.getContent());
            return 1;
        }).when(groupMessageMapper).insert(any(GroupMessageDO.class));

        String fileContent = "{\"fileId\":\"f1\",\"fileName\":\"a.txt\",\"size\":12,\"contentType\":\"text/plain\",\"url\":\"/files/f1/a.txt\"}";
        GroupMessageService.PersistResult result = service.persistMessage(101L, 1L, null, "FILE", fileContent);

        verify(sensitiveWordService, never()).filter(any());
        assertEquals(fileContent, insertedContent.get());
        assertEquals(MessageContentCodec.MSG_TYPE_FILE, result.getMessage().getMsgType());
        assertEquals(fileContent, result.getMessage().getContent());
    }

    private GroupMessageDO msg(Long seq, String rawContent) {
        GroupMessageDO m = new GroupMessageDO();
        m.setGroupId(101L);
        m.setSeq(seq);
        m.setServerMsgId("s-" + seq);
        m.setFromUserId(9L);
        m.setMsgType("TEXT");
        m.setContent(rawContent);
        m.setStatus(1);
        m.setCreatedAt(new Date());
        return m;
    }
}
