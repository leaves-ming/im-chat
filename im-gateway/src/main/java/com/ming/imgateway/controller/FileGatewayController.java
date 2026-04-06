package com.ming.imgateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imapicontract.common.ApiResponse;
import com.ming.imapicontract.file.CreateDownloadUrlRequest;
import com.ming.imapicontract.file.CreateDownloadUrlResponse;
import com.ming.imapicontract.file.StoreFileRequest;
import com.ming.imapicontract.file.StoreFileResponse;
import com.ming.imgateway.auth.AuthenticatedUser;
import com.ming.imgateway.auth.GatewayAuthAdapter;
import com.ming.imgateway.config.GatewayAccessProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 文件入口由 gateway 接管，对外保持稳定路径。
 */
@RestController
public class FileGatewayController {

    private static final ParameterizedTypeReference<ApiResponse<StoreFileResponse>> STORE_FILE_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<ApiResponse<CreateDownloadUrlResponse>> DOWNLOAD_URL_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;
    private final GatewayAuthAdapter gatewayAuthAdapter;
    private final GatewayAccessProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileGatewayController(WebClient gatewayWebClient,
                                 GatewayAuthAdapter gatewayAuthAdapter,
                                 GatewayAccessProperties properties) {
        this.webClient = gatewayWebClient;
        this.gatewayAuthAdapter = gatewayAuthAdapter;
        this.properties = properties;
    }

    @PostMapping(path = "/api/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<String>> upload(ServerWebExchange exchange) {
        return requireAuthenticatedUser(exchange)
                .flatMap(user -> exchange.getMultipartData()
                        .flatMap(parts -> resolveFilePart(parts)
                                .flatMap(filePart -> readFileBytes(filePart)
                                        .flatMap(bytes -> forwardUpload(exchange, user, filePart, bytes)))))
                .onErrorResume(UnauthorizedException.class,
                        ex -> Mono.just(jsonError(HttpStatus.UNAUTHORIZED, ex.getMessage(), traceId(exchange))))
                .onErrorResume(IllegalArgumentException.class,
                        ex -> Mono.just(jsonError(HttpStatus.BAD_REQUEST, ex.getMessage(), traceId(exchange))));
    }

    @PostMapping(path = "/api/file/download-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> createDownloadUrl(@RequestBody String body, ServerWebExchange exchange) {
        return requireAuthenticatedUser(exchange)
                .flatMap(user -> {
                    String fileId;
                    try {
                        fileId = objectMapper.readTree(body).path("fileId").asText(null);
                    } catch (Exception ex) {
                        return Mono.just(jsonError(HttpStatus.BAD_REQUEST, "bad request", traceId(exchange)));
                    }
                    if (!StringUtils.hasText(fileId)) {
                        return Mono.just(jsonError(HttpStatus.BAD_REQUEST, "fileId required", traceId(exchange)));
                    }
                    return webClient.post()
                            .uri("http://im-file-service/api/file/internal/download-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .headers(headers -> copyForwardHeaders(exchange, headers))
                            .bodyValue(new CreateDownloadUrlRequest(user.userId(), fileId))
                            .retrieve()
                            .bodyToMono(DOWNLOAD_URL_RESPONSE_TYPE)
                            .map(response -> mapDownloadUrlResponse(response, exchange))
                            .onErrorResume(ex -> Mono.just(jsonError(HttpStatus.BAD_REQUEST, "bad request", traceId(exchange))));
                })
                .onErrorResume(UnauthorizedException.class,
                        ex -> Mono.just(jsonError(HttpStatus.UNAUTHORIZED, ex.getMessage(), traceId(exchange))));
    }

    private Mono<AuthenticatedUser> requireAuthenticatedUser(ServerWebExchange exchange) {
        String token = gatewayAuthAdapter.resolveToken(exchange);
        if (!StringUtils.hasText(token)) {
            return Mono.error(new UnauthorizedException("unauthorized"));
        }
        return gatewayAuthAdapter.introspect(token)
                .switchIfEmpty(Mono.error(new UnauthorizedException("unauthorized")));
    }

    private Mono<FilePart> resolveFilePart(MultiValueMap<String, Part> parts) {
        for (Part part : parts.toSingleValueMap().values()) {
            if (part instanceof FilePart filePart) {
                return Mono.just(filePart);
            }
        }
        return Mono.error(new IllegalArgumentException("file part required"));
    }

    private Mono<byte[]> readFileBytes(FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    try {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                });
    }

