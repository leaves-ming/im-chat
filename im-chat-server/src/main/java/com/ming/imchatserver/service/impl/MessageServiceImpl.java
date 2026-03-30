package com.ming.imchatserver.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.config.ReliabilityProperties;
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.dao.OutboxMessageDO;
import com.ming.imchatserver.mapper.MessageMapper;
import com.ming.imchatserver.mapper.OutboxMapper;
import com.ming.imchatserver.message.MessageContentCodec;
import com.ming.imchatserver.mq.DispatchMessagePayload;
import com.ming.imchatserver.sensitive.SensitiveWordFilterResult;
import com.ming.imchatserver.sensitive.SensitiveWordHitException;
import com.ming.imchatserver.sensitive.SensitiveWordService;
import com.ming.imchatserver.service.FileService;
import com.ming.imchatserver.service.MessageService;
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
    private final MessageMapper messageMapper;
    private final OutboxMapper outboxMapper;
    private final ReliabilityProperties reliabilityProperties;
    private final SensitiveWordService sensitiveWordService;
    private final FileService fileService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param messageMapper 消息表数据访问对象
     */
    @Autowired
    public MessageServiceImpl(MessageMapper messageMapper,
                              OutboxMapper outboxMapper,
                              ReliabilityProperties reliabilityProperties,
                              SensitiveWordService sensitiveWordService,
                              FileService fileService) {
        this.messageMapper = messageMapper;
        this.outboxMapper = outboxMapper;
        this.reliabilityProperties = reliabilityProperties;
        this.sensitiveWordService = sensitiveWordService;
        this.fileService = fileService;
    }

    /**
     * 单元测试兼容构造函数。
     */
    public MessageServiceImpl(MessageMapper messageMapper) {
        this(messageMapper, null, null, null, null);
    }

    /**
     * 单元测试兼容构造函数（含 outbox）。
     */
    public MessageServiceImpl(MessageMapper messageMapper, OutboxMapper outboxMapper) {
        this(messageMapper, outboxMapper, null, null, null);
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
        msg.setStatus(msg.getStatus() == null ? "SENT" : msg.getStatus());
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
            payload.setServerMsgId(msg.getServerMsgId());
            payload.setClientMsgId(msg.getClientMsgId());
            payload.setFromUserId(msg.getFromUserId());
            payload.setToUserId(msg.getToUserId());
            payload.setContent(logicalContent);
            payload.setMsgType(msg.getMsgType());

            OutboxMessageDO outbox = new OutboxMessageDO();
            outbox.setEventId(payload.getEventId());
            outbox.setMessageId(msg.getId());
            String dispatchTopic = reliabilityProperties == null ? DEFAULT_DISPATCH_TOPIC : reliabilityProperties.getDispatchTopic();
            outbox.setTopic(dispatchTopic);
            outbox.setTag("SINGLE");
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setStatus(OUTBOX_STATUS_NEW);
            outbox.setRetryCount(0);
            outbox.setNextRetryAt(new Date());
            outbox.setProcessingAt(null);

            outboxMapper.insert(outbox);
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

        int targetOrd = statusOrd(status);
        if (targetOrd == 0) {
            return 0;
        }

        MessageDO exist = messageMapper.findByServerMsgId(serverMsgId);
        if (exist == null) {
            logger.warn("updateStatusByServerMsgId: serverMsgId={} not found", serverMsgId);
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

    /**
     * 将状态映射为可比较的序号。
     */
    private int statusOrd(String s) {
        if (s == null) {
            return 0;
        }
        String up = s.toUpperCase();
        return switch (up) {
            case "SENT" -> 1;
            case "DELIVERED" -> 2;
            case "ACKED" -> 3;
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

    /**
     * 基于游标进行离线消息拉取（升序）。
     */
    @Override
    public CursorPageResult pullOfflineByCursor(Long toUserId, Date cursorCreatedAt, Long cursorId, int limit) {
        try (Page<MessageDO> ignored = PageHelper.startPage(1, limit + 1, false)) {
            List<MessageDO> fetched = messageMapper.findByToUserIdAfterCursor(toUserId, cursorCreatedAt, cursorId);
            return buildPageResult(fetched, limit, false);
        }
    }

    /**
     * 拉取最近消息，并转换为客户端期望的升序结果。
     */
    @Override
    public CursorPageResult pullRecent(Long toUserId, int limit) {
        try (Page<MessageDO> ignored = PageHelper.startPage(1, limit + 1, false)) {
            List<MessageDO> fetched = messageMapper.findRecentByToUserId(toUserId);
            return buildPageResult(fetched, limit, true);
        }
    }

    /**
     * 将查询结果组装为统一的游标分页返回结构。
     */
    private CursorPageResult buildPageResult(List<MessageDO> fetched, int limit, boolean reverseToAsc) {
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
        }

        return new CursorPageResult(messages, hasMore, nextCursorCreatedAt, nextCursorId);
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
        return decoded;
    }
}
