package com.ming.immessageservice.sensitive;

import com.ming.immessageservice.config.SensitiveWordProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        SensitiveWordFilterResult result = service.filter("this badword should hit");

        assertTrue(result.isHit());
        assertTrue(result.shouldReject());
        assertEquals("badword", result.getMatchedWord());
    }

    @Test
    void containsShouldReturnFalseWhenDisabled() {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setEnabled(false);
        properties.setWordSource("classpath:test_sensitive_words.txt");

        SensitiveWordService service = new SensitiveWordService(properties, new DefaultResourceLoader());
        service.init();

        SensitiveWordFilterResult result = service.filter("badword");

        assertFalse(result.isHit());
        assertEquals("badword", result.getOutputText());
        assertEquals(SensitiveWordMode.OFF, result.getMode());
    }

    @Test
    void replaceModeShouldReturnMaskedText() {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setEnabled(true);
        properties.setMode("REPLACE");
        properties.setWordSource("classpath:test_sensitive_words.txt");

        SensitiveWordService service = new SensitiveWordService(properties, new DefaultResourceLoader());
        service.init();

        SensitiveWordFilterResult result = service.filter("this badword should replace");

        assertTrue(result.isHit());
        assertFalse(result.shouldReject());
        assertEquals("this ******* should replace", result.getOutputText());
    }

    @Test
    void emptyWordSourceShouldNotThrowAndShouldMiss() {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setEnabled(true);
        properties.setMode("REJECT");
        properties.setWordSource("classpath:empty_sensitive_words.txt");

        SensitiveWordService service = new SensitiveWordService(properties, new DefaultResourceLoader());
        service.init();

        SensitiveWordFilterResult result = service.filter("hello");

        assertFalse(result.isHit());
        assertEquals("hello", result.getOutputText());
    }

    @Test
    void loadFailureShouldPassWhenFailOpenEnabled() {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setEnabled(true);
        properties.setMode("REJECT");
        properties.setWordSource("classpath:missing_sensitive_words.txt");
        properties.setFailOpen(true);

        SensitiveWordService service = new SensitiveWordService(properties, new DefaultResourceLoader());
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
        properties.setWordSource("classpath:missing_sensitive_words.txt");
        properties.setFailOpen(false);

        SensitiveWordService service = new SensitiveWordService(properties, new DefaultResourceLoader());
        service.init();

        assertThrows(SensitiveWordUnavailableException.class, () -> service.filter("badword"));
    }
}
