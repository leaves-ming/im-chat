package com.ming.imchatserver.file;

/**
 * 文件访问权限不足。
 */
public class FileAccessDeniedException extends RuntimeException {
    public FileAccessDeniedException(String message) {
        super(message);
    }
}
