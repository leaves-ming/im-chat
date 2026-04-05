package com.ming.imfileservice.service;

import com.ming.imfileservice.dao.FileRecordDO;
import com.ming.imfileservice.file.FileMetadata;
import com.ming.imfileservice.file.StoredFileResource;

import java.io.InputStream;

/**
 * 文件服务。
 */
public interface FileService {

    record DownloadUrlResult(String downloadUrl, long expireAt) {
    }

    FileMetadata store(Long ownerUserId,
                       String originalFileName,
                       String contentType,
                       long size,
                       InputStream inputStream);

    String consumeUploadTokenAndBuildFileMessageContent(String rawIncomingContent, Long senderUserId);

    DownloadUrlResult createDownloadUrl(Long requesterUserId, String fileId);

    StoredFileResource loadBySignedDownloadUrl(String fileId, long expireAt, String signature);

    FileRecordDO findByFileId(String fileId);
}
