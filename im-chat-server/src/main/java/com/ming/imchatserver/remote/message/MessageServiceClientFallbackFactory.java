package com.ming.imchatserver.remote.message;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.message.AckMessageStatusRequest;
import com.ming.imapicontract.message.AckMessageStatusResponse;
import com.ming.imapicontract.message.AdvanceCursorRequest;
import com.ming.imapicontract.message.PersistSingleMessageRequest;
import com.ming.imapicontract.message.PersistSingleMessageResponse;
import com.ming.imapicontract.message.PullOfflineRequest;
import com.ming.imapicontract.message.PullOfflineResponse;
import com.ming.imapicontract.message.RecallSingleMessageRequest;
import com.ming.imapicontract.message.RecallSingleMessageResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 消息服务远程调用兜底。
 */
@Component
public class MessageServiceClientFallbackFactory implements FallbackFactory<MessageServiceClient> {

    @Override
    public MessageServiceClient create(Throwable cause) {
        String message = cause == null ? "im-message-service unavailable" : cause.getMessage();
        return new MessageServiceClient() {
            @Override
            public ApiResponse<PersistSingleMessageResponse> persistSingleMessage(PersistSingleMessageRequest request) {
                return ApiResponse.failure("REMOTE_UNAVAILABLE", message);
            }

            @Override
            public ApiResponse<AckMessageStatusResponse> ackMessageStatus(AckMessageStatusRequest request) {
                return ApiResponse.failure("REMOTE_UNAVAILABLE", message);
            }

            @Override
            public ApiResponse<PullOfflineResponse> pullOffline(PullOfflineRequest request) {
                return ApiResponse.failure("REMOTE_UNAVAILABLE", message);
            }

            @Override
            public ApiResponse<Void> advanceCursor(AdvanceCursorRequest request) {
                return ApiResponse.failure("REMOTE_UNAVAILABLE", message);
            }

            @Override
            public ApiResponse<RecallSingleMessageResponse> recallSingleMessage(RecallSingleMessageRequest request) {
                return ApiResponse.failure("REMOTE_UNAVAILABLE", message);
            }
        };
    }
}
