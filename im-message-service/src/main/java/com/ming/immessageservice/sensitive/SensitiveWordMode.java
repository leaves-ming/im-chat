package com.ming.immessageservice.sensitive;

/**
 * 敏感词过滤模式。
 */
public enum SensitiveWordMode {
    OFF,
    REJECT,
    REPLACE;

    public static SensitiveWordMode from(boolean enabled, String configuredMode) {
        if (!enabled) {
            return OFF;
        }
        if ("REPLACE".equalsIgnoreCase(configuredMode)) {
            return REPLACE;
        }
        return REJECT;
    }
}
