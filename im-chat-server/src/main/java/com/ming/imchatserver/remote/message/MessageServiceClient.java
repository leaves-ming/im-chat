package com.ming.imchatserver.remote.message;

import com.ming.im.apicontract.common.ApiResponse;
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
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 单聊消息远程客户端。
 */
@FeignClient(
        name = "im-message-service",
        contextId = "messageServiceClient",
        path = MessageApiPaths.BASE
)
public interface MessageServiceClient {

    @PostMapping(MessageApiPaths.SINGLE_PERSIST)
    ApiResponse<PersistSingleMessageResponse> persistSingleMessage(@RequestBody PersistSingleMessageRequest request);

    @PostMapping(MessageApiPaths.ACK)
    ApiResponse<AckMessageStatusResponse> ackMessageStatus(@RequestBody AckMessageStatusRequest request);

    @PostMapping(MessageApiPaths.GROUP_PERSIST)
    ApiResponse<PersistGroupMessageResponse> persistGroupMessage(@RequestBody PersistGroupMessageRequest request);

    @PostMapping(MessageApiPaths.PULL_OFFLINE)
    ApiResponse<PullOfflineResponse> pullOffline(@RequestBody PullOfflineRequest request);

    @PostMapping(MessageApiPaths.GROUP_PULL_OFFLINE)
    ApiResponse<PullGroupOfflineResponse> pullGroupOffline(@RequestBody PullGroupOfflineRequest request);

    @PostMapping(MessageApiPaths.ADVANCE_CURSOR)
    ApiResponse<Void> advanceCursor(@RequestBody AdvanceCursorRequest request);

    @PostMapping(MessageApiPaths.RECALL)
    ApiResponse<RecallSingleMessageResponse> recallSingleMessage(@RequestBody RecallSingleMessageRequest request);

    @PostMapping(MessageApiPaths.GROUP_RECALL)
    ApiResponse<RecallGroupMessageResponse> recallGroupMessage(@RequestBody RecallGroupMessageRequest request);

    @PostMapping(MessageApiPaths.GROUP_GET)
    ApiResponse<GetGroupMessageResponse> getGroupMessage(@RequestBody GetGroupMessageRequest request);

    @PostMapping(MessageApiPaths.FILE_ACCESS_CHECK)
    ApiResponse<CheckFileAccessResponse> checkFileAccess(@RequestBody CheckFileAccessRequest request);
}
