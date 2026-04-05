package com.ming.imfileservice.interfaces.http;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.file.ConsumeUploadTokenRequest;
import com.ming.imapicontract.file.ConsumeUploadTokenResponse;
import com.ming.imapicontract.file.CreateDownloadUrlRequest;
import com.ming.imapicontract.file.CreateDownloadUrlResponse;
import com.ming.imapicontract.file.FileApiPaths;
import com.ming.imapicontract.file.FileRecordDTO;
import com.ming.imapicontract.file.GetFileMetadataRequest;
import com.ming.imapicontract.file.StoreFileRequest;
import com.ming.imapicontract.file.StoreFileResponse;
import com.ming.imfileservice.dao.FileRecordDO;
import com.ming.imfileservice.file.FileMetadata;
import com.ming.imfileservice.service.FileService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;

/**
 * 文件服务控制器。
 */
@RestController
@RequestMapping(FileApiPaths.BASE)
public class FileCommandController {

    private final FileService fileService;

    public FileCommandController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(FileApiPaths.INTERNAL_UPLOAD)
    public ApiResponse<StoreFileResponse> upload(@RequestBody StoreFileRequest request) {
        FileMetadata metadata = fileService.store(
                request.ownerUserId(),
                request.fileName(),
                request.contentType(),
                request.size(),
                new ByteArrayInputStream(request.bytes() == null ? new byte[0] : request.bytes()));
        return ApiResponse.success(new StoreFileResponse(
                metadata.getUploadToken(),
                metadata.getFileId(),
                metadata.getFileName(),
                metadata.getContentType(),
                metadata.getSize(),
                metadata.getUrl()));
    }

    @PostMapping(FileApiPaths.INTERNAL_CONSUME_UPLOAD_TOKEN)
    public ApiResponse<ConsumeUploadTokenResponse> consumeUploadToken(@RequestBody ConsumeUploadTokenRequest request) {
        return ApiResponse.success(new ConsumeUploadTokenResponse(
                fileService.consumeUploadTokenAndBuildFileMessageContent(request.rawIncomingContent(), request.senderUserId())));
    }

    @PostMapping(FileApiPaths.INTERNAL_DOWNLOAD_URL)
    public ApiResponse<CreateDownloadUrlResponse> createDownloadUrl(@RequestBody CreateDownloadUrlRequest request) {
        FileService.DownloadUrlResult result = fileService.createDownloadUrl(request.requesterUserId(), request.fileId());
        return ApiResponse.success(new CreateDownloadUrlResponse(result.downloadUrl(), result.expireAt()));
    }

    @PostMapping(FileApiPaths.INTERNAL_METADATA_GET)
    public ApiResponse<FileRecordDTO> getMetadata(@RequestBody GetFileMetadataRequest request) {
        FileRecordDO record = fileService.findByFileId(request.fileId());
        if (record == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(new FileRecordDTO(
                record.getId(),
                record.getFileId(),
                record.getOwnerUserId(),
                record.getContentType(),
                record.getSize(),
                record.getStorageKey(),
                record.getOriginalFileName(),
                record.getCreatedAt()));
    }

}
