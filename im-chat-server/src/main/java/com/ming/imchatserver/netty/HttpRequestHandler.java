package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.service.AuthService;
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

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(HttpRequestHandler.class);
    private final NettyProperties properties;
    private final AuthService authService;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpRequestHandler(NettyProperties properties, AuthService authService) {
        this.properties = properties;
        this.authService = authService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        String uri = req.uri();
        if (HttpMethod.POST.equals(req.method()) && uri.startsWith("/api/auth/login")) {
            String body = req.content().toString(StandardCharsets.UTF_8);
            try {
                Map map = mapper.readValue(body, Map.class);
                String username = map.getOrDefault("username", "").toString();
                String password = map.getOrDefault("password", "").toString();
                AuthService.AuthResult r = authService.login(username, password);
                if (r != null && r.success) {
                    ObjectNode data = mapper.createObjectNode();
                    data.put("token", r.token);
                    data.put("userId", r.userId);
                    data.put("expiresIn", properties.getTokenExpireSeconds());
                    ObjectNode resp = mapper.createObjectNode();
                    resp.put("code", 0);
                    resp.put("msg", "ok");
                    resp.set("data", data);
                    writeJson(ctx, mapper.writeValueAsString(resp), HttpResponseStatus.OK);
                    // log with proxy header support
                    String remoteIp = req.headers().get("X-Real-IP");
                    if (remoteIp == null) {
                        String xff = req.headers().get("X-Forwarded-For");
                        if (xff != null) remoteIp = xff.split(",")[0].trim();
                    }
                    if (remoteIp == null) remoteIp = ctx.channel().remoteAddress() != null ? ctx.channel().remoteAddress().toString() : "unknown";
                    logger.info("login success username={} userId={} remote={}", username, r.userId, remoteIp);
                } else {
                    ObjectNode resp = mapper.createObjectNode();
                    resp.put("code", 401);
                    resp.put("msg", "invalid credentials");
                    writeJson(ctx, mapper.writeValueAsString(resp), HttpResponseStatus.UNAUTHORIZED);
                }
            } catch (Exception ex) {
                logger.warn("invalid login request body", ex);
                ObjectNode resp = mapper.createObjectNode();
                resp.put("code", 400);
                resp.put("msg", "bad request");
                writeJson(ctx, mapper.writeValueAsString(resp), HttpResponseStatus.BAD_REQUEST);
            }
        } else {
            // 非登录请求，传递给下一个 handler（可能是 websocket handshake）
            ctx.fireChannelRead(req.retain());
        }
    }

    private void writeJson(ChannelHandlerContext ctx, String json, HttpResponseStatus status) {
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(json, StandardCharsets.UTF_8));
        resp.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
        resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes());
        ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }
}

