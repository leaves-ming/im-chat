package com.ming.imchatserver.application.facade.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.message.AckMessageStatusRequest;
import com.ming.imapicontract.message.AckMessageStatusResponse;
import com.ming.imapicontract.message.AdvanceCursorRequest;
import com.ming.imapicontract.message.CursorPageDTO;
import com.ming.imapicontract.message.MessageDTO;
import com.ming.imapicontract.message.PersistSingleMessageRequest;
import com.ming.imapicontract.message.PersistSingleMessageResponse;
import com.ming.imapicontract.message.PullOfflineRequest;
import com.ming.imapicontract.message.PullOfflineResponse;
import com.ming.imapicontract.message.RecallSingleMessageRequest;
import com.ming.imapicontract.message.RecallSingleMessageResponse;
import com.ming.imapicontract.message.SyncCursorDTO;
import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.application.model.SingleMessagePage;
import com.ming.imchatserver.application.model.SingleMessageView;
import com.ming.imchatserver.application.model.SingleSyncCursor;
import com.ming.imchatserver.remote.message.MessageServiceClient;
import com.ming.imchatserver.service.MessageRecallException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MessageFacade 远程实现。
 */
@Primary
@Component
public class RemoteMessageFacade implements MessageFacade {

    private final MessageServiceClient messageServiceClient;

    public RemoteMessageFacade(MessageServiceClient messageServiceClient) {
        this.messageServiceClient = messageServiceClient;
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
        return new AckReportResult(toSingleMessageView(response.message()), response.status(), response.updated(), response.ackAt());
    }

    @Override
    public boolean enqueueStatusNotify(SingleMessageView message, String status) {
        return true;
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
}
