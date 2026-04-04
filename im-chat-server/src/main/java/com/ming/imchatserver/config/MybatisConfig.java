package com.ming.imchatserver.config;

import com.github.pagehelper.PageInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * MyBatis 配置类。
 * <p>
 * 负责：
 * - Mapper 扫描；
 * - SqlSessionFactory/SqlSessionTemplate 创建；
 * - PageHelper 分页拦截器注册。
 */
@Configuration
@MapperScan(basePackages = "com.ming.imchatserver.mapper", sqlSessionTemplateRef = "sqlSessionTemplate")
public class MybatisConfig {

    /**
     * 创建 SqlSessionFactory。
     *
     * @param dataSource 数据源
     * @return SqlSessionFactory 实例
     * @throws Exception 初始化异常
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setTypeAliasesPackage("com.ming.imchatserver.dao");
        //绑定数据源
        factoryBean.setPlugins(pageInterceptor());
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath*:/mapper/**/*.xml")
        );
        return factoryBean.getObject();
    }

    /**
     * 创建 线程安全的SqlSessionTemplate。
     */
    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    /**
     * 注册 PageHelper 拦截器（MySQL 方言）。
     */
    @Bean
    public PageInterceptor pageInterceptor() {
        PageInterceptor interceptor = new PageInterceptor();
        Properties properties = new Properties();
        properties.setProperty("helperDialect", "mysql");
        properties.setProperty("reasonable", "true");
        properties.setProperty("supportMethodsArguments", "true");
        interceptor.setProperties(properties);
        return interceptor;
    }
}
