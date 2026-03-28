package com.ming.imchatserver.sensitive;

import com.ming.imchatserver.config.SensitiveWordProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveWordServiceTest {

    @Test
    void containsShouldHitWhenEnabledAndWordLoaded() {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setEnabled(true);
        properties.setMode("REJECT");
        properties.setWordSource("classpath:test_sensitive_words.txt");

        SensitiveWordService service = new SensitiveWordService(properties, new DefaultResourceLoader());
        service.init();

        assertTrue(service.contains("this contains badword"));
        assertFalse(service.contains("clean text"));
    }

    @Test
    void containsShouldReturnFalseWhenDisabled() {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setEnabled(false);
        properties.setWordSource("classpath:test_sensitive_words.txt");

        SensitiveWordService service = new SensitiveWordService(properties, new DefaultResourceLoader());
        service.init();

        assertFalse(service.contains("badword"));
    }

    @Test
    void emptyWordSourceShouldNotThrowAndShouldMiss() {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setEnabled(true);
        properties.setWordSource("classpath:empty_sensitive_words.txt");

        SensitiveWordService service = new SensitiveWordService(properties, new DefaultResourceLoader());
        service.init();

        assertFalse(service.contains("anything"));
        assertEquals("anything", service.replace("anything"));
    }
}
