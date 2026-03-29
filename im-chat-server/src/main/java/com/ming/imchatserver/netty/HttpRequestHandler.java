package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.config.FileStorageProperties;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.file.FileMetadata;
import com.ming.imchatserver.file.FileStorageService;
import com.ming.imchatserver.file.LocalFileStorageService;
import com.ming.imchatserver.file.StoredFileResource;
import com.ming.imchatserver.metrics.MetricsService;
import com.ming.imchatserver.service.AuthService;
import io.netty.buffer.ByteBuf;
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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
/**
 * 处理 Netty 管道中的 HTTP 请求。
 * <p>
 * 目前只负责登录接口 {@code /api/auth/login}，其余 HTTP 请求会继续向后透传，
 * 由 WebSocket 握手相关 Handler 处理。
 */

    public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(HttpRequestHandler.class);
    private final NettyProperties properties;
    private final AuthService authService;
    private final MetricsService metricsService;
    private final FileStorageService fileStorageService;
    private final FileStorageProperties fileStorageProperties;
    private final ObjectMapper mapper = new ObjectMapper();
    /**
     * 创建 HTTP 处理器。
     *
     * @param properties 业务配置（token 过期时间等）
     * @param authService 认证服务，用于执行用户名密码登录
     */
    
    public HttpRequestHandler(NettyProperties properties,
                              AuthService authService,
                              MetricsService metricsService,
                              FileStorageService fileStorageService,
                              FileStorageProperties fileStorageProperties) {
        this.properties = properties;
        this.authService = authService;
        this.metricsService = metricsService;
        this.fileStorageService = fileStorageService;
        this.fileStorageProperties = fileStorageProperties;
    }

    /**
     * 单元测试兼容构造函数。
     */
    public HttpRequestHandler(NettyProperties properties, AuthService authService) {
        this(properties, authService, null, null, null);
    }

    public HttpRequestHandler(NettyProperties properties, AuthService authService, MetricsService metricsService) {
        this(properties, authService, metricsService, null, null);
    }

    @Override
    /**
     * 处理完整 HTTP 请求。
     * <p>
     * - 匹配登录接口时：解析 JSON 请求体，调用认证服务并返回 JSON 结果。<br>
     * - 非登录接口时：调用 {@code ctx.fireChannelRead(req.retain())} 交给后续 Handler。
     */
    
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        String uri = req.uri();
        if (HttpMethod.GET.equals(req.method()) && isFileRequest(uri)) {
            handleFileDownload(ctx, uri);
            return;
        }
        if (HttpMethod.GET.equals(req.method()) && uri.startsWith("/internal/metrics")) {
            if (metricsService == null) {
                writeJson(ctx, "{\"code\":1,\"msg\":\"metrics service unavailable\"}", HttpResponseStatus.SERVICE_UNAVAILABLE);
                return;
            }
            ObjectNode resp = mapper.createObjectNode();
            resp.put("code", 0);
            resp.put("msg", "ok");
            resp.set("data", mapper.valueToTree(metricsService.snapshot().asMap()));
            writeJson(ctx, mapper.writeValueAsString(resp), HttpResponseStatus.OK);
            return;
        }
        if (HttpMethod.POST.equals(req.method()) && uri.startsWith("/api/file/upload")) {
            handleFileUpload(ctx, req);
            return;
        }
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

    private void handleFileUpload(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        if (fileStorageService == null || fileStorageProperties == null) {
            writeJson(ctx, "{\"code\":1,\"msg\":\"file storage unavailable\"}", HttpResponseStatus.SERVICE_UNAVAILABLE);
            return;
        }
        Optional<AuthService.AuthUser> authUser = authenticate(req);
        if (authUser.isEmpty()) {
            writeJson(ctx, "{\"code\":401,\"msg\":\"unauthorized\"}", HttpResponseStatus.UNAUTHORIZED);
            return;
        }

        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(req);
        decoder.setDiscardThreshold(0);
        try {
            FileUpload upload = null;
            String partContentType = null;
            for (InterfaceHttpData data : decoder.getBodyHttpDatas()) {
                if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
                    upload = (FileUpload) data;
                    partContentType = upload.getContentType();
                    break;
                }
                if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                    Attribute attribute = (Attribute) data;
                    logger.debug("ignore upload attribute name={}", attribute.getName());
                }
            }
            if (upload == null || !upload.isCompleted()) {
                writeJson(ctx, "{\"code\":400,\"msg\":\"file part required\"}", HttpResponseStatus.BAD_REQUEST);
                return;
            }
            String sanitizedName = LocalFileStorageService.sanitizeFileName(upload.getFilename());
            long fileSize = upload.length();
            if (fileSize <= 0L) {
                writeJson(ctx, "{\"code\":400,\"msg\":\"file must not be empty\"}", HttpResponseStatus.BAD_REQUEST);
                return;
            }
            if (fileSize > fileStorageProperties.getMaxFileSizeBytes()) {
                writeJson(ctx, "{\"code\":400,\"msg\":\"file too large\"}", HttpResponseStatus.BAD_REQUEST);
                return;
            }
            if (!isAllowedContentType(partContentType) || !isAllowedExtension(sanitizedName)) {
                writeJson(ctx, "{\"code\":400,\"msg\":\"file type not allowed\"}", HttpResponseStatus.BAD_REQUEST);
                return;
            }
            byte[] bytes = new byte[(int) fileSize];
            ByteBuf byteBuf = upload.getByteBuf();
            byteBuf.getBytes(byteBuf.readerIndex(), bytes);
            FileMetadata metadata = fileStorageService.store(sanitizedName, partContentType, bytes);

            ObjectNode data = mapper.createObjectNode();
            data.put("fileId", metadata.getFileId());
            data.put("fileName", metadata.getFileName());
            data.put("contentType", metadata.getContentType());
            data.put("size", metadata.getSize());
            data.put("url", metadata.getUrl());
            ObjectNode resp = mapper.createObjectNode();
            resp.put("code", 0);
            resp.put("msg", "ok");
            resp.set("data", data);
            writeJson(ctx, mapper.writeValueAsString(resp), HttpResponseStatus.OK);
            logger.info("file uploaded userId={} fileId={} fileName={} size={}",
                    authUser.get().userId, metadata.getFileId(), metadata.getFileName(), metadata.getSize());
        } finally {
            decoder.destroy();
        }
    }

    private void handleFileDownload(ChannelHandlerContext ctx, String uri) throws Exception {
        if (fileStorageService == null || fileStorageProperties == null) {
            writePlain(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "file storage unavailable", "text/plain; charset=UTF-8");
            return;
        }
        String prefix = normalizedFilePrefix();
        String path = new QueryStringDecoder(uri).path();
        String suffix = path.substring(prefix.length());
        String[] parts = suffix.split("/", 3);
        if (parts.length < 3) {
            writePlain(ctx, HttpResponseStatus.NOT_FOUND, "not found", "text/plain; charset=UTF-8");
            return;
        }
        String fileId = parts[1];
        String fileName = URLDecoder.decode(parts[2], StandardCharsets.UTF_8);
        StoredFileResource resource = fileStorageService.load(fileId, fileName);
        if (resource == null) {
            writePlain(ctx, HttpResponseStatus.NOT_FOUND, "not found", "text/plain; charset=UTF-8");
            return;
        }
        byte[] bytes = Files.readAllBytes(resource.getPath());
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes));
        resp.headers().set(CONTENT_TYPE, resource.getContentType());
        resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        resp.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFileName() + "\"");
        ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }

    private Optional<AuthService.AuthUser> authenticate(FullHttpRequest req) {
        String authHeader = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty() || !authService.verifyToken(token)) {
            return Optional.empty();
        }
        return authService.parseToken(token);
    }

    private boolean isFileRequest(String uri) {
        return normalizedFilePrefix() != null && new QueryStringDecoder(uri).path().startsWith(normalizedFilePrefix() + "/");
    }

    private String normalizedFilePrefix() {
        if (fileStorageProperties == null || fileStorageProperties.getPublicUrlPrefix() == null || fileStorageProperties.getPublicUrlPrefix().isBlank()) {
            return null;
        }
        String prefix = fileStorageProperties.getPublicUrlPrefix();
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }

    private boolean isAllowedContentType(String contentType) {
        List<String> allowed = fileStorageProperties.getAllowedContentTypes();
        return allowed == null || allowed.isEmpty() || (contentType != null && allowed.stream().anyMatch(v -> v.equalsIgnoreCase(contentType)));
    }

    private boolean isAllowedExtension(String fileName) {
        List<String> allowed = fileStorageProperties.getAllowedExtensions();
        String extension = LocalFileStorageService.extractExtension(fileName);
        return allowed == null || allowed.isEmpty() || allowed.stream().anyMatch(v -> v.equalsIgnoreCase(extension));
    }

    /**
     * 以 JSON 形式回写 HTTP 响应并主动关闭连接。
     *
     * @param ctx    channel 上下文
     * @param json   响应体 JSON
     * @param status HTTP 状态码
     */
    private void writeJson(ChannelHandlerContext ctx, String json, HttpResponseStatus status) {
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(json, StandardCharsets.UTF_8));
        resp.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
        resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes());
        ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }

    private void writePlain(ChannelHandlerContext ctx, HttpResponseStatus status, String body, String contentType) {
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        resp.headers().set(CONTENT_TYPE, contentType);
        resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes());
        ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }
}
