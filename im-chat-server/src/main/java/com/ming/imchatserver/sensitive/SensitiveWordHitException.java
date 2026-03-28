package com.ming.imchatserver.sensitive;

/**
 * 敏感词命中业务异常。
 */
public class SensitiveWordHitException extends RuntimeException {

    public static final String CODE = "SENSITIVE_WORD_HIT";

    public SensitiveWordHitException() {
        super("message contains sensitive words");
    }

    public String getCode() {
        return CODE;
    }
}
