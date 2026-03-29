package com.ming.imchatserver.sensitive;

/**
 * 敏感词过滤统一结果。
 */
public class SensitiveWordFilterResult {

    private final boolean hit;
    private final SensitiveWordMode mode;
    private final String matchedWord;
    private final String outputText;

    public SensitiveWordFilterResult(boolean hit, SensitiveWordMode mode, String matchedWord, String outputText) {
        this.hit = hit;
        this.mode = mode;
        this.matchedWord = matchedWord;
        this.outputText = outputText;
    }

    public static SensitiveWordFilterResult passThrough(SensitiveWordMode mode, String text) {
        return new SensitiveWordFilterResult(false, mode, null, text);
    }

    public boolean isHit() {
        return hit;
    }

    public SensitiveWordMode getMode() {
        return mode;
    }

    public String getMatchedWord() {
        return matchedWord;
    }

    public String getOutputText() {
        return outputText;
    }

    public boolean shouldReject() {
        return hit && mode == SensitiveWordMode.REJECT;
    }
}
