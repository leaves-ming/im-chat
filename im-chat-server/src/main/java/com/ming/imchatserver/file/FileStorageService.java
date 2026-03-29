package com.ming.imchatserver.file;

/**
 * 文件存储抽象。
 */
public interface FileStorageService {

    FileMetadata store(String fileName, String contentType, byte[] bytes);

    StoredFileResource load(String fileId, String fileName);
}
