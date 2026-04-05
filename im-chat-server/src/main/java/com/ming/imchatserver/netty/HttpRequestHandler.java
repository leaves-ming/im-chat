package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.config.InstanceProperties;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.config.ObservabilityProperties;
import com.ming.imchatserver.config.RateLimitProperties;
import com.ming.imchatserver.metrics.MetricsService;
import com.ming.imchatserver.observability.RuntimeObservabilitySettings;
import com.ming.imchatserver.observability.TraceContextSupport;
import com.ming.imchatserver.service.AuthService;
import com.ming.imchatserver.service.RateLimitService;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;

import java.nio.charset.StandardCharsets;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

/**
 * 处理 Netty 管道中的 HTTP 请求。
 */
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(HttpRequestHandler.class);

    private final NettyProperties properties;
    private final AuthService authService;
    private final MetricsService metricsService;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final RuntimeObservabilitySettings runtimeObservabilitySettings;
    private final HealthEndpoint healthEndpoint;
    private final InstanceProperties instanceProperties;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpRequestHandler(NettyProperties properties,
                              AuthService authService,
                              MetricsService metricsService,
                              RateLimitService rateLimitService,
                              RateLimitProperties rateLimitProperties,
                              RuntimeObservabilitySettings runtimeObservabilitySettings,
                              HealthEndpoint healthEndpoint,
                              InstanceProperties instanceProperties) {
        this.properties = properties;
        this.authService = authService;
        this.metricsService = metricsService;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.runtimeObservabilitySettings = runtimeObservabilitySettings;
        this.healthEndpoint = healthEndpoint;
        this.instanceProperties = instanceProperties;
    }

    public HttpRequestHandler(NettyProperties properties,
                              AuthService authService,
                              MetricsService metricsService) {
        this(properties, authService, metricsService, null, null, null, null, null);
    }

    /**
     * 单元测试兼容构造函数。
     */
    public HttpRequestHandler(NettyProperties properties, AuthService authService) {
        this(properties, authService, null, null, null, null, null, null);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        RuntimeObservabilitySettings settings = runtimeSettings();
        String traceId = TraceContextSupport.resolveHttpTraceId(req.headers(), settings);
        TraceContextSupport.putMdc(traceId);
        ctx.channel().attr(NettyAttr.TRACE_ID).set(traceId);
        try {
            String uri = req.uri();
            if (settings.isAccessLogEnabled()) {
                logger.info("http request method={} uri={} remoteIp={} traceId={}",
                        req.method().name(), uri, resolveRemoteIp(ctx, req), traceId);
            }
            if (HttpMethod.GET.equals(req.method()) && uri.startsWith("/internal/metrics")) {
                if (metricsService == null) {
                    writeJson(ctx, "{\"code\":1,\"msg\":\"metrics service unavailable\"}", HttpResponseStatus.SERVICE_UNAVAILABLE);
                    return;
                }
                ObjectNode resp = mapper.createObjectNode();
                resp.put("code", 0);
                resp.put("msg", "ok");
                resp.put("traceId", traceId);
                resp.set("data", mapper.valueToTree(metricsService.snapshot().asMap()));
                writeJson(ctx, mapper.writeValueAsString(resp), HttpResponseStatus.OK, traceId);
                return;
            }
            if (HttpMethod.GET.equals(req.method()) && uri.startsWith("/internal/health")) {
                handleHealth(ctx, traceId);
                return;
            }
            if (HttpMethod.GET.equals(req.method()) && uri.startsWith("/internal/instance")) {
                handleInstance(ctx, traceId);
                return;
            }
            ctx.fireChannelRead(req.retain());
        } finally {
            TraceContextSupport.clearMdc();
        }
    }

    private void handleHealth(ChannelHandlerContext ctx, String traceId) throws Exception {
        if (healthEndpoint == null) {
            writeJson(ctx, "{\"code\":1,\"msg\":\"health endpoint unavailable\"}", HttpResponseStatus.SERVICE_UNAVAILABLE, traceId);
            return;
        }
        HealthDescriptor health = healthEndpoint.health();
        ObjectNode resp = mapper.createObjectNode();
        resp.put("code", 0);
        resp.put("msg", "ok");
        resp.put("traceId", traceId);
        resp.set("data", mapper.valueToTree(health));
        writeJson(ctx, mapper.writeValueAsString(resp), HttpResponseStatus.OK, traceId);
    }

    private void handleInstance(ChannelHandlerContext ctx, String traceId) throws Exception {
        ObjectNode data = mapper.createObjectNode();
        data.put("version", instanceProperties == null ? "unknown" : instanceProperties.getVersion());
        data.put("zone", instanceProperties == null ? "default" : instanceProperties.getZone());
        data.put("protocol", instanceProperties == null ? "netty-ws-http" : instanceProperties.getProtocol());
        data.put("startupTime", instanceProperties == null ? "unknown" : instanceProperties.getStartupTime());
        ObjectNode resp = mapper.createObjectNode();
        resp.put("code", 0);
        resp.put("msg", "ok");
        resp.put("traceId", traceId);
        resp.set("data", data);
        writeJson(ctx, mapper.writeValueAsString(resp), HttpResponseStatus.OK, traceId);
    }

    private void writeJson(ChannelHandlerContext ctx, String json, HttpResponseStatus status) {
        writeJson(ctx, json, status, TraceContextSupport.currentTraceId(ctx.channel()));
    }

    private void writeJson(ChannelHandlerContext ctx, String json, HttpResponseStatus status, String traceId) {
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(json, StandardCharsets.UTF_8));
        resp.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
        resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes());
        if (traceId != null) {
            resp.headers().set(runtimeSettings().getTraceHeaderName(), traceId);
        }
        ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }

    private RuntimeObservabilitySettings runtimeSettings() {
        if (runtimeObservabilitySettings != null) {
            return runtimeObservabilitySettings;
        }
        return new RuntimeObservabilitySettings(new ObservabilityProperties());
    }

    private boolean consumeRateLimit(String scope, String dimension, String subject, long limit, long windowSeconds) {
        if (rateLimitService == null || subject == null || subject.isBlank()) {
            return true;
        }
        return rateLimitService.checkAndIncrement(scope, dimension, subject, limit, windowSeconds).allowed();
    }

    private String resolveRemoteIp(ChannelHandlerContext ctx, FullHttpRequest req) {
        String remoteIp = req.headers().get("X-Real-IP");
        if (remoteIp == null) {
            String xff = req.headers().get("X-Forwarded-For");
            if (xff != null) {
                remoteIp = xff.split(",")[0].trim();
            }
        }
        if (remoteIp == null && ctx.channel().remoteAddress() != null) {
            remoteIp = ctx.channel().remoteAddress().toString();
        }
        return remoteIp == null ? "unknown" : remoteIp;
    }
}
