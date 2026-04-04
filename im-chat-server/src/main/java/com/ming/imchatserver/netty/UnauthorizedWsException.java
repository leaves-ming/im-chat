package com.ming.imchatserver.netty;

/**
 * WebSocket 未认证异常。
 */
public class UnauthorizedWsException extends RuntimeException {

    public UnauthorizedWsException(String message) {
        super(message);
    }
}
