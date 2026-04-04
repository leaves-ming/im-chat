package com.ming.immessageservice.domain.exception;

/**
 * 消息服务统一业务异常。
 */
public class MessageRpcException extends RuntimeException {

    private final String code;

    public MessageRpcException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
