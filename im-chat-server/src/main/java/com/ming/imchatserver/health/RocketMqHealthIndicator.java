package com.ming.imchatserver.health;

import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 基础装配健康检查。
 */
@Component("rocketmq")
public class RocketMqHealthIndicator implements HealthIndicator {

    private final ObjectProvider<RocketMQTemplate> templateProvider;
    private final ObjectProvider<RocketMQProperties> propertiesProvider;

    public RocketMqHealthIndicator(ObjectProvider<RocketMQTemplate> templateProvider,
                                   ObjectProvider<RocketMQProperties> propertiesProvider) {
        this.templateProvider = templateProvider;
        this.propertiesProvider = propertiesProvider;
    }

    @Override
    public Health health() {
        RocketMQTemplate template = templateProvider.getIfAvailable();
        RocketMQProperties properties = propertiesProvider.getIfAvailable();
        if (template == null || properties == null) {
            return Health.down()
                    .withDetail("reason", "rocketmq bean missing")
                    .build();
        }
        String nameServer = properties.getNameServer();
        if (nameServer == null || nameServer.isBlank()) {
            return Health.down()
                    .withDetail("reason", "rocketmq.name-server missing")
                    .build();
        }
        return Health.up()
                .withDetail("nameServer", nameServer)
                .withDetail("producerConfigured", template.getProducer() != null)
                .build();
    }
}
