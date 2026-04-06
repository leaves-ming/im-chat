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
import com.ming.immessageservice.remote.file.FileServiceClient;
import com.ming.immessageservice.sensitive.SensitiveWordService;
import org.springframework.stereotype.Service;

/**
 * 消息命令应用服务。
 */
@Service
public class MessageCommandApplicationService {

    private final SingleMessageDomainService singleMessageDomainService;
    private final GroupMessageDomainService groupMessageDomainService;
    private final MessageServiceProperties messageServiceProperties;
    private final FileServiceClient fileServiceClient;
    private final SensitiveWordService sensitiveWordService;

    public MessageCommandApplicationService(SingleMessageDomainService singleMessageDomainService,
                                            GroupMessageDomainService groupMessageDomainService,
                                            MessageServiceProperties messageServiceProperties,
                                            FileServiceClient fileServiceClient,
                                            SensitiveWordService sensitiveWordService) {
        this.singleMessageDomainService = singleMessageDomainService;
        this.groupMessageDomainService = groupMessageDomainService;
        this.messageServiceProperties = messageServiceProperties;
        this.fileServiceClient = fileServiceClient;
        this.sensitiveWordService = sensitiveWordService;
    }

    public PersistSingleMessageResponse persistSingleMessage(PersistSingleMessageRequest request) {
        String content = normalizeContent(request.msgType(), request.content(), request.fromUserId());
        content = sensitiveWordService.filterText(content);
        SingleMessageDomainService.PersistResult result = singleMessageDomainService.persistSingleMessage(
                request.fromUserId(),
                request.targetUserId(),
                request.clientMsgId(),
                request.msgType(),
                content);
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
        String content = normalizeContent(request.msgType(), request.content(), request.fromUserId());
        content = sensitiveWordService.filterText(content);
        return new PersistGroupMessageResponse(groupMessageDomainService.persistMessage(
                request.groupId(), request.fromUserId(), request.clientMsgId(), request.msgType(), content));
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

    private String normalizeContent(String msgType, String content, Long senderUserId) {
        if (!"FILE".equalsIgnoreCase(msgType)) {
            return content;
        }
        var response = fileServiceClient.consumeUploadToken(new com.ming.imapicontract.file.ConsumeUploadTokenRequest(content, senderUserId));
        if (response == null) {
            throw new IllegalStateException("file service response is null");
        }
        if (response.isSuccess() && response.getData() != null) {
            return response.getData().canonicalContent();
        }
        String code = response.getCode();
        String message = response.getMessage();
        if ("FORBIDDEN".equals(code)) {
            throw new SecurityException(message);
        }
        if ("TOKEN_ALREADY_BOUND".equals(code) || "INVALID_PARAM".equals(code)) {
            throw new IllegalArgumentException(message);
        }
        throw new IllegalStateException(message == null ? "file service unavailable" : message);
    }
}
