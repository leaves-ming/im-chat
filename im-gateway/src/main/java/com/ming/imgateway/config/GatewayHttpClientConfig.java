package com.ming.imgateway.config;

import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 下游服务 HTTP 客户端配置。
 */
@Configuration
public class GatewayHttpClientConfig {

    @Bean
    public WebClient.Builder gatewayWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public WebClient gatewayWebClient(WebClient.Builder builder,
                                      ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> lbFunctionProvider) {
        ReactorLoadBalancerExchangeFilterFunction lbFunction = lbFunctionProvider.getIfAvailable();
        if (lbFunction != null) {
            builder.filter(lbFunction);
        }
        return builder.build();
    }
}
