package com.ming.imchatserver.file;

import lombok.Getter;

/**
 * 文件元数据。
 */
@Getter
public class FileMetadata {
    private final String fileId;
    private final String fileName;
    private final String contentType;
    private final long size;
    private final String url;

    public FileMetadata(String fileId, String fileName, String contentType, long size, String url) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
        this.url = url;
    }

}
