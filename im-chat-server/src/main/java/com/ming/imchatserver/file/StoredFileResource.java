package com.ming.imchatserver.file;

import java.nio.file.Path;

/**
 * 本地文件访问结果。
 */
public class StoredFileResource {
    private final Path path;
    private final String fileName;
    private final String contentType;
    private final long size;

    public StoredFileResource(Path path, String fileName, String contentType, long size) {
        this.path = path;
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
    }

    public Path getPath() {
        return path;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }
}
