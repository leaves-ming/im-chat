package com.ming.imchatserver.observability;

import com.ming.imchatserver.config.ObservabilityProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 允许运行时安全刷新的观测开关。
 */
@Component
public class RuntimeObservabilitySettings {

    private final AtomicBoolean accessLogEnabled;
    private final AtomicBoolean traceGenerateIfMissing;
    private final AtomicReference<String> traceHeaderName;

    public RuntimeObservabilitySettings(ObservabilityProperties properties) {
        this.accessLogEnabled = new AtomicBoolean(properties.isAccessLogEnabled());
        this.traceGenerateIfMissing = new AtomicBoolean(properties.getTrace().isGenerateIfMissing());
        this.traceHeaderName = new AtomicReference<>(properties.getTrace().getHeaderName());
    }

    public boolean isAccessLogEnabled() {
        return accessLogEnabled.get();
    }

    public void setAccessLogEnabled(boolean enabled) {
        accessLogEnabled.set(enabled);
    }

    public boolean isTraceGenerateIfMissing() {
        return traceGenerateIfMissing.get();
    }

    public void setTraceGenerateIfMissing(boolean enabled) {
        traceGenerateIfMissing.set(enabled);
    }

    public String getTraceHeaderName() {
        return traceHeaderName.get();
    }

    public void setTraceHeaderName(String headerName) {
        if (headerName != null && !headerName.isBlank()) {
            traceHeaderName.set(headerName);
        }
    }
}
