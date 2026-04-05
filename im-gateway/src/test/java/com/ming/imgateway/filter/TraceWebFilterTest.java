package com.ming.imgateway.filter;

import com.ming.imgateway.config.GatewayAccessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraceWebFilterTest {

    @Test
    void shouldReuseIncomingTraceHeader() {
        GatewayAccessProperties properties = new GatewayAccessProperties();
        properties.setTraceHeaderName("X-Trace-Id");
        TraceWebFilter filter = new TraceWebFilter(properties);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/meta").header("X-Trace-Id", "trace-001"));
        WebFilterChain chain = webExchange -> Mono.empty();

        filter.filter(exchange, chain).block();

        assertEquals("trace-001", exchange.getAttribute(TraceWebFilter.TRACE_ID_ATTR));
        assertEquals("trace-001", exchange.getResponse().getHeaders().getFirst("X-Trace-Id"));
    }

    @Test
    void shouldGenerateTraceIdWhenMissing() {
        GatewayAccessProperties properties = new GatewayAccessProperties();
        properties.setGenerateTraceIdIfMissing(true);
        TraceWebFilter filter = new TraceWebFilter(properties);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/internal/meta"));
        WebFilterChain chain = webExchange -> Mono.empty();

        filter.filter(exchange, chain).block();

        String traceId = exchange.getAttribute(TraceWebFilter.TRACE_ID_ATTR);
        assertEquals(32, traceId.length());
        assertEquals(traceId, exchange.getResponse().getHeaders().getFirst(properties.getTraceHeaderName()));
    }
}
