package com.ming.imchatserver.sensitive;

import com.ming.imchatserver.config.SensitiveWordProperties;
import com.ming.imchatserver.metrics.MetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SensitiveWordServiceTest {

    @Test
    void containsShouldHitWhenEnabledAndWordLoaded() {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setEnabled(true);
        properties.setMode("REJECT");
        properties.setWordSource("classpath:test_sensitive_words.txt");

        SensitiveWordService service = new SensitiveWordService(properties, new DefaultResourceLoader(), metricsService());
        service.init();

        SensitiveWordFilterResult hit = service.filter("this contains badword");
        SensitiveWordFilterResult miss = service.filter("clean text");

        assertTrue(hit.isHit());
        assertEquals(SensitiveWordMode.REJECT, hit.getMode());
        assertEquals("badword", hit.getMatchedWord());
        assertEquals("this contains badword", hit.getOutputText());
        assertFalse(miss.isHit());
    }

    @Test
    void containsShouldReturnFalseWhenDisabled() {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setEnabled(false);
        properties.setWordSource("classpath:test_sensitive_words.txt");

        SensitiveWordService service = new SensitiveWordService(properties, new DefaultResourceLoader(), metricsService());
        service.init();

        SensitiveWordFilterResult result = service.filter("badword");

        assertFalse(result.isHit());
        assertEquals(SensitiveWordMode.OFF, result.getMode());
        assertEquals("badword", result.getOutputText());
    }

    @Test
    void emptyWordSourceShouldNotThrowAndShouldMiss() {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setEnabled(true);
        properties.setWordSource("classpath:empty_sensitive_words.txt");

        SensitiveWordService service = new SensitiveWordService(properties, new DefaultResourceLoader(), metricsService());
        service.init();

        assertFalse(service.contains("anything"));
        assertEquals("anything", service.replace("anything"));
    }

    @Test
    void replaceModeShouldReturnMaskedTextAndAccumulateMetrics() {
        MetricsService metricsService = metricsService();
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setEnabled(true);
        properties.setMode("REPLACE");
        properties.setWordSource("classpath:test_sensitive_words.txt");

        SensitiveWordService service = new SensitiveWordService(properties, new DefaultResourceLoader(), metricsService);
        service.init();

        SensitiveWordFilterResult result = service.filter("before badword after");

        assertTrue(result.isHit());
        assertEquals(SensitiveWordMode.REPLACE, result.getMode());
        assertEquals("badword", result.getMatchedWord());
        assertEquals("before ******* after", result.getOutputText());
        assertEquals(1L, metricsService.snapshot().getImSensitiveCheckTotal());
        assertEquals(1L, metricsService.snapshot().getImSensitiveHitTotal());
        assertEquals(1L, metricsService.snapshot().getImSensitiveReplaceTotal());
    }

    @Test
    void loadFailureShouldPassWhenFailOpenEnabled() {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setEnabled(true);
        properties.setMode("REJECT");
        properties.setFailOpen(true);
        properties.setWordSource("classpath:missing_sensitive_words.txt");

        SensitiveWordService service = new SensitiveWordService(properties, new DefaultResourceLoader(), metricsService());
        service.init();

        SensitiveWordFilterResult result = service.filter("badword");

        assertFalse(result.isHit());
        assertEquals("badword", result.getOutputText());
    }

    @Test
    void loadFailureShouldRejectWhenFailOpenDisabled() {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setEnabled(true);
        properties.setMode("REJECT");
        properties.setFailOpen(false);
        properties.setWordSource("classpath:missing_sensitive_words.txt");

        SensitiveWordService service = new SensitiveWordService(properties, new DefaultResourceLoader(), metricsService());
        service.init();

        assertThrows(SensitiveWordUnavailableException.class, () -> service.filter("badword"));
    }

    private MetricsService metricsService() {
        return new MetricsService();
    }
}
