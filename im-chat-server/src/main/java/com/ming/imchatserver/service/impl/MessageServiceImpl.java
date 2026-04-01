package com.ming.imchatserver.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.config.ReliabilityProperties;
import com.ming.imchatserver.config.RedisStateProperties;
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.dao.OutboxMessageDO;
import com.ming.imchatserver.mapper.MessageMapper;
import com.ming.imchatserver.mapper.OutboxMapper;
import com.ming.imchatserver.mapper.SingleCursorMapper;
import com.ming.imchatserver.message.MessageContentCodec;
import com.ming.imchatserver.mq.DispatchMessagePayload;
import com.ming.imchatserver.sensitive.SensitiveWordFilterResult;
import com.ming.imchatserver.sensitive.SensitiveWordHitException;
import com.ming.imchatserver.sensitive.SensitiveWordService;
import com.ming.imchatserver.service.FileService;
import com.ming.imchatserver.service.MessageService;
import com.ming.imchatserver.service.MessageRecallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * {@link MessageService} 的默认实现。
 * <p>
 * 负责消息落库、幂等回查、状态推进和游标分页结果组装。
 */
@Service
public class MessageServiceImpl implements MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageServiceImpl.class);
    private static final int OUTBOX_STATUS_NEW = 0;
    private static final String DEFAULT_DISPATCH_TOPIC = "im.msg.dispatch";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_DELIVERED = "DELIVERED";
    private static final String STATUS_ACKED = "ACKED";
    private static final String STATUS_RETRACTED = "RETRACTED";
    private final MessageMapper messageMapper;
    private final SingleCursorMapper singleCursorMapper;
    private final OutboxMapper outboxMapper;
    private final ReliabilityProperties reliabilityProperties;
    private final RedisStateProperties redisStateProperties;
    private final SensitiveWordService sensitiveWordService;
    private final FileService fileService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param messageMapper 消息表数据访问对象
     */
    @Autowired
    public MessageServiceImpl(MessageMapper messageMapper,
                              SingleCursorMapper singleCursorMapper,
                              OutboxMapper outboxMapper,
                              ReliabilityProperties reliabilityProperties,
                              SensitiveWordService sensitiveWordService,
                              FileService fileService) {
        this(messageMapper, singleCursorMapper, outboxMapper, reliabilityProperties, null, sensitiveWordService, fileService);
    }

    public MessageServiceImpl(MessageMapper messageMapper,
                              OutboxMapper outboxMapper,
                              ReliabilityProperties reliabilityProperties,
                              SensitiveWordService sensitiveWordService,
                              FileService fileService) {
        this(messageMapper, null, outboxMapper, reliabilityProperties, null, sensitiveWordService, fileService);
    }

    public MessageServiceImpl(MessageMapper messageMapper,
                              SingleCursorMapper singleCursorMapper,
                              OutboxMapper outboxMapper,
                              ReliabilityProperties reliabilityProperties,
                              RedisStateProperties redisStateProperties,
                              SensitiveWordService sensitiveWordService,
                              FileService fileService) {
        this.messageMapper = messageMapper;
        this.singleCursorMapper = singleCursorMapper;
        this.outboxMapper = outboxMapper;
        this.reliabilityProperties = reliabilityProperties;
        this.redisStateProperties = redisStateProperties;
        this.sensitiveWordService = sensitiveWordService;
        this.fileService = fileService;
    }

    /**
     * 单元测试兼容构造函数。
     */
    public MessageServiceImpl(MessageMapper messageMapper) {
        this(messageMapper, null, null, null, null, null, null);
    }

    /**
     * 单元测试兼容构造函数（含 outbox）。
     */
    public MessageServiceImpl(MessageMapper messageMapper, OutboxMapper outboxMapper) {
        this(messageMapper, null, outboxMapper, null, null, null, null);
    }

    /**
     * 持久化消息。
     * <p>
     * 若发生唯一键冲突（from_user_id + client_msg_id），会回查已有记录并返回已有 serverMsgId，
     * 用于客户端重试幂等。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PersistResult persistMessage(MessageDO msg) {
        String msgType = msg == null ? MessageContentCodec.MSG_TYPE_TEXT : MessageContentCodec.normalizeMsgType(msg.getMsgType());
        String logicalContent = msg == null ? null : msg.getContent();
        if (sensitiveWordService != null && MessageContentCodec.MSG_TYPE_TEXT.equals(msgType)) {
            SensitiveWordFilterResult filterResult = sensitiveWordService.filter(msg == null ? null : msg.getContent());
            if (filterResult.shouldReject()) {
                throw new SensitiveWordHitException(filterResult.getMatchedWord());
            }
            logicalContent = filterResult.getOutputText();
        }
        if (msg != null) {
            msg.setMsgType(msgType);
            msg.setContent(logicalContent);
        }
        msg.setClientMsgId(normalizeClientMsgId(msg.getClientMsgId()));
        if (MessageContentCodec.MSG_TYPE_FILE.equals(msgType)) {
            if (fileService == null) {
                throw new IllegalStateException("file service unavailable");
            }
            logicalContent = fileService.consumeUploadTokenAndBuildFileMessageContent(logicalContent, msg.getFromUserId());
            msg.setContent(logicalContent);
        }

        String serverMsgId = UUID.randomUUID().toString();
        msg.setServerMsgId(serverMsgId);
        msg.setStatus(msg.getStatus() == null ? STATUS_SENT : msg.getStatus());
        String storedContent = MessageContentCodec.encodeForStorage(msgType, logicalContent);

        try {
            msg.setContent(storedContent);
            messageMapper.insert(msg);
            msg.setContent(logicalContent);
            appendOutbox(msg, logicalContent);
            logger.info("persistMessage: inserted new message serverMsgId={} from={} to={}", serverMsgId, msg.getFromUserId(), msg.getToUserId());
            return new PersistResult(serverMsgId, true);
        } catch (DuplicateKeyException ex) {
            msg.setContent(logicalContent);
            if (msg.getClientMsgId() != null) {
                MessageDO exist = messageMapper.findByFromUserIdAndClientMsgId(msg.getFromUserId(), msg.getClientMsgId());
                if (exist != null) {
                    logger.info("persistMessage: idempotent hit for fromUser={} clientMsgId={}, returning existing serverMsgId={}",
                            msg.getFromUserId(), msg.getClientMsgId(), exist.getServerMsgId());
                    return new PersistResult(exist.getServerMsgId(), false);
                }
            }
            throw ex;
        }
    }

    /**
     * 在消息事务内写 outbox，保证“落库成功 => 最终可分发”。
     */
    private void appendOutbox(MessageDO msg, String logicalContent) {
        if (outboxMapper == null) {
            return;
        }
        try {
            DispatchMessagePayload payload = new DispatchMessagePayload();
            payload.setEventId(UUID.randomUUID().toString());
            payload.setEventType(DispatchMessagePayload.EVENT_TYPE_MESSAGE);
            payload.setOriginServerId(currentServerId());
            payload.setServerMsgId(msg.getServerMsgId());
            payload.setClientMsgId(msg.getClientMsgId());
            payload.setFromUserId(msg.getFromUserId());
            payload.setToUserId(msg.getToUserId());
            payload.setContent(logicalContent);
            payload.setMsgType(msg.getMsgType());
            payload.setCreatedAt(msg.getCreatedAt() == null ? null : msg.getCreatedAt().toInstant().toString());
            appendSingleDispatchOutbox(msg.getId(), payload);
        } catch (Exception ex) {
            throw new IllegalStateException("appendOutbox failed serverMsgId=" + msg.getServerMsgId(), ex);
        }
    }

    /**
     * 规范化客户端消息 ID：空白字符串统一转为 null。
     */
    private String normalizeClientMsgId(String clientMsgId) {
        if (clientMsgId == null) {
            return null;
        }
        String trimmed = clientMsgId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 按状态机推进消息状态。
     * <p>
     * 仅允许状态单步前进，不允许回退、跳级或重复更新。
     */
    @Override
    public int updateStatusByServerMsgId(String serverMsgId, String status) {
        if (serverMsgId == null || status == null) {
            return 0;
        }
        if (STATUS_RETRACTED.equalsIgnoreCase(status)) {
            return 0;
        }

        int targetOrd = statusOrd(status);
        if (targetOrd == 0) {
            return 0;
        }

        MessageDO exist = messageMapper.findByServerMsgId(serverMsgId);
        if (exist == null) {
            logger.warn("updateStatusByServerMsgId: serverMsgId={} not found", serverMsgId);
            return 0;
        }
        if (isRetracted(exist)) {
            logger.debug("updateStatusByServerMsgId: serverMsgId={} already retracted, skip status update", serverMsgId);
            return 0;
        }

        int curOrd = statusOrd(exist.getStatus());
        if (targetOrd != curOrd + 1) {
            logger.debug("updateStatusByServerMsgId: serverMsgId={} currentStatus={} targetStatus={} no-op",
                    serverMsgId, exist.getStatus(), status);
            return 0;
        }

        Date now = new Date();
        Date deliveredAt = null;
        Date ackedAt = null;
        if (targetOrd >= 2) {
            deliveredAt = exist.getDeliveredAt() == null ? now : exist.getDeliveredAt();
        }
        if (targetOrd >= 3) {
            ackedAt = exist.getAckedAt() == null ? now : exist.getAckedAt();
        }

        int updated = messageMapper.updateStatusByServerMsgId(serverMsgId, status, deliveredAt, ackedAt);
        if (updated > 0) {
            logger.info("updateStatusByServerMsgId: serverMsgId={} status={} updatedRows={}", serverMsgId, status, updated);
        } else {
            logger.debug("updateStatusByServerMsgId: serverMsgId={} status={} no rows updated", serverMsgId, status);
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageDO recallMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds) {
        MessageDO exist = messageMapper.findByServerMsgId(serverMsgId);
        if (exist == null) {
            throw new MessageRecallException("INVALID_PARAM", "serverMsgId not found");
        }
        if (operatorUserId == null || !operatorUserId.equals(exist.getFromUserId())) {
            throw new MessageRecallException("FORBIDDEN", "only sender can recall message");
        }
        if (isRetracted(exist)) {
            throw new MessageRecallException("INVALID_PARAM", "message already retracted");
        }
        if (!withinRecallWindow(exist.getCreatedAt(), recallWindowSeconds)) {
            throw new MessageRecallException("FORBIDDEN", "message recall window expired");
        }

        Date now = new Date();
        int updated = messageMapper.updateRetractionByServerMsgId(serverMsgId, STATUS_RETRACTED, now, operatorUserId);
        if (updated <= 0) {
            MessageDO latest = messageMapper.findByServerMsgId(serverMsgId);
            if (isRetracted(latest)) {
                throw new MessageRecallException("INVALID_PARAM", "message already retracted");
            }
            throw new IllegalStateException("recall message failed serverMsgId=" + serverMsgId);
        }
        MessageDO recalled = decodeMessage(messageMapper.findByServerMsgId(serverMsgId));
        appendRecallOutbox(recalled);
        return recalled;
    }

    /**
     * 将状态映射为可比较的序号。
     */
    private int statusOrd(String s) {
        if (s == null) {
            return 0;
        }
        String up = s.toUpperCase();
        return switch (up) {
            case STATUS_SENT -> 1;
            case STATUS_DELIVERED -> 2;
            case STATUS_ACKED -> 3;
            default -> 0;
        };
    }

    /**
     * 根据服务端消息 ID 查询消息实体。
     */
    @Override
    public MessageDO findByServerMsgId(String serverMsgId) {
        if (serverMsgId == null) {
            return null;
        }
        return decodeMessage(messageMapper.findByServerMsgId(serverMsgId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean enqueueStatusNotify(MessageDO message, String status) {
        if (message == null || status == null || outboxMapper == null) {
            return false;
        }
        try {
            DispatchMessagePayload payload = new DispatchMessagePayload();
            payload.setEventId(UUID.randomUUID().toString());
            payload.setEventType(DispatchMessagePayload.EVENT_TYPE_STATUS_NOTIFY);
            payload.setOriginServerId(currentServerId());
            payload.setServerMsgId(message.getServerMsgId());
            payload.setClientMsgId(message.getClientMsgId());
            payload.setFromUserId(message.getFromUserId());
            payload.setToUserId(message.getToUserId());
            payload.setNotifyUserId(message.getFromUserId());
            payload.setStatus(status);
            payload.setCreatedAt(message.getCreatedAt() == null ? null : message.getCreatedAt().toInstant().toString());
            appendSingleDispatchOutbox(message.getId(), payload);
            return true;
        } catch (Exception ex) {
            throw new IllegalStateException("append status notify outbox failed serverMsgId=" + message.getServerMsgId(), ex);
        }
    }

    @Override
    public SyncCursor getSyncCursor(Long toUserId, String deviceId) {
        if (singleCursorMapper == null || toUserId == null || deviceId == null || deviceId.isBlank()) {
            return null;
        }
        return toSyncCursor(singleCursorMapper.findByUserIdAndDeviceId(toUserId, deviceId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void advanceSyncCursor(Long toUserId, String deviceId, SyncCursor syncCursor) {
        if (singleCursorMapper == null || toUserId == null || deviceId == null || deviceId.isBlank()
                || syncCursor == null || !syncCursor.isComplete()) {
            return;
        }
        singleCursorMapper.upsertLastPullCursor(toUserId, deviceId, syncCursor.getCursorCreatedAt(), syncCursor.getCursorId());
    }

    @Override
    public CursorPageResult pullOffline(Long toUserId, String deviceId, SyncCursor syncCursor, int limit) {
        if (syncCursor == null || !syncCursor.isComplete()) {
            return pullRecent(toUserId, deviceId, limit);
        }
        try (Page<MessageDO> ignored = PageHelper.startPage(1, limit + 1, false)) {
            List<MessageDO> fetched = messageMapper.findByToUserIdAfterCursor(toUserId, syncCursor.getCursorCreatedAt(), syncCursor.getCursorId());
            return buildPageResult(fetched, limit, false, syncCursor, deviceId);
        }
    }

    @Override
    public CursorPageResult pullOfflineFromCheckpoint(Long toUserId, String deviceId, int limit) {
        SyncCursor syncCursor = getSyncCursor(toUserId, deviceId);
        if (syncCursor == null || !syncCursor.isComplete()) {
            return pullRecent(toUserId, deviceId, limit);
        }
        return pullOffline(toUserId, deviceId, syncCursor, limit);
    }

    /**
     * 基于游标进行离线消息拉取（升序）。
     */
    @Override
    public CursorPageResult pullOfflineByCursor(Long toUserId, Date cursorCreatedAt, Long cursorId, int limit) {
        return pullOffline(toUserId, null, new SyncCursor(cursorCreatedAt, cursorId), limit);
    }

    /**
     * 拉取最近消息，并转换为客户端期望的升序结果。
     */
    @Override
    public CursorPageResult pullRecent(Long toUserId, int limit) {
        return pullRecent(toUserId, null, limit);
    }

    private CursorPageResult pullRecent(Long toUserId, String deviceId, int limit) {
        try (Page<MessageDO> ignored = PageHelper.startPage(1, limit + 1, false)) {
            List<MessageDO> fetched = messageMapper.findRecentByToUserId(toUserId);
            return buildPageResult(fetched, limit, true, null, deviceId);
        }
    }

    /**
     * 将查询结果组装为统一的游标分页返回结构。
     */
    private CursorPageResult buildPageResult(List<MessageDO> fetched,
                                             int limit,
                                             boolean reverseToAsc,
                                             SyncCursor baseCursor,
                                             String deviceId) {
        boolean hasMore = fetched.size() > limit;
        List<MessageDO> selected = hasMore ? fetched.subList(0, limit) : fetched;

        List<MessageDO> messages = new ArrayList<>(selected.size());
        for (MessageDO item : selected) {
            messages.add(decodeMessage(item));
        }
        if (reverseToAsc) {
            Collections.reverse(messages);
        }

        Date nextCursorCreatedAt = null;
        Long nextCursorId = null;
        if (!messages.isEmpty()) {
            MessageDO last = messages.get(messages.size() - 1);
            nextCursorCreatedAt = last.getCreatedAt();
            nextCursorId = last.getId();
        } else if (baseCursor != null && baseCursor.isComplete()) {
            nextCursorCreatedAt = baseCursor.getCursorCreatedAt();
            nextCursorId = baseCursor.getCursorId();
        } else if (deviceId != null && !deviceId.isBlank()) {
            nextCursorId = 0L;
        }

        return new CursorPageResult(messages, hasMore, nextCursorCreatedAt, nextCursorId);
    }

    private SyncCursor toSyncCursor(com.ming.imchatserver.dao.SingleCursorDO cursor) {
        if (cursor == null || cursor.getLastPullCreatedAt() == null || cursor.getLastPullMessageId() == null) {
            return null;
        }
        return new SyncCursor(cursor.getLastPullCreatedAt(), cursor.getLastPullMessageId());
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

    private void appendRecallOutbox(MessageDO recalled) {
        if (outboxMapper == null || recalled == null) {
            return;
        }
        try {
            DispatchMessagePayload payload = new DispatchMessagePayload();
            payload.setEventId(UUID.randomUUID().toString());
            payload.setEventType(DispatchMessagePayload.EVENT_TYPE_RECALL);
            payload.setOriginServerId(currentServerId());
            payload.setServerMsgId(recalled.getServerMsgId());
            payload.setClientMsgId(recalled.getClientMsgId());
            payload.setFromUserId(recalled.getFromUserId());
            payload.setToUserId(recalled.getToUserId());
            payload.setMsgType(recalled.getMsgType());
            payload.setStatus(STATUS_RETRACTED);
            payload.setCreatedAt(recalled.getCreatedAt() == null ? null : recalled.getCreatedAt().toInstant().toString());
            payload.setRetractedAt(recalled.getRetractedAt() == null ? null : recalled.getRetractedAt().toInstant().toString());
            payload.setRetractedBy(recalled.getRetractedBy());
            appendSingleDispatchOutbox(recalled.getId(), payload);
        } catch (Exception ex) {
            throw new IllegalStateException("append recall outbox failed serverMsgId=" + recalled.getServerMsgId(), ex);
        }
    }

    private void appendSingleDispatchOutbox(Long messageId, DispatchMessagePayload payload) throws Exception {
        OutboxMessageDO outbox = new OutboxMessageDO();
        outbox.setEventId(payload.getEventId());
        outbox.setMessageId(messageId);
        String dispatchTopic = reliabilityProperties == null ? DEFAULT_DISPATCH_TOPIC : reliabilityProperties.getDispatchTopic();
        outbox.setTopic(dispatchTopic);
        outbox.setTag(DispatchMessagePayload.TAG_SINGLE);
        outbox.setPayload(objectMapper.writeValueAsString(payload));
        outbox.setStatus(OUTBOX_STATUS_NEW);
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(new Date());
        outbox.setProcessingAt(null);
        outboxMapper.insert(outbox);
    }

    private String currentServerId() {
        return redisStateProperties == null ? null : redisStateProperties.getServerId();
    }
}
