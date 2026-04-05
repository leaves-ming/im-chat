package com.ming.imfileservice.file;

import java.nio.file.Path;

/**
 * 本地文件访问结果。
 */
public record StoredFileResource(Path path, String fileName, String contentType, long size) {
}
