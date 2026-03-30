package com.ming.imchatserver.file;

import java.io.InputStream;

/**
 * 文件存储抽象。
 */
public interface FileStorageService {

    void store(String storageKey, InputStream inputStream);

    StoredFileResource load(String storageKey, String fileName, String contentType);
}
