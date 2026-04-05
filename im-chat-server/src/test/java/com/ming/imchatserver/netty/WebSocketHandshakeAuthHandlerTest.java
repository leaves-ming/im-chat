package com.ming.imchatserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.config.NettyProperties;
import com.ming.imchatserver.config.ObservabilityProperties;
import com.ming.imchatserver.observability.RuntimeObservabilitySettings;
import com.ming.imchatserver.service.AuthService;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketHandshakeAuthHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void publicWebsocketShouldRejectDirectAccessWhenDisabled() {
        NettyProperties properties = new NettyProperties();
        properties.setPublicWebsocketDirectAccessEnabled(false);
        AuthService authService = mock(AuthService.class);

        EmbeddedChannel channel = new EmbeddedChannel(
                new WebSocketHandshakeAuthHandler(properties, authService, mock(ChannelUserManager.class),
                        new RuntimeObservabilitySettings(new ObservabilityProperties()))
        );
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ws");
        request.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer token-1");

        channel.writeInbound(request);

        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.FORBIDDEN, response.status());
        JsonNode payload = readJson(response);
        assertEquals(403, payload.get("code").asInt());
        assertEquals("connect via gateway", payload.get("msg").asText());
    }

    @Test
    void internalWebsocketShouldAcceptTrustedGatewayRequest() {
        NettyProperties properties = new NettyProperties();
        properties.setPublicWebsocketDirectAccessEnabled(false);
        AuthService authService = mock(AuthService.class);
        AuthService.AuthUser authUser = new AuthService.AuthUser();
        authUser.userId = 7L;
        authUser.username = "alice";
        when(authService.parseToken("token-1")).thenReturn(Optional.of(authUser));

        EmbeddedChannel channel = new EmbeddedChannel(
                new WebSocketHandshakeAuthHandler(properties, authService, mock(ChannelUserManager.class),
                        new RuntimeObservabilitySettings(new ObservabilityProperties()))
        );
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ws-internal?token=token-1");
        request.headers().set(properties.getTrustedGatewayHeaderName(), properties.getTrustedGatewayHeaderValue());
        request.headers().set(properties.getTrustedGatewaySecretHeaderName(), properties.getTrustedGatewaySecret());
        request.headers().set("X-Device-Id", "device-a");
        request.headers().set(properties.getClientIpHeaderName(), "10.0.0.8");

        channel.writeInbound(request);

        DefaultFullHttpRequest forwarded = channel.readInbound();
        assertSame(request, forwarded);
        assertEquals("/ws", forwarded.uri());
        assertEquals(7L, channel.attr(NettyAttr.USER_ID).get());
        assertEquals(Boolean.TRUE, channel.attr(NettyAttr.AUTH_OK).get());
        assertEquals("device-a", channel.attr(NettyAttr.DEVICE_ID).get());
    }

    @Test
    void internalWebsocketShouldRejectMissingGatewayHeaders() {
        NettyProperties properties = new NettyProperties();
        AuthService authService = mock(AuthService.class);

        EmbeddedChannel channel = new EmbeddedChannel(
                new WebSocketHandshakeAuthHandler(properties, authService, mock(ChannelUserManager.class),
                        new RuntimeObservabilitySettings(new ObservabilityProperties()))
        );
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ws-internal?token=token-1");

        channel.writeInbound(request);

        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.FORBIDDEN, response.status());
    }

    private JsonNode readJson(FullHttpResponse response) {
        try {
            return mapper.readTree(response.content().toString(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
