package com.ming.imchatserver.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.dao.GroupMessageDO;
import com.ming.imchatserver.mapper.GroupCursorMapper;
import com.ming.imchatserver.mapper.GroupMessageMapper;
import com.ming.imchatserver.message.MessageContentCodec;
import com.ming.imchatserver.sensitive.SensitiveWordFilterResult;
import com.ming.imchatserver.sensitive.SensitiveWordHitException;
import com.ming.imchatserver.sensitive.SensitiveWordService;
import com.ming.imchatserver.service.FileService;
import com.ming.imchatserver.service.GroupMessageService;
import com.ming.imchatserver.service.GroupService;
import com.ming.imchatserver.service.MessageRecallException;
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
    private static final int STATUS_SENT = 1;
    private static final int STATUS_RETRACTED = 2;

    private final GroupMessageMapper groupMessageMapper;
    private final GroupCursorMapper groupCursorMapper;
    private final SensitiveWordService sensitiveWordService;
    private final FileService fileService;
    private final GroupService groupService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GroupMessageServiceImpl(GroupMessageMapper groupMessageMapper,
                                   GroupCursorMapper groupCursorMapper,
                                   SensitiveWordService sensitiveWordService,
                                   FileService fileService,
                                   GroupService groupService) {
        this.groupMessageMapper = groupMessageMapper;
        this.groupCursorMapper = groupCursorMapper;
        this.sensitiveWordService = sensitiveWordService;
        this.fileService = fileService;
        this.groupService = groupService;
    }

    @Override
    public PersistResult persistMessage(Long groupId, Long fromUserId, String clientMsgId, String msgType, String content) {
        String normalizedMsgType = MessageContentCodec.normalizeMsgType(msgType);
        String filteredContent = content;
        if (sensitiveWordService != null && MessageContentCodec.MSG_TYPE_TEXT.equals(normalizedMsgType)) {
            SensitiveWordFilterResult filterResult = sensitiveWordService.filter(content);
            if (filterResult.shouldReject()) {
                throw new SensitiveWordHitException(filterResult.getMatchedWord());
            }
            filteredContent = filterResult.getOutputText();
        }
        if (MessageContentCodec.MSG_TYPE_FILE.equals(normalizedMsgType)) {
            if (fileService == null) {
                throw new IllegalStateException("file service unavailable");
            }
            filteredContent = fileService.consumeUploadTokenAndBuildFileMessageContent(filteredContent, fromUserId);
        }
        for (int attempt = 1; attempt <= MAX_SEQ_ALLOCATE_RETRY; attempt++) {
            try {
                return tryPersistMessage(groupId, fromUserId, clientMsgId, normalizedMsgType, filteredContent);
            } catch (DuplicateKeyException ex) {
                // uk_group_seq 并发冲突时重试分配 seq。
                logger.warn("persistTextMessage duplicate seq conflict, retry attempt={} groupId={}", attempt, groupId);
            }
        }
        throw new IllegalStateException("persist group message failed after retries, groupId=" + groupId);
    }

    @Transactional(rollbackFor = Exception.class)
    protected PersistResult tryPersistMessage(Long groupId, Long fromUserId, String clientMsgId, String msgType, String content) {
        Long maxSeq = groupMessageMapper.findMaxSeq(groupId);
        long nextSeq = (maxSeq == null ? 0L : maxSeq) + 1L;

        Date now = new Date();
        GroupMessageDO message = new GroupMessageDO();
        message.setGroupId(groupId);
        message.setSeq(nextSeq);
        message.setServerMsgId(UUID.randomUUID().toString());
        message.setClientMsgId(normalizeClientMsgId(clientMsgId));
        message.setFromUserId(fromUserId);
        message.setMsgType(msgType);
        message.setContent(toStoredContent(msgType, content));
        message.setStatus(STATUS_SENT);
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
            messages.add(decodeMessage(item));
        }

        long nextCursorSeq = baseSeq;
        if (!messages.isEmpty()) {
            nextCursorSeq = messages.get(messages.size() - 1).getSeq();
        }
        groupCursorMapper.upsertLastPullSeq(groupId, userId, nextCursorSeq);
        return new PullResult(messages, hasMore, nextCursorSeq);
    }

    @Override
    public GroupMessageDO findByServerMsgId(String serverMsgId) {
        return decodeMessage(groupMessageMapper.findByServerMsgId(serverMsgId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupMessageDO recallMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds) {
        GroupMessageDO exist = groupMessageMapper.findByServerMsgId(serverMsgId);
        if (exist == null) {
            throw new MessageRecallException("INVALID_PARAM", "serverMsgId not found");
        }
        if (groupService == null) {
            throw new IllegalStateException("group service unavailable");
        }
        if (groupService.getActiveMember(exist.getGroupId(), operatorUserId) == null) {
            throw new MessageRecallException("FORBIDDEN", "operator is not active group member");
        }
        if (!groupService.canRecallMessage(exist.getGroupId(), operatorUserId, exist.getFromUserId())) {
            throw new MessageRecallException("FORBIDDEN", "insufficient role to recall message");
        }
        if (isRetracted(exist)) {
            throw new MessageRecallException("INVALID_PARAM", "message already retracted");
        }
        if (!withinRecallWindow(exist.getCreatedAt(), recallWindowSeconds)) {
            throw new MessageRecallException("FORBIDDEN", "message recall window expired");
        }

        Date now = new Date();
        int updated = groupMessageMapper.updateRetractionByServerMsgId(serverMsgId, STATUS_RETRACTED, now, operatorUserId);
        if (updated <= 0) {
            GroupMessageDO latest = groupMessageMapper.findByServerMsgId(serverMsgId);
            if (isRetracted(latest)) {
                throw new MessageRecallException("INVALID_PARAM", "message already retracted");
            }
            throw new IllegalStateException("recall group message failed serverMsgId=" + serverMsgId);
        }
        return decodeMessage(groupMessageMapper.findByServerMsgId(serverMsgId));
    }

    private String normalizeClientMsgId(String clientMsgId) {
        if (clientMsgId == null) {
            return null;
        }
        String trimmed = clientMsgId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String toStoredContent(String msgType, String content) {
        if (MessageContentCodec.MSG_TYPE_FILE.equals(MessageContentCodec.normalizeMsgType(msgType))) {
            return content;
        }
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception ex) {
            throw new IllegalStateException("serialize group content failed", ex);
        }
    }

    private String fromStoredContent(String msgType, String rawContent) {
        return MessageContentCodec.decodeFromStorage(msgType, rawContent);
    }

    private GroupMessageDO decodeMessage(GroupMessageDO item) {
        if (item == null) {
            return null;
        }
        GroupMessageDO decoded = new GroupMessageDO();
        decoded.setId(item.getId());
        decoded.setGroupId(item.getGroupId());
        decoded.setSeq(item.getSeq());
        decoded.setServerMsgId(item.getServerMsgId());
        decoded.setClientMsgId(item.getClientMsgId());
        decoded.setFromUserId(item.getFromUserId());
        decoded.setMsgType(MessageContentCodec.normalizeMsgType(item.getMsgType()));
        decoded.setStatus(item.getStatus());
        decoded.setCreatedAt(item.getCreatedAt());
        decoded.setRetractedAt(item.getRetractedAt());
        decoded.setRetractedBy(item.getRetractedBy());
        decoded.setContent(isRetracted(item) ? null : fromStoredContent(decoded.getMsgType(), item.getContent()));
        return decoded;
    }

    private boolean isRetracted(GroupMessageDO message) {
        return message != null && (Integer.valueOf(STATUS_RETRACTED).equals(message.getStatus()) || message.getRetractedAt() != null);
    }

    private boolean withinRecallWindow(Date createdAt, long recallWindowSeconds) {
        if (createdAt == null || recallWindowSeconds <= 0) {
            return false;
        }
        long elapsedMs = System.currentTimeMillis() - createdAt.getTime();
        return elapsedMs >= 0 && elapsedMs <= recallWindowSeconds * 1000L;
    }
}
