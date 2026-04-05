package com.ming.imchatserver.service;

/**
 * social 远程调用异常。
 */
public class SocialRpcException extends RuntimeException {

    private final String code;

    public SocialRpcException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
