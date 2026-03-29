package com.ming.imchatserver.sensitive;

/**
 * 敏感词词库不可用时的异常。
 */
public class SensitiveWordUnavailableException extends RuntimeException {

    public static final String CODE = "SENSITIVE_WORD_UNAVAILABLE";

    public SensitiveWordUnavailableException(String source, Throwable cause) {
        super("sensitive word dictionary unavailable: " + source, cause);
    }

    public String getCode() {
        return CODE;
    }
}
