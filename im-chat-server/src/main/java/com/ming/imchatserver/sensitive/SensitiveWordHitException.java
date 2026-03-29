package com.ming.imchatserver.sensitive;

/**
 * 敏感词命中业务异常。
 */
public class SensitiveWordHitException extends RuntimeException {

    public static final String CODE = "SENSITIVE_WORD_HIT";
    private final String matchedWord;

    public SensitiveWordHitException() {
        this(null);
    }

    public SensitiveWordHitException(String matchedWord) {
        super("message contains sensitive words");
        this.matchedWord = matchedWord;
    }

    public String getCode() {
        return CODE;
    }

    public String getMatchedWord() {
        return matchedWord;
    }
}
