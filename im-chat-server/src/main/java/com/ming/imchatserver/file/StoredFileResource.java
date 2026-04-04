package com.ming.imchatserver.file;

import java.nio.file.Path;

/**
 * 本地文件访问结果。
 *
 * @author ming
 */
public record StoredFileResource(Path path, String fileName, String contentType, long size) {
}
