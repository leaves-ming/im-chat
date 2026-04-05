package com.ming.imfileservice.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置。
 */
@Configuration
@MapperScan("com.ming.imfileservice.mapper")
public class MybatisConfig {
}
