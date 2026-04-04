package com.ming.imchatserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 观测相关配置。
 */
@Component
@ConfigurationProperties(prefix = "im.observability")
@Getter
@Setter
public class ObservabilityProperties {

    private boolean accessLogEnabled = true;

    private final Trace trace = new Trace();

    @Getter
    @Setter
    public static class Trace {
        private String headerName = "X-Trace-Id";
        private boolean generateIfMissing = true;
    }
}
