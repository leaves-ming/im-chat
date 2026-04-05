package com.ming.imgateway.config;

import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 下游服务 HTTP 客户端配置。
 */
@Configuration
public class GatewayHttpClientConfig {

    @Bean
    @ConditionalOnMissingBean(WebClient.Builder.class)
    public WebClient.Builder gatewayWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public WebClient gatewayWebClient(WebClient.Builder builder,
                                      ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> lbFunctionProvider) {
        WebClient.Builder targetBuilder = builder.clone();
        ReactorLoadBalancerExchangeFilterFunction lbFunction = lbFunctionProvider.getIfAvailable();
        if (lbFunction != null) {
            targetBuilder.filter(lbFunction);
        }
        return targetBuilder.build();
    }
}
