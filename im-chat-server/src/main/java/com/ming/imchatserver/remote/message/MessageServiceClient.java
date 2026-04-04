package com.ming.imchatserver.remote.message;

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
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 单聊消息远程客户端。
 */
@FeignClient(
        name = "im-message-service",
        contextId = "messageServiceClient",
        path = MessageApiPaths.BASE,
        fallbackFactory = MessageServiceClientFallbackFactory.class
)
public interface MessageServiceClient {

    @PostMapping(MessageApiPaths.SINGLE_PERSIST)
    ApiResponse<PersistSingleMessageResponse> persistSingleMessage(@RequestBody PersistSingleMessageRequest request);

    @PostMapping(MessageApiPaths.ACK)
    ApiResponse<AckMessageStatusResponse> ackMessageStatus(@RequestBody AckMessageStatusRequest request);

    @PostMapping(MessageApiPaths.PULL_OFFLINE)
    ApiResponse<PullOfflineResponse> pullOffline(@RequestBody PullOfflineRequest request);

    @PostMapping(MessageApiPaths.ADVANCE_CURSOR)
    ApiResponse<Void> advanceCursor(@RequestBody AdvanceCursorRequest request);

    @PostMapping(MessageApiPaths.RECALL)
    ApiResponse<RecallSingleMessageResponse> recallSingleMessage(@RequestBody RecallSingleMessageRequest request);
}
