package com.ming.imchatserver.service;

/**
 * 消息撤回业务异常。
 */
public class MessageRecallException extends RuntimeException {

    private final String code;

    public MessageRecallException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
