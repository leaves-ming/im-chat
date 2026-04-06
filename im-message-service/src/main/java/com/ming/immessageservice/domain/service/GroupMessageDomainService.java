package com.ming.immessageservice.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imapicontract.message.GroupMessageDTO;
import com.ming.imapicontract.message.MessageContentCodec;
import com.ming.imapicontract.message.PullGroupOfflineResponse;
import com.ming.immessageservice.domain.exception.MessageRpcException;
import com.ming.immessageservice.infrastructure.dao.GroupMessageDO;
import com.ming.immessageservice.infrastructure.mapper.GroupCursorMapper;
import com.ming.immessageservice.infrastructure.mapper.GroupMessageMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 群消息领域服务。
 */
@Service
public class GroupMessageDomainService {

    private static final int MAX_SEQ_ALLOCATE_RETRY = 5;
    private static final int STATUS_SENT = 1;
    private static final int STATUS_RETRACTED = 2;

    private final GroupMessageMapper groupMessageMapper;
    private final GroupCursorMapper groupCursorMapper;
    private final DispatchOutboxDomainService dispatchOutboxDomainService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GroupMessageDomainService(GroupMessageMapper groupMessageMapper,
                                     GroupCursorMapper groupCursorMapper,
                                     DispatchOutboxDomainService dispatchOutboxDomainService) {
        this.groupMessageMapper = groupMessageMapper;
        this.groupCursorMapper = groupCursorMapper;
        this.dispatchOutboxDomainService = dispatchOutboxDomainService;
    }

    public GroupMessageDTO getByServerMsgId(String serverMsgId) {
        return toGroupMessageDTO(decodeMessage(requiredMessage(serverMsgId)));
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupMessageDTO persistMessage(Long groupId, Long fromUserId, String clientMsgId, String msgType, String content) {
        String normalizedMsgType = MessageContentCodec.normalizeMsgType(msgType);
        for (int attempt = 1; attempt <= MAX_SEQ_ALLOCATE_RETRY; attempt++) {
            try {
                return tryPersistMessage(groupId, fromUserId, clientMsgId, normalizedMsgType, content);
            } catch (DuplicateKeyException ex) {
                if (attempt == MAX_SEQ_ALLOCATE_RETRY) {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("persist group message failed");
    }

    @Transactional(rollbackFor = Exception.class)
    protected GroupMessageDTO tryPersistMessage(Long groupId, Long fromUserId, String clientMsgId, String msgType, String content) {
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
        dispatchOutboxDomainService.appendGroupMessage(message);
        return toGroupMessageDTO(message);
    }

    @Transactional(rollbackFor = Exception.class)
    public PullGroupOfflineResponse pullOffline(Long groupId, Long userId, Long cursorSeq, int limit) {
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

        List<GroupMessageDTO> messages = new ArrayList<>(selected.size());
        for (GroupMessageDO item : selected) {
            messages.add(toGroupMessageDTO(decodeMessage(item)));
        }

        long nextCursorSeq = baseSeq;
        if (!messages.isEmpty()) {
            nextCursorSeq = messages.getLast().seq();
        }
        groupCursorMapper.upsertLastPullSeq(groupId, userId, nextCursorSeq);
        return new PullGroupOfflineResponse(messages, hasMore, nextCursorSeq);
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupMessageDTO recallMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds) {
        GroupMessageDO exist = requiredMessage(serverMsgId);
        if (isRetracted(exist)) {
            throw new MessageRpcException("INVALID_PARAM", "message already retracted");
        }
        if (!withinRecallWindow(exist.getCreatedAt(), recallWindowSeconds)) {
            throw new MessageRpcException("FORBIDDEN", "message recall window expired");
        }
        int updated = groupMessageMapper.updateRetractionByServerMsgId(serverMsgId, STATUS_RETRACTED, new Date(), operatorUserId);
        if (updated <= 0) {
            throw new IllegalStateException("recall group message failed serverMsgId=" + serverMsgId);
        }
        GroupMessageDO recalled = decodeMessage(requiredMessage(serverMsgId));
        dispatchOutboxDomainService.appendGroupRecall(recalled);
        return toGroupMessageDTO(recalled);
    }

    public boolean hasFileAccess(String fileId, Long userId) {
        Integer access = groupMessageMapper.existsFileForActiveMember(fileId, userId);
        return access != null && access > 0;
    }

    private GroupMessageDO requiredMessage(String serverMsgId) {
        GroupMessageDO message = groupMessageMapper.findByServerMsgId(serverMsgId);
        if (message == null) {
            throw new MessageRpcException("INVALID_PARAM", "serverMsgId not found");
        }
        return message;
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

    private GroupMessageDTO toGroupMessageDTO(GroupMessageDO message) {
        if (message == null) {
            return null;
        }
        return new GroupMessageDTO(
                message.getId(),
                message.getGroupId(),
                message.getSeq(),
                message.getServerMsgId(),
                message.getClientMsgId(),
                message.getFromUserId(),
                message.getMsgType(),
                message.getContent(),
                message.getStatus(),
                message.getCreatedAt(),
                message.getRetractedAt(),
                message.getRetractedBy()
        );
    }
}
