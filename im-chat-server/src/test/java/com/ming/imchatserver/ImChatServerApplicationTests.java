package com.ming.imchatserver;

import com.ming.imchatserver.mq.DispatchProducer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * ImChatServerApplicationTests - 代码模块说明。
 * <p>
 * 本类注释由统一规范补充，描述职责、边界与使用语义。
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration",
        "im.redisson.enabled=false"
})
class ImChatServerApplicationTests {

    @TestConfiguration
    static class TestBeans {
        @Bean
        DispatchProducer dispatchProducer() {
            return (tag, payload) -> {
            };
        }
    }

    @Test
    /**
     * 方法说明。
     */
    void contextLoads() {
    }

}
