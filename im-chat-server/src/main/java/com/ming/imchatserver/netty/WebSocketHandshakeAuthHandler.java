package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.service.AuthService;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

public class WebSocketHandshakeAuthHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandshakeAuthHandler.class);
    private final NettyProperties properties;
    private final AuthService authService;
    private final ChannelUserManager channelUserManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public WebSocketHandshakeAuthHandler(NettyProperties properties, AuthService authService, ChannelUserManager channelUserManager) {
        this.properties = properties;
        this.authService = authService;
        this.channelUserManager = channelUserManager;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest req) {
            String uri = req.uri();
            URI parsed = new URI(uri);
            String path = parsed.getPath();
            if (properties.getWebsocketPath().equals(path)) {
                // origin check
                if (properties.isOriginCheckEnabled()) {
                    String origin = req.headers().get(HttpHeaderNames.ORIGIN);
                    boolean okOrigin = false;
                    if (origin != null) {
                        for (String allow : properties.getOriginWhitelist()) {
                            if (origin.startsWith(allow)) { okOrigin = true; break; }
                        }
                    }
                    if (!okOrigin) {
                        writeErrorAndClose(ctx, HttpResponseStatus.FORBIDDEN, 403, "origin not allowed");
                        return;
                    }
                }
                // extract token with priority:
                // 1) Authorization: Bearer <token>
                // 2) query parameter token
                // 3) Sec-WebSocket-Protocol header (compatible but has security/visibility risks)
                String token = null;
                String authHeader = req.headers().get(HttpHeaderNames.AUTHORIZATION);
                if (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) {
                    token = authHeader.substring(7).trim();
                }
                if (token == null) {
                    String query = parsed.getQuery();
                    if (query != null && query.contains("token=")) {
                        for (String part : query.split("&")) {
                            if (part.startsWith("token=")) { token = part.substring("token=".length()); break; }
                        }
                    }
                }
                if (token == null) {
                    List<String> protocols = req.headers().getAll("Sec-WebSocket-Protocol");
                    if (!protocols.isEmpty()) token = protocols.get(0);
                }
                if (token == null || !authService.verifyToken(token)) {
                    writeErrorAndClose(ctx, HttpResponseStatus.UNAUTHORIZED, 401, "invalid token");
                    return;
                }
                Optional<AuthService.AuthUser> user = authService.parseToken(token);
                if (user.isEmpty()) {
                    writeErrorAndClose(ctx, HttpResponseStatus.UNAUTHORIZED, 401, "invalid token payload");
                    return;
                }
                AuthService.AuthUser u = user.get();
                // set attr for downstream handlers
                ctx.channel().attr(NettyAttr.USER_ID).set(u.userId);
                // log with proxied IP headers if present
                String remoteIp = req.headers().get("X-Real-IP");
                if (remoteIp == null) {
                    String xff = req.headers().get("X-Forwarded-For");
                    if (xff != null) remoteIp = xff.split(",")[0].trim();
                }
                if (remoteIp == null) remoteIp = ctx.channel().remoteAddress() != null ? ctx.channel().remoteAddress().toString() : "unknown";
                logger.info("websocket handshake accepted: userId={} channelId={} remote={} path={}", u.userId, ctx.channel().id(), remoteIp, path);
                // let the pipeline continue (WebSocketServerProtocolHandler will do handshake)
            }
        }
        super.channelRead(ctx, msg);
    }

    private void writeErrorAndClose(ChannelHandlerContext ctx, HttpResponseStatus status, int code, String msg) {
        try {
            Map<String,Object> m = new HashMap<>();
            m.put("code", code);
            m.put("msg", msg);
            String body = mapper.writeValueAsString(m);
            FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
            resp.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
            resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes());
            ctx.writeAndFlush(resp).addListener(f -> ctx.close());
        } catch (Exception ex) {
            logger.error("writeErrorAndClose error", ex);
            ctx.close();
        }
    }
}

