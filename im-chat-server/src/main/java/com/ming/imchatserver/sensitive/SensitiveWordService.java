package com.ming.imchatserver.sensitive;

import com.ming.imchatserver.config.SensitiveWordProperties;
import com.ming.imchatserver.metrics.MetricsService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词服务：负责词库加载与匹配委托。
 */
@Service
public class SensitiveWordService {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveWordService.class);

    private final SensitiveWordProperties properties;
    private final ResourceLoader resourceLoader;
    private final MetricsService metricsService;
    private volatile SensitiveWordEngine engine = new SensitiveWordEngine(List.of());
    private volatile RuntimeException loadFailure;

    @Autowired
    public SensitiveWordService(SensitiveWordProperties properties,
                                ResourceLoader resourceLoader,
                                MetricsService metricsService) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.metricsService = metricsService;
    }

    public SensitiveWordService(SensitiveWordProperties properties, ResourceLoader resourceLoader) {
        this(properties, resourceLoader, null);
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public boolean contains(String text) {
        return filter(text).isHit();
    }

    public String replace(String text) {
        return filter(text).getOutputText();
    }

    public boolean isRejectMode() {
        return resolveMode() == SensitiveWordMode.REJECT;
    }

    public SensitiveWordFilterResult check(String text) {
        return filter(text);
    }

    public SensitiveWordFilterResult filter(String text) {
        SensitiveWordMode mode = resolveMode();
        if (mode == SensitiveWordMode.OFF) {
            return SensitiveWordFilterResult.passThrough(SensitiveWordMode.OFF, text);
        }
        incrementCheckMetric();
        ensureAvailable();

        SensitiveWordEngine.EngineResult engineResult = engine.filter(text);
        String outputText = mode == SensitiveWordMode.REPLACE ? engineResult.getOutputText() : text;
        SensitiveWordFilterResult result = new SensitiveWordFilterResult(
                engineResult.isHit(),
                mode,
                engineResult.getMatchedWord(),
                outputText
        );
        if (result.isHit()) {
            incrementHitMetric();
            if (mode == SensitiveWordMode.REPLACE) {
                incrementReplaceMetric();
            }
            logger.warn("sensitive word hit mode={} matchedWord={} textLength={} failOpen={}",
                    mode,
                    result.getMatchedWord(),
                    text == null ? 0 : text.length(),
                    properties.isFailOpen());
        }
        return result;
    }

    public void validateTextOrThrow(String text) {
        SensitiveWordFilterResult result = filter(text);
        if (result.shouldReject()) {
            throw new SensitiveWordHitException(result.getMatchedWord());
        }
    }

    void reload() {
        try {
            List<String> words = loadWords();
            this.engine = new SensitiveWordEngine(words);
            this.loadFailure = null;
            logger.info("sensitive words loaded count={} source={} mode={} failOpen={}",
                    words.size(),
                    properties.getWordSource(),
                    resolveMode(),
                    properties.isFailOpen());
        } catch (Exception ex) {
            this.engine = new SensitiveWordEngine(List.of());
            this.loadFailure = ex instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new IllegalStateException(ex.getMessage(), ex);
            if (properties.isFailOpen()) {
                logger.warn("load sensitive word source failed, fail-open enabled source={}", properties.getWordSource(), ex);
            } else {
                logger.error("load sensitive word source failed, fail-close enabled source={}", properties.getWordSource(), ex);
            }
        }
    }

    private List<String> loadWords() {
        String source = properties.getWordSource();
        if (source == null || source.isBlank()) {
            return List.of();
        }
        Resource resource = resourceLoader.getResource(source);
        if (!resource.exists()) {
            throw new IllegalStateException("sensitive word source not found: " + source);
        }
        List<String> words = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                words.add(trimmed);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("load sensitive word source failed: " + source, ex);
        }
        return words;
    }

    private SensitiveWordMode resolveMode() {
        return SensitiveWordMode.from(properties.isEnabled(), properties.getMode());
    }

    private void ensureAvailable() {
        RuntimeException failure = loadFailure;
        if (failure == null || properties.isFailOpen()) {
            return;
        }
        throw new SensitiveWordUnavailableException(properties.getWordSource(), failure);
    }

    private void incrementCheckMetric() {
        if (metricsService != null) {
            metricsService.incrementSensitiveCheck();
        }
    }

    private void incrementHitMetric() {
        if (metricsService != null) {
            metricsService.incrementSensitiveHit();
        }
    }

    private void incrementReplaceMetric() {
        if (metricsService != null) {
            metricsService.incrementSensitiveReplace();
        }
    }
}
