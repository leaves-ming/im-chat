package com.ming.common.config;

import com.ming.common.exception.GlobalExceptionHandler;
import com.ming.common.feign.FeignTraceInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * im-common-starter自动装配配置类
 */
@Configuration
@ComponentScan(basePackages = "com.ming.common")
public class CommonAutoConfiguration {

    /**
     * Web环境下自动注册全局异常处理器
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * 存在Feign依赖时自动注册TraceId拦截器
     */
    @Bean
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    public FeignTraceInterceptor feignTraceInterceptor() {
        return new FeignTraceInterceptor();
    }
}
