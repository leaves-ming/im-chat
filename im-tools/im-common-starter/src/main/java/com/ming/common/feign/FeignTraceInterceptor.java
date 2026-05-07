package com.ming.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;

/**
 * Feign调用TraceId透传拦截器，全链路日志追踪
 */
public class FeignTraceInterceptor implements RequestInterceptor {
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public void apply(RequestTemplate template) {
        String traceId = MDC.get(TRACE_ID_HEADER);
        if (traceId != null) {
            template.header(TRACE_ID_HEADER, traceId);
        }
    }
}
