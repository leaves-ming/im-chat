package com.ming.immessageservice.remote.file;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.file.ConsumeUploadTokenRequest;
import com.ming.imapicontract.file.ConsumeUploadTokenResponse;
import com.ming.imapicontract.file.FileApiPaths;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 文件服务远程客户端。
 */
@FeignClient(
        name = "im-file-service",
        contextId = "messageFileServiceClient",
        path = FileApiPaths.BASE
)
public interface FileServiceClient {

    @PostMapping(FileApiPaths.INTERNAL_CONSUME_UPLOAD_TOKEN)
    ApiResponse<ConsumeUploadTokenResponse> consumeUploadToken(@RequestBody ConsumeUploadTokenRequest request);
}
