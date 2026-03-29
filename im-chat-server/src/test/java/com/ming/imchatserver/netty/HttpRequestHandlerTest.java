package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.config.FileStorageProperties;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.file.FileStorageService;
import com.ming.imchatserver.service.AuthService;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
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
        FileStorageService fileStorageService = new com.ming.imchatserver.file.LocalFileStorageService(fileStorageProperties);

        AuthService.AuthUser authUser = new AuthService.AuthUser();
        authUser.userId = 1L;
        authUser.username = "alice";
        when(authService.verifyToken("token-1")).thenReturn(true);
        when(authService.parseToken("token-1")).thenReturn(Optional.of(authUser));

        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler(nettyProperties, authService, null, fileStorageService, fileStorageProperties));

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
        FileStorageService fileStorageService = new com.ming.imchatserver.file.LocalFileStorageService(fileStorageProperties);

        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler(nettyProperties, authService, null, fileStorageService, fileStorageProperties));
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
}
