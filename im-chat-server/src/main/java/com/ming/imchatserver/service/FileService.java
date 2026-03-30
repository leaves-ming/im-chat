package com.ming.imchatserver.service;

import com.ming.imchatserver.dao.FileRecordDO;
import com.ming.imchatserver.file.FileMetadata;
import com.ming.imchatserver.file.StoredFileResource;

import java.io.InputStream;

/**
 * 文件服务：元数据管理、FILE 消息规范化与下载授权。
 */
public interface FileService {

    FileMetadata store(Long ownerUserId,
                       String originalFileName,
                       String contentType,
                       long size,
                       InputStream inputStream);

    String consumeUploadTokenAndBuildFileMessageContent(String rawIncomingContent, Long senderUserId);

    StoredFileResource loadAuthorizedFile(Long requesterUserId, String fileId);

    FileRecordDO findByFileId(String fileId);
}
