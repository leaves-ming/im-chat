package com.ming.imchatserver.remote.file;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.file.CreateDownloadUrlRequest;
import com.ming.imapicontract.file.CreateDownloadUrlResponse;
import com.ming.imapicontract.file.FileApiPaths;
import com.ming.imapicontract.file.FileRecordDTO;
import com.ming.imapicontract.file.GetFileMetadataRequest;
import com.ming.imapicontract.file.StoreFileRequest;
import com.ming.imapicontract.file.StoreFileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 文件服务远程客户端。
 */
@FeignClient(
        name = "im-file-service",
        contextId = "chatFileServiceClient",
        path = FileApiPaths.BASE
)
public interface FileServiceClient {

    @PostMapping(FileApiPaths.INTERNAL_UPLOAD)
    ApiResponse<StoreFileResponse> upload(@RequestBody StoreFileRequest request);

    @PostMapping(FileApiPaths.INTERNAL_DOWNLOAD_URL)
    ApiResponse<CreateDownloadUrlResponse> createDownloadUrl(@RequestBody CreateDownloadUrlRequest request);

    @PostMapping(FileApiPaths.INTERNAL_METADATA_GET)
    ApiResponse<FileRecordDTO> getMetadata(@RequestBody GetFileMetadataRequest request);
}
