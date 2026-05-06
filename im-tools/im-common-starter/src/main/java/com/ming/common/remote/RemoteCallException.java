package com.ming.common.remote;

/**
 * 远程调用异常
 */
public class RemoteCallException extends RuntimeException {

    private final String code;

    public RemoteCallException(String code, String message) {
        super(message);
        this.code = code;
    }

    public RemoteCallException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
