package com.ming.imchatserver.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.dao.GroupMessageDO;
import com.ming.imchatserver.mapper.GroupCursorMapper;
import com.ming.imchatserver.mapper.GroupMessageMapper;
import com.ming.imchatserver.sensitive.SensitiveWordFilterResult;
import com.ming.imchatserver.sensitive.SensitiveWordHitException;
import com.ming.imchatserver.sensitive.SensitiveWordService;
import com.ming.imchatserver.service.GroupMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * {@link GroupMessageService} 默认实现。
 */
@Service
public class GroupMessageServiceImpl implements GroupMessageService {

    private static final Logger logger = LoggerFactory.getLogger(GroupMessageServiceImpl.class);
    private static final int MAX_SEQ_ALLOCATE_RETRY = 5;

    private final GroupMessageMapper groupMessageMapper;
    private final GroupCursorMapper groupCursorMapper;
    private final SensitiveWordService sensitiveWordService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GroupMessageServiceImpl(GroupMessageMapper groupMessageMapper,
                                   GroupCursorMapper groupCursorMapper,
                                   SensitiveWordService sensitiveWordService) {
        this.groupMessageMapper = groupMessageMapper;
        this.groupCursorMapper = groupCursorMapper;
        this.sensitiveWordService = sensitiveWordService;
    }

    @Override
    public PersistResult persistTextMessage(Long groupId, Long fromUserId, String clientMsgId, String content) {
        String filteredContent = content;
        if (sensitiveWordService != null) {
            SensitiveWordFilterResult filterResult = sensitiveWordService.filter(content);
            if (filterResult.shouldReject()) {
                throw new SensitiveWordHitException(filterResult.getMatchedWord());
            }
            filteredContent = filterResult.getOutputText();
        }
        for (int attempt = 1; attempt <= MAX_SEQ_ALLOCATE_RETRY; attempt++) {
            try {
                return tryPersistTextMessage(groupId, fromUserId, clientMsgId, filteredContent);
            } catch (DuplicateKeyException ex) {
                // uk_group_seq 并发冲突时重试分配 seq。
                logger.warn("persistTextMessage duplicate seq conflict, retry attempt={} groupId={}", attempt, groupId);
            }
        }
        throw new IllegalStateException("persist group message failed after retries, groupId=" + groupId);
    }

    @Transactional(rollbackFor = Exception.class)
    protected PersistResult tryPersistTextMessage(Long groupId, Long fromUserId, String clientMsgId, String content) {
        Long maxSeq = groupMessageMapper.findMaxSeq(groupId);
        long nextSeq = (maxSeq == null ? 0L : maxSeq) + 1L;

        Date now = new Date();
        GroupMessageDO message = new GroupMessageDO();
        message.setGroupId(groupId);
        message.setSeq(nextSeq);
        message.setServerMsgId(UUID.randomUUID().toString());
        message.setClientMsgId(normalizeClientMsgId(clientMsgId));
        message.setFromUserId(fromUserId);
        message.setMsgType("TEXT");
        message.setContent(toJsonText(content));
        message.setStatus(1);
        message.setCreatedAt(now);

        groupMessageMapper.insert(message);
        message.setContent(content);
        return new PersistResult(message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PullResult pullOffline(Long groupId, Long userId, Long cursorSeq, int limit) {
        long baseSeq;
        if (cursorSeq == null) {
            Long lastPullSeq = groupCursorMapper.findLastPullSeq(groupId, userId);
            baseSeq = lastPullSeq == null ? 0L : Math.max(0L, lastPullSeq);
        } else {
            baseSeq = Math.max(0L, cursorSeq);
        }

        List<GroupMessageDO> fetched = groupMessageMapper.findAfterSeq(groupId, baseSeq, limit + 1);
        boolean hasMore = fetched.size() > limit;
        List<GroupMessageDO> selected = hasMore ? fetched.subList(0, limit) : fetched;

        List<GroupMessageDO> messages = new ArrayList<>(selected.size());
        for (GroupMessageDO item : selected) {
            GroupMessageDO decoded = new GroupMessageDO();
            decoded.setId(item.getId());
            decoded.setGroupId(item.getGroupId());
            decoded.setSeq(item.getSeq());
            decoded.setServerMsgId(item.getServerMsgId());
            decoded.setClientMsgId(item.getClientMsgId());
            decoded.setFromUserId(item.getFromUserId());
            decoded.setMsgType(item.getMsgType());
            decoded.setContent(fromJsonText(item.getContent()));
            decoded.setStatus(item.getStatus());
            decoded.setCreatedAt(item.getCreatedAt());
            messages.add(decoded);
        }

        long nextCursorSeq = baseSeq;
        if (!messages.isEmpty()) {
            nextCursorSeq = messages.get(messages.size() - 1).getSeq();
        }
        groupCursorMapper.upsertLastPullSeq(groupId, userId, nextCursorSeq);
        return new PullResult(messages, hasMore, nextCursorSeq);
    }

    private String normalizeClientMsgId(String clientMsgId) {
        if (clientMsgId == null) {
            return null;
        }
        String trimmed = clientMsgId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String toJsonText(String plainText) {
        try {
            return objectMapper.writeValueAsString(plainText);
        } catch (Exception ex) {
            throw new IllegalStateException("serialize group content failed", ex);
        }
    }

    private String fromJsonText(String rawContent) {
        if (rawContent == null) {
            return null;
        }
        try {
            return objectMapper.readValue(rawContent, String.class);
        } catch (Exception ex) {
            return rawContent;
        }
    }
}
