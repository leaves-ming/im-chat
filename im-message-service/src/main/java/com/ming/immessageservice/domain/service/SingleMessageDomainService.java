package com.ming.immessageservice.domain.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ming.imapicontract.message.CursorPageDTO;
import com.ming.imapicontract.message.MessageContentCodec;
import com.ming.imapicontract.message.MessageDTO;
import com.ming.imapicontract.message.SyncCursorDTO;
import com.ming.immessageservice.domain.exception.MessageRpcException;
import com.ming.immessageservice.infrastructure.dao.MessageDO;
import com.ming.immessageservice.infrastructure.dao.SingleCursorDO;
import com.ming.immessageservice.infrastructure.mapper.MessageMapper;
import com.ming.immessageservice.infrastructure.mapper.SingleCursorMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 单聊消息领域服务。
 */
@Service
public class SingleMessageDomainService {

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_ACKED = "ACKED";
    public static final String STATUS_RETRACTED = "RETRACTED";

    private final MessageMapper messageMapper;
    private final SingleCursorMapper singleCursorMapper;
    private final DispatchOutboxDomainService dispatchOutboxDomainService;

    public SingleMessageDomainService(MessageMapper messageMapper,
                                      SingleCursorMapper singleCursorMapper,
                                      DispatchOutboxDomainService dispatchOutboxDomainService) {
        this.messageMapper = messageMapper;
        this.singleCursorMapper = singleCursorMapper;
        this.dispatchOutboxDomainService = dispatchOutboxDomainService;
    }