    private Mono<ResponseEntity<String>> forwardUpload(ServerWebExchange exchange,
                                                       AuthenticatedUser user,
                                                       FilePart filePart,
                                                       byte[] bytes) {
        String contentType = filePart.headers().getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : filePart.headers().getContentType().toString();
        return webClient.post()
                .uri("http://im-file-service/api/file/internal/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> copyForwardHeaders(exchange, headers))
                .body(BodyInserters.fromValue(new StoreFileRequest(
                        user.userId(),
                        filePart.filename(),
                        contentType,
                        bytes.length,
                        bytes)))
                .retrieve()
                .bodyToMono(STORE_FILE_RESPONSE_TYPE)
                .map(response -> mapUploadResponse(response, exchange))
                .onErrorResume(ex -> Mono.just(jsonError(HttpStatus.BAD_REQUEST, "bad request", traceId(exchange))));
    }

    private ResponseEntity<String> mapUploadResponse(ApiResponse<StoreFileResponse> response, ServerWebExchange exchange) {
        if (response == null) {
            return jsonError(HttpStatus.SERVICE_UNAVAILABLE, "file storage unavailable", traceId(exchange));
        }
        if (!response.isSuccess() || response.getData() == null) {
            return mapFailure(response, exchange);
        }
        StoreFileResponse data = response.getData();
        ObjectNode body = successBody(exchange);
        ObjectNode payload = body.putObject("data");
        payload.put("uploadToken", data.uploadToken());
        payload.put("fileId", data.fileId());
        payload.put("fileName", data.fileName());
        payload.put("contentType", data.contentType());
        payload.put("size", data.size());
        payload.put("url", data.url());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body.toString());
    }

    private ResponseEntity<String> mapDownloadUrlResponse(ApiResponse<CreateDownloadUrlResponse> response, ServerWebExchange exchange) {
        if (response == null) {
            return jsonError(HttpStatus.SERVICE_UNAVAILABLE, "file storage unavailable", traceId(exchange));
        }
        if (!response.isSuccess() || response.getData() == null) {
            return mapFailure(response, exchange);
        }
        CreateDownloadUrlResponse data = response.getData();
        ObjectNode body = successBody(exchange);
        ObjectNode payload = body.putObject("data");
        payload.put("downloadUrl", data.downloadUrl());
        payload.put("expireAt", data.expireAt());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body.toString());
    }

    private ResponseEntity<String> mapFailure(ApiResponse<?> response, ServerWebExchange exchange) {
        String code = response == null ? null : response.getCode();
        String message = response == null ? null : response.getMessage();
        if ("FORBIDDEN".equals(code)) {
            return jsonError(HttpStatus.FORBIDDEN, "forbidden", traceId(exchange));
        }
        if ("NOT_FOUND".equals(code)) {
            return jsonError(HttpStatus.NOT_FOUND, "not found", traceId(exchange));
        }
        return jsonError(HttpStatus.BAD_REQUEST, StringUtils.hasText(message) ? message : "bad request", traceId(exchange));
    }

    private ObjectNode successBody(ServerWebExchange exchange) {
        ObjectNode resp = objectMapper.createObjectNode();
        resp.put("code", 0);
        resp.put("msg", "ok");
        String traceId = traceId(exchange);
        if (StringUtils.hasText(traceId)) {
            resp.put("traceId", traceId);
        }
        return resp;
    }

    private ResponseEntity<String> jsonError(HttpStatus status, String message, String traceId) {
        ObjectNode resp = objectMapper.createObjectNode();
        resp.put("code", status.value());
        resp.put("msg", message);
        if (StringUtils.hasText(traceId)) {
            resp.put("traceId", traceId);
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(resp.toString());
    }

    private void copyForwardHeaders(ServerWebExchange exchange, HttpHeaders downstreamHeaders) {
        HttpHeaders incomingHeaders = exchange.getRequest().getHeaders();
        String traceId = traceId(exchange);
        if (StringUtils.hasText(traceId)) {
            downstreamHeaders.set(properties.getTraceHeaderName(), traceId);
        }
        copyIfPresent(incomingHeaders, downstreamHeaders, HttpHeaders.AUTHORIZATION);
        copyIfPresent(incomingHeaders, downstreamHeaders, "X-Device-Id");
        copyIfPresent(incomingHeaders, downstreamHeaders, "X-Real-IP");
        copyIfPresent(incomingHeaders, downstreamHeaders, "X-Forwarded-For");
        copyIfPresent(incomingHeaders, downstreamHeaders, "X-Forwarded-Proto");
        copyIfPresent(incomingHeaders, downstreamHeaders, HttpHeaders.USER_AGENT);
    }

    private void copyIfPresent(HttpHeaders incomingHeaders, HttpHeaders downstreamHeaders, String headerName) {
        String value = incomingHeaders.getFirst(headerName);
        if (StringUtils.hasText(value)) {
            downstreamHeaders.set(headerName, value);
        }
    }

    private String traceId(ServerWebExchange exchange) {
        return exchange.getAttributeOrDefault("traceId", "");
    }

    private static final class UnauthorizedException extends RuntimeException {
        private UnauthorizedException(String message) {
            super(message);
        }
    }
}
