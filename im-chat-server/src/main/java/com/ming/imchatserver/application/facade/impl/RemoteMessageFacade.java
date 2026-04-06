package com.ming.imchatserver.application.facade.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.ming.imapicontract.common.ApiResponse;
import com.ming.imapicontract.message.AckMessageStatusRequest;
import com.ming.imapicontract.message.AckMessageStatusResponse;
import com.ming.imapicontract.message.AdvanceCursorRequest;
import com.ming.imapicontract.message.CursorPageDTO;
import com.ming.imapicontract.message.GetGroupMessageRequest;
import com.ming.imapicontract.message.GroupMessageDTO;
import com.ming.imapicontract.message.MessageDTO;
import com.ming.imapicontract.message.PersistGroupMessageRequest;
import com.ming.imapicontract.message.PersistGroupMessageResponse;
import com.ming.imapicontract.message.PersistSingleMessageRequest;
import com.ming.imapicontract.message.PersistSingleMessageResponse;
import com.ming.imapicontract.message.PullGroupOfflineRequest;
import com.ming.imapicontract.message.PullGroupOfflineResponse;
import com.ming.imapicontract.message.PullOfflineRequest;
import com.ming.imapicontract.message.PullOfflineResponse;
import com.ming.imapicontract.message.RecallGroupMessageRequest;
import com.ming.imapicontract.message.RecallGroupMessageResponse;
import com.ming.imapicontract.message.RecallSingleMessageRequest;
import com.ming.imapicontract.message.RecallSingleMessageResponse;
import com.ming.imapicontract.message.SyncCursorDTO;
import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.application.model.GroupMessagePage;
import com.ming.imchatserver.application.model.GroupMessagePersistResult;
import com.ming.imchatserver.application.model.GroupMessageView;
import com.ming.imchatserver.application.model.SingleMessagePage;
import com.ming.imchatserver.application.model.SingleMessageView;
import com.ming.imchatserver.application.model.SingleSyncCursor;
import com.ming.imchatserver.config.RateLimitProperties;
import com.ming.imchatserver.config.RedisStateProperties;
import com.ming.imchatserver.remote.message.MessageServiceClient;
import com.ming.imchatserver.service.IdempotencyService;
import com.ming.imchatserver.service.MessageRecallException;
import com.ming.imchatserver.service.RateLimitService;
import com.ming.imchatserver.service.remote.RemoteGroupService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * MessageFacade 远程实现。
 */
@Primary
@Component
public class RemoteMessageFacade implements MessageFacade {

    private final MessageServiceClient messageServiceClient;
    private final RemoteGroupService groupService;
    private final IdempotencyService idempotencyService;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final RedisStateProperties redisStateProperties;

    public RemoteMessageFacade(MessageServiceClient messageServiceClient,
                               RemoteGroupService groupService,
                               IdempotencyService idempotencyService,
                               RateLimitService rateLimitService,
                               RateLimitProperties rateLimitProperties,
                               RedisStateProperties redisStateProperties) {
        this.messageServiceClient = messageServiceClient;
        this.groupService = groupService;
        this.idempotencyService = idempotencyService;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.redisStateProperties = redisStateProperties;
    }

    @Override
    @SentinelResource("im-message-service:persistSingle")
    public ChatPersistResult sendChat(Long fromUserId, Long targetUserId, String clientMsgId, String msgType, String content) {
        PersistSingleMessageResponse response = unwrap(messageServiceClient.persistSingleMessage(
                new PersistSingleMessageRequest(fromUserId, targetUserId, clientMsgId, msgType, content)));
        return new ChatPersistResult(response.clientMsgId(), response.serverMsgId(), response.createdNew());
    }

    @Override
    @SentinelResource("im-message-service:ackMessage")
    public AckReportResult reportAck(Long reporterUserId, String serverMsgId, String targetStatus) {
        AckMessageStatusResponse response = unwrap(messageServiceClient.ackMessageStatus(
                new AckMessageStatusRequest(reporterUserId, serverMsgId, targetStatus)));
        SingleMessageView message = toSingleMessageView(response.message());
        return new AckReportResult(
                message,
                response.status(),
                response.updated(),
                response.ackAt(),
                response.statusNotifyAppended());
    }

