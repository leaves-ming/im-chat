package com.ming.immessageservice.application;

import com.ming.imapicontract.message.AckMessageStatusRequest;
import com.ming.imapicontract.message.AckMessageStatusResponse;
import com.ming.imapicontract.message.AdvanceCursorRequest;
import com.ming.imapicontract.message.CheckFileAccessRequest;
import com.ming.imapicontract.message.CheckFileAccessResponse;
import com.ming.imapicontract.message.GetGroupMessageRequest;
import com.ming.imapicontract.message.GetGroupMessageResponse;
import com.ming.imapicontract.message.PersistSingleMessageRequest;
import com.ming.imapicontract.message.PersistGroupMessageRequest;
import com.ming.imapicontract.message.PersistGroupMessageResponse;
import com.ming.imapicontract.message.PersistSingleMessageResponse;
import com.ming.imapicontract.message.PullGroupOfflineRequest;
import com.ming.imapicontract.message.PullGroupOfflineResponse;
import com.ming.imapicontract.message.PullOfflineRequest;
import com.ming.imapicontract.message.PullOfflineResponse;
import com.ming.imapicontract.message.RecallGroupMessageRequest;
import com.ming.imapicontract.message.RecallGroupMessageResponse;
import com.ming.imapicontract.message.RecallSingleMessageRequest;
import com.ming.imapicontract.message.RecallSingleMessageResponse;
import com.ming.immessageservice.config.MessageServiceProperties;
import com.ming.immessageservice.domain.service.GroupMessageDomainService;
import com.ming.immessageservice.domain.service.SingleMessageDomainService;
import org.springframework.stereotype.Service;

/**
 * 消息命令应用服务。
 */
@Service
public class MessageCommandApplicationService {

    private final SingleMessageDomainService singleMessageDomainService;
    private final GroupMessageDomainService groupMessageDomainService;
    private final MessageServiceProperties messageServiceProperties;

    public MessageCommandApplicationService(SingleMessageDomainService singleMessageDomainService,
                                            GroupMessageDomainService groupMessageDomainService,
                                            MessageServiceProperties messageServiceProperties) {
        this.singleMessageDomainService = singleMessageDomainService;
        this.groupMessageDomainService = groupMessageDomainService;
        this.messageServiceProperties = messageServiceProperties;
    }

    public PersistSingleMessageResponse persistSingleMessage(PersistSingleMessageRequest request) {
        SingleMessageDomainService.PersistResult result = singleMessageDomainService.persistSingleMessage(
                request.fromUserId(),
                request.targetUserId(),
                request.clientMsgId(),
                request.msgType(),
                request.content());
        return new PersistSingleMessageResponse(request.clientMsgId(), result.serverMsgId(), result.createdNew());
    }

    public AckMessageStatusResponse ackMessageStatus(AckMessageStatusRequest request) {
        SingleMessageDomainService.AckResult result = singleMessageDomainService.reportAck(
                request.reporterUserId(),
                request.serverMsgId(),
                request.targetStatus());
        return new AckMessageStatusResponse(result.message(), result.status(), result.updated(), result.ackAt());
    }

    public PersistGroupMessageResponse persistGroupMessage(PersistGroupMessageRequest request) {
        return new PersistGroupMessageResponse(groupMessageDomainService.persistMessage(
                request.groupId(), request.fromUserId(), request.clientMsgId(), request.msgType(), request.content()));
    }

    public PullOfflineResponse pullOffline(PullOfflineRequest request) {
        int requestedLimit = request.limit() > 0 ? request.limit() : messageServiceProperties.getOfflinePullMaxLimit();
        int limit = Math.min(requestedLimit, messageServiceProperties.getOfflinePullMaxLimit());
        return new PullOfflineResponse(singleMessageDomainService.pullOffline(
                request.userId(),
                request.deviceId(),
                request.syncCursor(),
                limit,
                request.useCheckpoint()));
    }

    public PullGroupOfflineResponse pullGroupOffline(PullGroupOfflineRequest request) {
        int requestedLimit = request.limit() > 0 ? request.limit() : messageServiceProperties.getOfflinePullMaxLimit();
        int limit = Math.min(requestedLimit, messageServiceProperties.getOfflinePullMaxLimit());
        return groupMessageDomainService.pullOffline(request.groupId(), request.userId(), request.cursorSeq(), limit);
    }

    public void advanceCursor(AdvanceCursorRequest request) {
        singleMessageDomainService.advanceCursor(
                request.userId(),
                request.deviceId(),
                request.cursorCreatedAt(),
                request.cursorId());
    }

    public RecallSingleMessageResponse recallSingleMessage(RecallSingleMessageRequest request) {
        return new RecallSingleMessageResponse(singleMessageDomainService.recallSingleMessage(
                request.operatorUserId(),
                request.serverMsgId(),
                request.recallWindowSeconds()));
    }

    public RecallGroupMessageResponse recallGroupMessage(RecallGroupMessageRequest request) {
        return new RecallGroupMessageResponse(groupMessageDomainService.recallMessage(
                request.operatorUserId(), request.serverMsgId(), request.recallWindowSeconds()));
    }

    public GetGroupMessageResponse getGroupMessage(GetGroupMessageRequest request) {
        return new GetGroupMessageResponse(groupMessageDomainService.getByServerMsgId(request.serverMsgId()));
    }

    public CheckFileAccessResponse checkFileAccess(CheckFileAccessRequest request) {
        boolean allowed = singleMessageDomainService.hasFileAccess(request.fileId(), request.userId())
                || groupMessageDomainService.hasFileAccess(request.fileId(), request.userId());
        return new CheckFileAccessResponse(allowed);
    }
}
