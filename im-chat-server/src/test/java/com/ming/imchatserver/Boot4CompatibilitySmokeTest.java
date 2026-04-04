package com.ming.imchatserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.imchatserver.config.RedissonConfig;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot 4.0.3 兼容性冒烟测试。
 */
class Boot4CompatibilitySmokeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void jacksonRoundTripSmoke() throws Exception {
        ProbePayload payload = new ProbePayload(1L, "boot4");
        String json = objectMapper.writeValueAsString(payload);
        ProbePayload parsed = objectMapper.readValue(json, ProbePayload.class);
        assertThat(parsed.id()).isEqualTo(1L);
        assertThat(parsed.name()).isEqualTo("boot4");
    }

    @Test
    void mybatisBasicQuerySmoke() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:boot4_compat;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.addMapper(ProbeMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = factory.openSession()) {
            Integer result = session.getMapper(ProbeMapper.class).selectOne();
            assertThat(result).isEqualTo(1);
        }
    }

    @Test
    void redissonClientInitSmoke() {
        RedissonConfig config = new RedissonConfig();
        RedissonClient client = config.redissonClient("redis://127.0.0.1:6379", "");
        assertThat(client).isNotNull();
        client.shutdown();
    }

    @Test
    void rocketMqTemplateAutoConfigSmoke() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RocketMQAutoConfiguration.class))
                .withPropertyValues(
                        "spring.main.web-application-type=none",
                        "rocketmq.name-server=127.0.0.1:9876",
                        "rocketmq.producer.group=boot4-compat-producer")
                .run(context -> assertThat(context).hasSingleBean(RocketMQTemplate.class));
    }

    interface ProbeMapper {
        @Select("SELECT 1")
        Integer selectOne();
    }

    record ProbePayload(Long id, String name) {
    }
}
