package com.ming.immessageservice.interfaces.http;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.message.AckMessageStatusRequest;
import com.ming.imapicontract.message.AckMessageStatusResponse;
import com.ming.imapicontract.message.AdvanceCursorRequest;
import com.ming.imapicontract.message.MessageApiPaths;
import com.ming.imapicontract.message.PersistSingleMessageRequest;
import com.ming.imapicontract.message.PersistSingleMessageResponse;
import com.ming.imapicontract.message.PullOfflineRequest;
import com.ming.imapicontract.message.PullOfflineResponse;
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

    @PostMapping(MessageApiPaths.PULL_OFFLINE)
    public ApiResponse<PullOfflineResponse> pullOffline(@RequestBody PullOfflineRequest request) {
        return ApiResponse.success(messageCommandApplicationService.pullOffline(request));
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
}
