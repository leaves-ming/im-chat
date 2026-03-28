package com.ming.imchatserver.sensitive;

import com.ming.imchatserver.config.SensitiveWordProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private volatile SensitiveWordEngine engine = new SensitiveWordEngine(List.of());

    public SensitiveWordService(SensitiveWordProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public boolean contains(String text) {
        if (!properties.isEnabled()) {
            return false;
        }
        return engine.contains(text);
    }

    public String replace(String text) {
        if (!properties.isEnabled()) {
            return text;
        }
        return engine.replace(text);
    }

    public boolean isRejectMode() {
        return "REJECT".equalsIgnoreCase(properties.getMode());
    }

    public void validateTextOrThrow(String text) {
        if (!properties.isEnabled() || !isRejectMode()) {
            return;
        }
        if (engine.contains(text)) {
            throw new SensitiveWordHitException();
        }
    }

    void reload() {
        List<String> words = loadWords();
        this.engine = new SensitiveWordEngine(words);
        logger.info("sensitive words loaded count={} source={}", words.size(), properties.getWordSource());
    }

    private List<String> loadWords() {
        String source = properties.getWordSource();
        if (source == null || source.isBlank()) {
            return List.of();
        }
        try {
            Resource resource = resourceLoader.getResource(source);
            if (!resource.exists()) {
                logger.warn("sensitive word source not found: {}", source);
                return List.of();
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
            }
            return words;
        } catch (Exception ex) {
            logger.warn("load sensitive word source failed: {}", source, ex);
            return List.of();
        }
    }
}
