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
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.remote.message.MessageServiceClient;
import com.ming.imchatserver.service.MessageRecallException;
import com.ming.imchatserver.service.MessageService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MessageFacade 远程实现。
 */
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
        return new AckReportResult(toMessageDO(response.message()), response.status(), response.updated(), response.ackAt());
    }

    @Override
    public boolean enqueueStatusNotify(MessageDO message, String status) {
        return false;
    }

    @Override
    @SentinelResource("im-message-service:pullOffline")
    public MessageService.CursorPageResult pullOffline(Long userId,
                                                       String deviceId,
                                                       MessageService.SyncCursor syncCursor,
                                                       int limit) {
        SyncCursorDTO syncCursorDTO = syncCursor == null ? null : new SyncCursorDTO(syncCursor.getCursorCreatedAt(), syncCursor.getCursorId());
        PullOfflineResponse response = unwrap(messageServiceClient.pullOffline(
                new PullOfflineRequest(userId, deviceId, syncCursorDTO, limit, false)));
        return toCursorPageResult(response.page());
    }

    @Override
    @SentinelResource("im-message-service:loadInitialSync")
    public MessageService.CursorPageResult loadInitialSync(Long userId, String deviceId, int limit) {
        PullOfflineResponse response = unwrap(messageServiceClient.pullOffline(
                new PullOfflineRequest(userId, deviceId, null, limit, true)));
        return toCursorPageResult(response.page());
    }

    @Override
    @SentinelResource("im-message-service:advanceCursor")
    public void advanceSyncCursor(Long userId, String deviceId, MessageService.CursorPageResult pageResult) {
        if (pageResult == null || pageResult.getNextCursorCreatedAt() == null || pageResult.getNextCursorId() == null) {
            return;
        }
        unwrap(messageServiceClient.advanceCursor(new AdvanceCursorRequest(
                userId,
                deviceId,
                pageResult.getNextCursorCreatedAt(),
                pageResult.getNextCursorId()
        )));
    }

    @Override
    @SentinelResource("im-message-service:recallSingle")
    public MessageDO recallMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds) {
        RecallSingleMessageResponse response = unwrap(messageServiceClient.recallSingleMessage(
                new RecallSingleMessageRequest(operatorUserId, serverMsgId, recallWindowSeconds)));
        return toMessageDO(response.message());
    }

    private MessageService.CursorPageResult toCursorPageResult(CursorPageDTO page) {
        List<MessageDO> messages = new ArrayList<>();
        if (page != null && page.messages() != null) {
            for (MessageDTO message : page.messages()) {
                messages.add(toMessageDO(message));
            }
        }
        return new MessageService.CursorPageResult(
                messages,
                page != null && page.hasMore(),
                page == null ? null : page.nextCursorCreatedAt(),
                page == null ? null : page.nextCursorId()
        );
    }

    private MessageDO toMessageDO(MessageDTO message) {
        if (message == null) {
            return null;
        }
        MessageDO target = new MessageDO();
        target.setId(message.id());
        target.setServerMsgId(message.serverMsgId());
        target.setClientMsgId(message.clientMsgId());
        target.setFromUserId(message.fromUserId());
        target.setToUserId(message.toUserId());
        target.setMsgType(message.msgType());
        target.setContent(message.content());
        target.setStatus(message.status());
        target.setCreatedAt(message.createdAt());
        target.setDeliveredAt(message.deliveredAt());
        target.setAckedAt(message.ackedAt());
        target.setRetractedAt(message.retractedAt());
        target.setRetractedBy(message.retractedBy());
        return target;
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
