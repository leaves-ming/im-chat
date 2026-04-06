package com.ming.immessageservice.interfaces.http;

import com.ming.imapicontract.common.ApiResponse;
import com.ming.imapicontract.message.AckMessageStatusRequest;
import com.ming.imapicontract.message.AckMessageStatusResponse;
import com.ming.imapicontract.message.AdvanceCursorRequest;
import com.ming.imapicontract.message.CheckFileAccessRequest;
import com.ming.imapicontract.message.CheckFileAccessResponse;
import com.ming.imapicontract.message.GetGroupMessageRequest;
import com.ming.imapicontract.message.GetGroupMessageResponse;
import com.ming.imapicontract.message.MessageApiPaths;
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
import com.ming.immessageservice.application.MessageCommandApplicationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息命令控制器。
 */
@RestController
@RequestMapping(MessageApiPaths.BASE)
public class MessageCommandController {

    private final MessageCommandApplicationService messageCommandApplicationService;

    public MessageCommandController(MessageCommandApplicationService messageCommandApplicationService) {
        this.messageCommandApplicationService = messageCommandApplicationService;
    }

    @PostMapping(MessageApiPaths.SINGLE_PERSIST)
    public ApiResponse<PersistSingleMessageResponse> persistSingleMessage(@RequestBody PersistSingleMessageRequest request) {
        return ApiResponse.success(messageCommandApplicationService.persistSingleMessage(request));
    }

    @PostMapping(MessageApiPaths.ACK)
    public ApiResponse<AckMessageStatusResponse> ackMessageStatus(@RequestBody AckMessageStatusRequest request) {
        return ApiResponse.success(messageCommandApplicationService.ackMessageStatus(request));
    }

    @PostMapping(MessageApiPaths.GROUP_PERSIST)
    public ApiResponse<PersistGroupMessageResponse> persistGroupMessage(@RequestBody PersistGroupMessageRequest request) {
        return ApiResponse.success(messageCommandApplicationService.persistGroupMessage(request));
    }

    @PostMapping(MessageApiPaths.PULL_OFFLINE)
    public ApiResponse<PullOfflineResponse> pullOffline(@RequestBody PullOfflineRequest request) {
        return ApiResponse.success(messageCommandApplicationService.pullOffline(request));
    }

    @PostMapping(MessageApiPaths.GROUP_PULL_OFFLINE)
    public ApiResponse<PullGroupOfflineResponse> pullGroupOffline(@RequestBody PullGroupOfflineRequest request) {
        return ApiResponse.success(messageCommandApplicationService.pullGroupOffline(request));
    }

    @PostMapping(MessageApiPaths.ADVANCE_CURSOR)
    public ApiResponse<Void> advanceCursor(@RequestBody AdvanceCursorRequest request) {
        messageCommandApplicationService.advanceCursor(request);
        return ApiResponse.success(null);
    }

    @PostMapping(MessageApiPaths.RECALL)
    public ApiResponse<RecallSingleMessageResponse> recallSingleMessage(@RequestBody RecallSingleMessageRequest request) {
        return ApiResponse.success(messageCommandApplicationService.recallSingleMessage(request));
    }

    @PostMapping(MessageApiPaths.GROUP_RECALL)
    public ApiResponse<RecallGroupMessageResponse> recallGroupMessage(@RequestBody RecallGroupMessageRequest request) {
        return ApiResponse.success(messageCommandApplicationService.recallGroupMessage(request));
    }

    @PostMapping(MessageApiPaths.GROUP_GET)
    public ApiResponse<GetGroupMessageResponse> getGroupMessage(@RequestBody GetGroupMessageRequest request) {
        return ApiResponse.success(messageCommandApplicationService.getGroupMessage(request));
    }

    @PostMapping(MessageApiPaths.FILE_ACCESS_CHECK)
    public ApiResponse<CheckFileAccessResponse> checkFileAccess(@RequestBody CheckFileAccessRequest request) {
        return ApiResponse.success(messageCommandApplicationService.checkFileAccess(request));
    }
}
