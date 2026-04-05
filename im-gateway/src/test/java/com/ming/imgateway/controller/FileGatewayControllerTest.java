package com.ming.imgateway.controller;

import com.ming.imgateway.auth.AuthenticatedUser;
import com.ming.imgateway.auth.GatewayAuthAdapter;
import com.ming.imgateway.config.GatewayAccessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileGatewayControllerTest {

    @Test
    void uploadShouldReturnUnauthorizedWhenTokenMissing() {
        GatewayAuthAdapter gatewayAuthAdapter = mock(GatewayAuthAdapter.class);
        GatewayAccessProperties properties = new GatewayAccessProperties();
        FileGatewayController controller = new FileGatewayController(mockWebClient(""), gatewayAuthAdapter, properties);

        WebTestClient.bindToController(controller).build()
                .post()
                .uri("/api/file/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(401);
    }

    @Test
    void uploadShouldProxyToFileService() {
        GatewayAuthAdapter gatewayAuthAdapter = mock(GatewayAuthAdapter.class);
        when(gatewayAuthAdapter.resolveToken(any())).thenReturn("token-1");
        when(gatewayAuthAdapter.introspect("token-1"))
                .thenReturn(Mono.just(new AuthenticatedUser(1L, "alice", "token-1")));

        String body = """
                {"success":true,"code":"OK","data":{"uploadToken":"up-1","fileId":"f_1","fileName":"note.txt","contentType":"text/plain","size":5,"url":"/files/f_1"}}
                """;
        FileGatewayController controller = new FileGatewayController(mockWebClient(body), gatewayAuthAdapter, new GatewayAccessProperties());

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", "hello".getBytes())
                .filename("note.txt")
                .contentType(MediaType.TEXT_PLAIN);

        WebTestClient.bindToController(controller).build()
                .post()
                .uri("/api/file/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.uploadToken").isEqualTo("up-1")
                .jsonPath("$.data.fileId").isEqualTo("f_1");
    }

    @Test
    void downloadUrlShouldRejectWhenFileIdMissing() {
        GatewayAuthAdapter gatewayAuthAdapter = mock(GatewayAuthAdapter.class);
        when(gatewayAuthAdapter.resolveToken(any())).thenReturn("token-1");
        when(gatewayAuthAdapter.introspect("token-1"))
                .thenReturn(Mono.just(new AuthenticatedUser(1L, "alice", "token-1")));

        FileGatewayController controller = new FileGatewayController(mockWebClient(""), gatewayAuthAdapter, new GatewayAccessProperties());

        WebTestClient.bindToController(controller).build()
                .post()
                .uri("/api/file/download-url")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.msg").isEqualTo("fileId required");
    }

    @Test
    void downloadUrlShouldProxyToFileService() {
        GatewayAuthAdapter gatewayAuthAdapter = mock(GatewayAuthAdapter.class);
        when(gatewayAuthAdapter.resolveToken(any())).thenReturn("token-1");
        when(gatewayAuthAdapter.introspect("token-1"))
                .thenReturn(Mono.just(new AuthenticatedUser(1L, "alice", "token-1")));

        String body = """
                {"success":true,"code":"OK","data":{"downloadUrl":"/files/download?fileId=f_1&exp=123&sig=abc","expireAt":123}}
                """;
        FileGatewayController controller = new FileGatewayController(mockWebClient(body), gatewayAuthAdapter, new GatewayAccessProperties());

        WebTestClient.bindToController(controller).build()
                .post()
                .uri("/api/file/download-url")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fileId\":\"f_1\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.downloadUrl").isEqualTo("/files/download?fileId=f_1&exp=123&sig=abc")
                .jsonPath("$.data.expireAt").isEqualTo(123);
    }

    private WebClient mockWebClient(String body) {
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
        return WebClient.builder().exchangeFunction(exchangeFunction).build();
    }
}