    @Override
    @SentinelResource("im-message-service:pullOffline")
    public SingleMessagePage pullOffline(Long userId, String deviceId, SingleSyncCursor syncCursor, int limit) {
        SyncCursorDTO syncCursorDTO = syncCursor == null ? null : new SyncCursorDTO(syncCursor.cursorCreatedAt(), syncCursor.cursorId());
        PullOfflineResponse response = unwrap(messageServiceClient.pullOffline(
                new PullOfflineRequest(userId, deviceId, syncCursorDTO, limit, false)));
        return toCursorPageResult(response.page());
    }

    @Override
    @SentinelResource("im-message-service:loadInitialSync")
    public SingleMessagePage loadInitialSync(Long userId, String deviceId, int limit) {
        PullOfflineResponse response = unwrap(messageServiceClient.pullOffline(
                new PullOfflineRequest(userId, deviceId, null, limit, true)));
        return toCursorPageResult(response.page());
    }

    @Override
    @SentinelResource("im-message-service:advanceCursor")
    public void advanceSyncCursor(Long userId, String deviceId, SingleMessagePage pageResult) {
        if (pageResult == null || pageResult.nextCursorCreatedAt() == null || pageResult.nextCursorId() == null) {
            return;
        }
        unwrap(messageServiceClient.advanceCursor(new AdvanceCursorRequest(
                userId,
                deviceId,
                pageResult.nextCursorCreatedAt(),
                pageResult.nextCursorId()
        )));
    }

