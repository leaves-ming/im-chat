package com.ming.imfileservice.remote.message;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.message.CheckFileAccessRequest;
import com.ming.imapicontract.message.CheckFileAccessResponse;
import com.ming.imapicontract.message.MessageApiPaths;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 消息服务远程客户端。
 */
@FeignClient(
        name = "im-message-service",
        contextId = "fileMessageServiceClient",
        path = MessageApiPaths.BASE
)
public interface MessageServiceClient {

    @PostMapping(MessageApiPaths.FILE_ACCESS_CHECK)
    ApiResponse<CheckFileAccessResponse> checkFileAccess(@RequestBody CheckFileAccessRequest request);
}
