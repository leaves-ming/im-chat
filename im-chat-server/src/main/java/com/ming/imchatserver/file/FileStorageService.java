package com.ming.imchatserver.file;

import java.io.InputStream;

/**
 * 文件存储抽象。
 * @author ming
 */
public interface FileStorageService {

    /**
     * 存储文件内容到指定存储键。
     *
     * @param storageKey 存储键
     * @param inputStream 文件输入流
     */
    void store(String storageKey, InputStream inputStream);

    /**
     * 按存储键加载文件资源。
     *
     * @param storageKey 存储键
     * @param fileName 文件名
     * @param contentType 文件内容类型
     * @return 文件资源
     */
    StoredFileResource load(String storageKey, String fileName, String contentType);
}
