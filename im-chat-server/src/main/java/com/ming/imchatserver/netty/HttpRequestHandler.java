package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.config.FileStorageProperties;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.file.FileAccessDeniedException;
import com.ming.imchatserver.file.FileMetadata;
import com.ming.imchatserver.file.FileNotFoundBizException;
import com.ming.imchatserver.file.LocalFileStorageService;
import com.ming.imchatserver.file.StoredFileResource;
import com.ming.imchatserver.metrics.MetricsService;
import com.ming.imchatserver.service.AuthService;
import com.ming.imchatserver.service.FileService;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.stream.ChunkedNioFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

/**
 * 处理 Netty 管道中的 HTTP 请求。
 */
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(HttpRequestHandler.class);

    private final NettyProperties properties;
    private final AuthService authService;
    private final MetricsService metricsService;
    private final FileService fileService;
    private final FileStorageProperties fileStorageProperties;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpRequestHandler(NettyProperties properties,
                              AuthService authService,
                              MetricsService metricsService,
                              FileService fileService,
                              FileStorageProperties fileStorageProperties) {
        this.properties = properties;
        this.authService = authService;
        this.metricsService = metricsService;
        this.fileService = fileService;
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
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        String uri = req.uri();
        if (HttpMethod.GET.equals(req.method()) && isSignedFileDownloadRequest(uri)) {
            handleSignedFileDownload(ctx, req);
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
        if (HttpMethod.POST.equals(req.method()) && uri.startsWith("/api/file/download-url")) {
            handleFileDownloadUrl(ctx, req);
            return;
        }
        if (HttpMethod.POST.equals(req.method()) && uri.startsWith("/api/auth/login")) {
            handleLogin(ctx, req);
            return;
        }

        ctx.fireChannelRead(req.retain());
    }

    private void handleLogin(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        String body = req.content().toString(StandardCharsets.UTF_8);
        try {
            Map map = mapper.readValue(body, Map.class);
            String username = map.getOrDefault("username", "").toString();
            String password = map.getOrDefault("password", "").toString();
            AuthService.AuthResult result = authService.login(username, password);
            if (result != null && result.success) {
                ObjectNode data = mapper.createObjectNode();
                data.put("token", result.token);
                data.put("userId", result.userId);
                data.put("expiresIn", properties.getTokenExpireSeconds());
                ObjectNode resp = mapper.createObjectNode();
                resp.put("code", 0);
                resp.put("msg", "ok");
                resp.set("data", data);
                writeJson(ctx, mapper.writeValueAsString(resp), HttpResponseStatus.OK);
                logger.info("login success username={} userId={}", username, result.userId);
                return;
            }
            ObjectNode resp = mapper.createObjectNode();
            resp.put("code", 401);
            resp.put("msg", "invalid credentials");
            writeJson(ctx, mapper.writeValueAsString(resp), HttpResponseStatus.UNAUTHORIZED);
        } catch (Exception ex) {
            logger.warn("invalid login request body", ex);
            ObjectNode resp = mapper.createObjectNode();
            resp.put("code", 400);
            resp.put("msg", "bad request");
            writeJson(ctx, mapper.writeValueAsString(resp), HttpResponseStatus.BAD_REQUEST);
        }
    }

    private void handleFileUpload(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        if (fileService == null || fileStorageProperties == null) {
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

            try (InputStream inputStream = new ByteBufInputStream(upload.getByteBuf().duplicate(), false)) {
                FileMetadata metadata = fileService.store(authUser.get().userId, sanitizedName, partContentType, fileSize, inputStream);
                ObjectNode data = mapper.createObjectNode();
                data.put("uploadToken", metadata.getUploadToken());
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
            }
        } finally {
            decoder.destroy();
        }
    }

    private void handleFileDownloadUrl(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        if (fileService == null || fileStorageProperties == null) {
            writeJson(ctx, "{\"code\":1,\"msg\":\"file storage unavailable\"}", HttpResponseStatus.SERVICE_UNAVAILABLE);
            return;
        }
        Optional<AuthService.AuthUser> authUser = authenticate(req);
        if (authUser.isEmpty()) {
            writeJson(ctx, "{\"code\":401,\"msg\":\"unauthorized\"}", HttpResponseStatus.UNAUTHORIZED);
            return;
        }
        try {
            JsonNode body = mapper.readTree(req.content().toString(StandardCharsets.UTF_8));
            String fileId = body == null ? null : body.path("fileId").asText(null);
            if (fileId == null || fileId.isBlank()) {
                writeJson(ctx, "{\"code\":400,\"msg\":\"fileId required\"}", HttpResponseStatus.BAD_REQUEST);
                return;
            }
            FileService.DownloadUrlResult result = fileService.createDownloadUrl(authUser.get().userId, fileId);
            ObjectNode data = mapper.createObjectNode();
            data.put("downloadUrl", result.downloadUrl());
            data.put("expireAt", result.expireAt());
            ObjectNode resp = mapper.createObjectNode();
            resp.put("code", 0);
            resp.put("msg", "ok");
            resp.set("data", data);
            writeJson(ctx, mapper.writeValueAsString(resp), HttpResponseStatus.OK);
        } catch (FileAccessDeniedException ex) {
            writeJson(ctx, "{\"code\":403,\"msg\":\"forbidden\"}", HttpResponseStatus.FORBIDDEN);
        } catch (FileNotFoundBizException ex) {
            writeJson(ctx, "{\"code\":404,\"msg\":\"not found\"}", HttpResponseStatus.NOT_FOUND);
        } catch (Exception ex) {
            logger.warn("invalid download-url request", ex);
            writeJson(ctx, "{\"code\":400,\"msg\":\"bad request\"}", HttpResponseStatus.BAD_REQUEST);
        }
    }

    private void handleSignedFileDownload(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        if (fileService == null || fileStorageProperties == null) {
            writePlain(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "file storage unavailable", "text/plain; charset=UTF-8");
            return;
        }
        QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
        String fileId = firstQueryParam(decoder, "fileId");
        String expRaw = firstQueryParam(decoder, "exp");
        String signature = firstQueryParam(decoder, "sig");

        final StoredFileResource resource;
        try {
            long expireAt = expRaw == null ? -1L : Long.parseLong(expRaw);
            resource = fileService.loadBySignedDownloadUrl(fileId, expireAt, signature);
        } catch (FileAccessDeniedException ex) {
            writePlain(ctx, HttpResponseStatus.FORBIDDEN, "forbidden", "text/plain; charset=UTF-8");
            return;
        } catch (NumberFormatException ex) {
            writePlain(ctx, HttpResponseStatus.FORBIDDEN, "forbidden", "text/plain; charset=UTF-8");
            return;
        } catch (FileNotFoundBizException ex) {
            writePlain(ctx, HttpResponseStatus.NOT_FOUND, "not found", "text/plain; charset=UTF-8");
            return;
        }

        RandomAccessFile raf = new RandomAccessFile(resource.getPath().toFile(), "r");
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(CONTENT_TYPE, resource.getContentType());
        response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFileName() + "\"");
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        HttpUtil.setContentLength(response, resource.getSize());

        ctx.write(response);
        ctx.write(new HttpChunkedInput(new ChunkedNioFile(raf.getChannel())))
                .addListener(future -> {
                    try {
                        raf.close();
                    } catch (Exception ex) {
                        logger.warn("close file failed", ex);
                    }
                    if (!future.isSuccess()) {
                        logger.warn("stream file failed fileName={}", resource.getFileName(), future.cause());
                    }
                });
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
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

    private boolean isSignedFileDownloadRequest(String uri) {
        String prefix = normalizedFilePrefix();
        return prefix != null && (prefix + "/download").equals(new QueryStringDecoder(uri).path());
    }

    private String normalizedFilePrefix() {
        if (fileStorageProperties == null) {
            return null;
        }
        String prefix = fileStorageProperties.getPublicUrlPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "/files";
        }
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

    private String firstQueryParam(QueryStringDecoder decoder, String key) {
        List<String> values = decoder.parameters().get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }
}