    @Override
    @SentinelResource("im-message-service:recallSingle")
    public SingleMessageView recallMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds) {
        RecallSingleMessageResponse response = unwrap(messageServiceClient.recallSingleMessage(
                new RecallSingleMessageRequest(operatorUserId, serverMsgId, recallWindowSeconds)));
        return toSingleMessageView(response.message());
    }

    @Override
    @SentinelResource("im-message-service:persistGroup")
    public GroupMessagePersistResult sendGroupChat(Long groupId,
                                                   Long fromUserId,
                                                   String clientMsgId,
                                                   String msgType,
                                                   String content) {
        if (!groupService.isActiveMember(groupId, fromUserId)) {
            throw new SecurityException("sender is not active group member");
        }
        if (!consumeMessageRateLimit(fromUserId)) {
            throw new IllegalArgumentException("RATE_LIMITED:message send rate exceeded");
        }
        if (!claimClientMessageId(fromUserId, clientMsgId)) {
            throw new IllegalArgumentException("DUPLICATE_REQUEST:clientMsgId replay detected");
        }
        try {
            PersistGroupMessageResponse response = unwrap(messageServiceClient.persistGroupMessage(
                    new PersistGroupMessageRequest(groupId, fromUserId, clientMsgId, msgType, content)));
            return new GroupMessagePersistResult(toGroupMessageView(response.message()));
        } catch (RuntimeException ex) {
            releaseClientMessageId(fromUserId, clientMsgId);
            throw ex;
        }
    }

    @Override
    @SentinelResource("im-message-service:pullGroupOffline")
    public GroupMessagePage pullGroupOffline(Long groupId, Long userId, Long cursorSeq, int limit) {
        if (!groupService.isActiveMember(groupId, userId)) {
            throw new SecurityException("user is not active group member");
        }
        PullGroupOfflineResponse response = unwrap(messageServiceClient.pullGroupOffline(
                new PullGroupOfflineRequest(groupId, userId, cursorSeq, limit)));
        List<GroupMessageView> messages = new ArrayList<>();
        if (response.messages() != null) {
            for (GroupMessageDTO item : response.messages()) {
                messages.add(toGroupMessageView(item));
            }
        }
        return new GroupMessagePage(messages, response.hasMore(), response.nextCursorSeq());
    }

    @Override
    @SentinelResource("im-message-service:recallGroup")
    public GroupMessageView recallGroupMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds) {
        GroupMessageView existing = toGroupMessageView(unwrap(
                messageServiceClient.getGroupMessage(new GetGroupMessageRequest(serverMsgId))).message());
        if (existing == null) {
            throw new IllegalArgumentException("serverMsgId not found");
        }
        RecallGroupMessageResponse response = unwrap(messageServiceClient.recallGroupMessage(
                new RecallGroupMessageRequest(operatorUserId, serverMsgId, recallWindowSeconds)));
        return toGroupMessageView(response.message());
    }

    private SingleMessagePage toCursorPageResult(CursorPageDTO page) {
        List<SingleMessageView> messages = new ArrayList<>();
        if (page != null && page.messages() != null) {
            for (MessageDTO message : page.messages()) {
                messages.add(toSingleMessageView(message));
            }
        }
        return new SingleMessagePage(
                messages,
                page != null && page.hasMore(),
                page == null ? null : page.nextCursorCreatedAt(),
                page == null ? null : page.nextCursorId()
        );
    }

    private SingleMessageView toSingleMessageView(MessageDTO message) {
        if (message == null) {
            return null;
        }
        return new SingleMessageView(
                message.id(),
                message.serverMsgId(),
                message.clientMsgId(),
                message.fromUserId(),
                message.toUserId(),
                message.msgType(),
                message.content(),
                message.status(),
                message.createdAt(),
                message.deliveredAt(),
                message.ackedAt(),
                message.retractedAt(),
                message.retractedBy()
        );
    }

    private GroupMessageView toGroupMessageView(GroupMessageDTO message) {
        if (message == null) {
            return null;
        }
        return new GroupMessageView(
                message.id(),
                message.groupId(),
                message.seq(),
                message.serverMsgId(),
                message.clientMsgId(),
                message.fromUserId(),
                message.msgType(),
                message.content(),
                message.status(),
                message.createdAt(),
                message.retractedAt(),
                message.retractedBy()
        );
    }

    private <T> T unwrap(ApiResponse<T> response) {
        if (response == null) {
            throw new IllegalStateException("message service response is null");
        }
        if (response.isSuccess()) {
            return response.getData();
        }
        throw toException(response.getCode(), response.getMessage());
    }

    private RuntimeException toException(String code, String message) {
        if ("FORBIDDEN".equals(code)) {
            return new SecurityException(message);
        }
        if ("INVALID_PARAM".equals(code)) {
            return new IllegalArgumentException(message);
        }
        if ("REMOTE_UNAVAILABLE".equals(code)) {
            return new IllegalStateException(message);
        }
        return new MessageRecallException(code == null ? "REMOTE_ERROR" : code, message);
    }

    private boolean consumeMessageRateLimit(Long userId) {
        if (rateLimitService == null || rateLimitProperties == null || userId == null) {
            return true;
        }
        return rateLimitService.checkAndIncrement(
                "message_send",
                "userId",
                String.valueOf(userId),
                rateLimitProperties.getMessageSend().getLimit(),
                rateLimitProperties.getMessageSend().getWindowSeconds()).allowed();
    }

    private boolean claimClientMessageId(Long userId, String clientMsgId) {
        if (idempotencyService == null || redisStateProperties == null || clientMsgId == null || clientMsgId.isBlank() || userId == null) {
            return true;
        }
        return idempotencyService.claimClientMessage(
                userId,
                clientMsgId,
                Duration.ofSeconds(redisStateProperties.getClientMsgIdTtlSeconds()));
    }

    private void releaseClientMessageId(Long userId, String clientMsgId) {
        if (idempotencyService == null || clientMsgId == null || clientMsgId.isBlank() || userId == null) {
            return;
        }
        idempotencyService.releaseClientMessage(userId, clientMsgId);
    }
}
