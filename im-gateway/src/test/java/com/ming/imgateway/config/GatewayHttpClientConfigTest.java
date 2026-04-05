package com.ming.imgateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayHttpClientConfigTest {

    private final GatewayHttpClientConfig config = new GatewayHttpClientConfig();

    @Test
    void gatewayWebClientShouldCloneBuilderAndApplyLoadBalancerFilterWhenAvailable() {
        WebClient.Builder builder = config.gatewayWebClientBuilder()
                .filter((request, next) -> next.exchange(ClientRequest.from(request)
                        .header("X-Base-Filter", "base")
                        .build()));

        ReactorLoadBalancerExchangeFilterFunction lbFunction = mock(ReactorLoadBalancerExchangeFilterFunction.class);
        doAnswer(invocation -> {
            ClientRequest request = invocation.getArgument(0);
            ExchangeFunction next = invocation.getArgument(1);
            return next.exchange(ClientRequest.from(request)
                    .header("X-Lb-Filter", "lb")
                    .build());
        }).when(lbFunction).filter(any(), any());

        ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(lbFunction);

        AtomicReference<ClientRequest> gatewayRequestRef = new AtomicReference<>();
        WebClient gatewayClient = config.gatewayWebClient(
                builder.exchangeFunction(captureRequest(gatewayRequestRef)),
                provider);
        gatewayClient.get().uri("http://im-user-service/api/auth/login").retrieve().toBodilessEntity().block();

        assertEquals("base", gatewayRequestRef.get().headers().getFirst("X-Base-Filter"));
        assertEquals("lb", gatewayRequestRef.get().headers().getFirst("X-Lb-Filter"));

        AtomicReference<ClientRequest> originalRequestRef = new AtomicReference<>();
        WebClient originalClient = builder.exchangeFunction(captureRequest(originalRequestRef)).build();
        originalClient.get().uri("http://im-user-service/api/auth/login").retrieve().toBodilessEntity().block();

        assertEquals("base", originalRequestRef.get().headers().getFirst("X-Base-Filter"));
        assertEquals(null, originalRequestRef.get().headers().getFirst("X-Lb-Filter"));
    }

    @Test
    void gatewayWebClientShouldWorkWithoutLoadBalancerFilter() {
        WebClient.Builder builder = config.gatewayWebClientBuilder();
        ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        AtomicReference<ClientRequest> requestRef = new AtomicReference<>();
        WebClient client = config.gatewayWebClient(builder.exchangeFunction(captureRequest(requestRef)), provider);
        client.get().uri("http://example.com/health").retrieve().toBodilessEntity().block();

        assertEquals("http://example.com/health", requestRef.get().url().toString());
    }

    private ExchangeFunction captureRequest(AtomicReference<ClientRequest> requestRef) {
        return request -> {
            requestRef.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .build());
        };
    }
}
