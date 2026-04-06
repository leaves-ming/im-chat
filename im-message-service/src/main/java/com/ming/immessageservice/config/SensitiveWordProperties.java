package com.ming.immessageservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 敏感词过滤配置。
 */
@Component
@ConfigurationProperties(prefix = "im.sensitive")
@Getter
@Setter
public class SensitiveWordProperties {
    /** 是否启用敏感词过滤，默认关闭以保持兼容。 */
    private boolean enabled = false;
    /** 过滤模式，支持 REJECT / REPLACE。 */
    private String mode = "REJECT";
    /** 词库来源，默认 classpath 文件。 */
    private String wordSource = "classpath:sensitive_words.txt";
    /** 词库加载失败时是否放行，默认 true 以保持兼容。 */
    private boolean failOpen = true;
}
