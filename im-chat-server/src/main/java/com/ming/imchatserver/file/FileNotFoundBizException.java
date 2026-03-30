package com.ming.imchatserver.file;

/**
 * 文件不存在。
 */
public class FileNotFoundBizException extends RuntimeException {
    public FileNotFoundBizException(String message) {
        super(message);
    }
}
