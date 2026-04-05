package com.ming.imfileservice.service;

/**
 * 文件 uploadToken 业务异常。
 */
public class FileTokenBizException extends RuntimeException {

    private final String code;

    public FileTokenBizException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
