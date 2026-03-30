package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.config.FileStorageProperties;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.file.FileAccessDeniedException;
import com.ming.imchatserver.file.FileMetadata;
import com.ming.imchatserver.service.AuthService;
import com.ming.imchatserver.service.FileService;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpRequestHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void uploadShouldReturnFileMetadata() throws Exception {
        NettyProperties nettyProperties = new NettyProperties();
        AuthService authService = mock(AuthService.class);
        FileStorageProperties fileStorageProperties = new FileStorageProperties();
        fileStorageProperties.setLocalBaseDir(tempDir.toString());
        fileStorageProperties.setAllowedContentTypes(java.util.List.of("text/plain"));
        fileStorageProperties.setAllowedExtensions(java.util.List.of("txt"));
        FileService fileService = mock(FileService.class);

        AuthService.AuthUser authUser = new AuthService.AuthUser();
        authUser.userId = 1L;
        authUser.username = "alice";
        when(authService.verifyToken("token-1")).thenReturn(true);
        when(authService.parseToken("token-1")).thenReturn(Optional.of(authUser));

        when(fileService.store(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("note.txt"),
                org.mockito.ArgumentMatchers.eq("text/plain"),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any())).thenReturn(new FileMetadata("up-1", "f_1", "note.txt", "text/plain", 10L, "/files/f_1"));

        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler(nettyProperties, authService, null, fileService, fileStorageProperties));

        String boundary = "----CodexBoundary";
        byte[] body = (
                "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"../note.txt\"\r\n" +
                "Content-Type: text/plain\r\n\r\n" +
                "hello file\r\n" +
                "--" + boundary + "--\r\n"
        ).getBytes(StandardCharsets.UTF_8);

        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "/api/file/upload",
                Unpooled.wrappedBuffer(body)
        );
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
        request.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer token-1");

        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();
        JsonNode json = mapper.readTree(response.content().toString(StandardCharsets.UTF_8));

        assertEquals(0, json.get("code").asInt());
        assertEquals("up-1", json.get("data").get("uploadToken").asText());
        assertEquals("note.txt", json.get("data").get("fileName").asText());
        assertEquals("text/plain", json.get("data").get("contentType").asText());
        assertTrue(json.get("data").get("url").asText().contains("/files/"));
    }

    @Test
    void uploadShouldRejectUnauthorized() throws Exception {
        NettyProperties nettyProperties = new NettyProperties();
        AuthService authService = mock(AuthService.class);
        FileStorageProperties fileStorageProperties = new FileStorageProperties();
        fileStorageProperties.setLocalBaseDir(tempDir.toString());
        FileService fileService = mock(FileService.class);

        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler(nettyProperties, authService, null, fileService, fileStorageProperties));
        byte[] body = "bad".getBytes(StandardCharsets.UTF_8);
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "/api/file/upload",
                Unpooled.wrappedBuffer(body)
        );
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=x");
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);

        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();
        JsonNode json = mapper.readTree(response.content().toString(StandardCharsets.UTF_8));

        assertEquals(401, json.get("code").asInt());
    }

    @Test
    void downloadShouldRejectWhenMissingToken() {
        NettyProperties nettyProperties = new NettyProperties();
        AuthService authService = mock(AuthService.class);
        FileStorageProperties fileStorageProperties = new FileStorageProperties();
        FileService fileService = mock(FileService.class);

        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler(nettyProperties, authService, null, fileService, fileStorageProperties));
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                "/files/f_1"
        );

        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.UNAUTHORIZED, response.status());
    }

    @Test
    void downloadShouldReturnForbiddenWhenUnauthorizedAccess() {
        NettyProperties nettyProperties = new NettyProperties();
        AuthService authService = mock(AuthService.class);
        FileStorageProperties fileStorageProperties = new FileStorageProperties();
        FileService fileService = mock(FileService.class);

        AuthService.AuthUser authUser = new AuthService.AuthUser();
        authUser.userId = 1L;
        when(authService.verifyToken("token-1")).thenReturn(true);
        when(authService.parseToken("token-1")).thenReturn(Optional.of(authUser));
        when(fileService.loadAuthorizedFile(1L, "f_1")).thenThrow(new FileAccessDeniedException("forbidden"));

        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler(nettyProperties, authService, null, fileService, fileStorageProperties));
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                "/files/f_1"
        );
        request.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer token-1");

        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.FORBIDDEN, response.status());
    }
}