    @Transactional(rollbackFor = Exception.class)
    public PersistResult persistSingleMessage(Long fromUserId,
                                              Long targetUserId,
                                              String clientMsgId,
                                              String msgType,
                                              String content) {
        MessageDO message = new MessageDO();
        message.setServerMsgId(UUID.randomUUID().toString());
        message.setClientMsgId(normalizeClientMsgId(clientMsgId));
        message.setFromUserId(fromUserId);
        message.setToUserId(targetUserId);
        message.setMsgType(MessageContentCodec.normalizeMsgType(msgType));
        message.setContent(MessageContentCodec.encodeForStorage(message.getMsgType(), content));
        message.setStatus(STATUS_SENT);
        try {
            messageMapper.insert(message);
            dispatchOutboxDomainService.appendSingleMessage(decodeMessage(message));
            return new PersistResult(message.getServerMsgId(), true);
        } catch (DuplicateKeyException ex) {
            if (message.getClientMsgId() == null) {
                throw ex;
            }
            MessageDO existing = messageMapper.findByFromUserIdAndClientMsgId(fromUserId, message.getClientMsgId());
            if (existing == null) {
                throw ex;
            }
            return new PersistResult(existing.getServerMsgId(), false);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public AckResult reportAck(Long reporterUserId, String serverMsgId, String targetStatus) {
        MessageDO existing = requiredMessage(serverMsgId);
        if (reporterUserId == null || !reporterUserId.equals(existing.getToUserId())) {
            throw new MessageRpcException("FORBIDDEN", "not message recipient");
        }
        int updated = updateStatusByServerMsgId(serverMsgId, targetStatus);
        Date ackAt = updated > 0 ? new Date() : null;
        MessageDO latest = updated > 0 ? requiredMessage(serverMsgId) : existing;
        if (updated > 0) {
            dispatchOutboxDomainService.appendStatusNotify(latest, targetStatus, latest.getFromUserId());
        }
        return new AckResult(toMessageDTO(latest), targetStatus, updated, ackAt);
    }

    public CursorPageDTO pullOffline(Long userId, String deviceId, SyncCursorDTO syncCursor, int limit, boolean useCheckpoint) {
        if (useCheckpoint) {
            SyncCursorDTO checkpoint = getSyncCursor(userId, deviceId);
            if (checkpoint == null || checkpoint.cursorCreatedAt() == null || checkpoint.cursorId() == null) {
                return pullRecent(userId, limit);
            }
            return pullAfterCursor(userId, checkpoint, limit, checkpoint, deviceId);
        }
        if (syncCursor == null || syncCursor.cursorCreatedAt() == null || syncCursor.cursorId() == null) {
            return pullRecent(userId, limit);
        }
        return pullAfterCursor(userId, syncCursor, limit, syncCursor, deviceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void advanceCursor(Long userId, String deviceId, Date cursorCreatedAt, Long cursorId) {
        if (userId == null || deviceId == null || deviceId.isBlank() || cursorCreatedAt == null || cursorId == null) {
            return;
        }
        singleCursorMapper.upsertLastPullCursor(userId, deviceId, cursorCreatedAt, cursorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public MessageDTO recallSingleMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds) {
        MessageDO existing = requiredMessage(serverMsgId);
        if (operatorUserId == null || !operatorUserId.equals(existing.getFromUserId())) {
            throw new MessageRpcException("FORBIDDEN", "only sender can recall message");
        }
        if (isRetracted(existing)) {
            throw new MessageRpcException("INVALID_PARAM", "message already retracted");
        }
        if (!withinRecallWindow(existing.getCreatedAt(), recallWindowSeconds)) {
            throw new MessageRpcException("FORBIDDEN", "message recall window expired");
        }
        int updated = messageMapper.updateRetractionByServerMsgId(serverMsgId, STATUS_RETRACTED, new Date(), operatorUserId);
        if (updated <= 0) {
            throw new IllegalStateException("recall message failed serverMsgId=" + serverMsgId);
        }
        MessageDTO recalled = toMessageDTO(requiredMessage(serverMsgId));
        dispatchOutboxDomainService.appendSingleRecall(requiredMessage(serverMsgId));
        return recalled;
    }

    public boolean hasFileAccess(String fileId, Long userId) {
        Integer access = messageMapper.existsFileParticipant(fileId, userId);
        return access != null && access > 0;
    }

    public SyncCursorDTO getSyncCursor(Long userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return null;
        }
        SingleCursorDO cursor = singleCursorMapper.findByUserIdAndDeviceId(userId, deviceId);
        if (cursor == null || cursor.getLastPullCreatedAt() == null || cursor.getLastPullMessageId() == null) {
            return null;
        }
        return new SyncCursorDTO(cursor.getLastPullCreatedAt(), cursor.getLastPullMessageId());
    }

    private CursorPageDTO pullAfterCursor(Long userId,
                                          SyncCursorDTO syncCursor,
                                          int limit,
                                          SyncCursorDTO baseCursor,
                                          String deviceId) {
        try (Page<MessageDO> ignored = PageHelper.startPage(1, limit + 1, false)) {
            List<MessageDO> fetched = messageMapper.findByToUserIdAfterCursor(userId, syncCursor.cursorCreatedAt(), syncCursor.cursorId());
            return buildPageResult(fetched, limit, false, baseCursor, deviceId);
        }
    }

    private CursorPageDTO pullRecent(Long userId, int limit) {
        try (Page<MessageDO> ignored = PageHelper.startPage(1, limit + 1, false)) {
            List<MessageDO> fetched = messageMapper.findRecentByToUserId(userId);
            return buildPageResult(fetched, limit, true, null, null);
        }
    }

    private CursorPageDTO buildPageResult(List<MessageDO> fetched,
                                          int limit,
                                          boolean reverseToAsc,
                                          SyncCursorDTO baseCursor,
                                          String deviceId) {
        boolean hasMore = fetched.size() > limit;
        List<MessageDO> selected = hasMore ? fetched.subList(0, limit) : fetched;
        List<MessageDTO> messages = new ArrayList<>(selected.size());
        for (MessageDO item : selected) {
            messages.add(toMessageDTO(decodeMessage(item)));
        }
        if (reverseToAsc) {
            Collections.reverse(messages);
        }
        Date nextCursorCreatedAt = null;
        Long nextCursorId = null;
        if (!messages.isEmpty()) {
            MessageDTO last = messages.get(messages.size() - 1);
            nextCursorCreatedAt = last.createdAt();
            nextCursorId = last.id();
        } else if (baseCursor != null && baseCursor.cursorCreatedAt() != null && baseCursor.cursorId() != null) {
            nextCursorCreatedAt = baseCursor.cursorCreatedAt();
            nextCursorId = baseCursor.cursorId();
        } else if (deviceId != null && !deviceId.isBlank()) {
            nextCursorId = 0L;
        }
        return new CursorPageDTO(messages, hasMore, nextCursorCreatedAt, nextCursorId);
    }

    private int updateStatusByServerMsgId(String serverMsgId, String status) {
        if (serverMsgId == null || status == null || STATUS_RETRACTED.equalsIgnoreCase(status)) {
            return 0;
        }
        MessageDO existing = requiredMessage(serverMsgId);
        if (isRetracted(existing)) {
            return 0;
        }
        int currentOrd = statusOrd(existing.getStatus());
        int targetOrd = statusOrd(status);
        if (targetOrd == 0 || targetOrd != currentOrd + 1) {
            return 0;
        }
        Date now = new Date();
        Date deliveredAt = targetOrd >= 2 ? (existing.getDeliveredAt() == null ? now : existing.getDeliveredAt()) : null;
        Date ackedAt = targetOrd >= 3 ? (existing.getAckedAt() == null ? now : existing.getAckedAt()) : null;
        return messageMapper.updateStatusByServerMsgId(serverMsgId, status, deliveredAt, ackedAt);
    }

    private MessageDO requiredMessage(String serverMsgId) {
        MessageDO message = messageMapper.findByServerMsgId(serverMsgId);
        if (message == null) {
            throw new MessageRpcException("INVALID_PARAM", "serverMsgId not found");
        }
        return decodeMessage(message);
    }

    private MessageDO decodeMessage(MessageDO message) {
        if (message == null) {
            return null;
        }
        MessageDO decoded = new MessageDO();
        decoded.setId(message.getId());
        decoded.setServerMsgId(message.getServerMsgId());
        decoded.setClientMsgId(message.getClientMsgId());
        decoded.setFromUserId(message.getFromUserId());
        decoded.setToUserId(message.getToUserId());
        decoded.setMsgType(MessageContentCodec.normalizeMsgType(message.getMsgType()));
        decoded.setContent(MessageContentCodec.decodeFromStorage(decoded.getMsgType(), message.getContent()));
        decoded.setStatus(message.getStatus());
        decoded.setCreatedAt(message.getCreatedAt());
        decoded.setDeliveredAt(message.getDeliveredAt());
        decoded.setAckedAt(message.getAckedAt());
        decoded.setRetractedAt(message.getRetractedAt());
        decoded.setRetractedBy(message.getRetractedBy());
        if (isRetracted(message)) {
            decoded.setStatus(STATUS_RETRACTED);
            decoded.setContent(null);
        }
        return decoded;
    }

    private MessageDTO toMessageDTO(MessageDO message) {
        if (message == null) {
            return null;
        }
        return new MessageDTO(
                message.getId(),
                message.getServerMsgId(),
                message.getClientMsgId(),
                message.getFromUserId(),
                message.getToUserId(),
                message.getMsgType(),
                message.getContent(),
                message.getStatus(),
                message.getCreatedAt(),
                message.getDeliveredAt(),
                message.getAckedAt(),
                message.getRetractedAt(),
                message.getRetractedBy()
        );
    }

    private boolean isRetracted(MessageDO message) {
        return message != null && (STATUS_RETRACTED.equalsIgnoreCase(message.getStatus()) || message.getRetractedAt() != null);
    }

    private boolean withinRecallWindow(Date createdAt, long recallWindowSeconds) {
        if (createdAt == null || recallWindowSeconds <= 0) {
            return false;
        }
        long elapsedMs = System.currentTimeMillis() - createdAt.getTime();
        return elapsedMs >= 0 && elapsedMs <= recallWindowSeconds * 1000L;
    }

    private int statusOrd(String status) {
        if (status == null) {
            return 0;
        }
        return switch (status.toUpperCase()) {
            case STATUS_SENT -> 1;
            case STATUS_DELIVERED -> 2;
            case STATUS_ACKED -> 3;
            default -> 0;
        };
    }

    private String normalizeClientMsgId(String clientMsgId) {
        if (clientMsgId == null) {
            return null;
        }
        String trimmed = clientMsgId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record PersistResult(String serverMsgId, boolean createdNew) {
    }

    public record AckResult(MessageDTO message, String status, int updated, Date ackAt) {
    }
}
