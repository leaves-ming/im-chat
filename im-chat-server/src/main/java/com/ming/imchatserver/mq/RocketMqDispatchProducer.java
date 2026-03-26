package com.ming.imchatserver.mq;

import com.ming.imchatserver.config.ReliabilityProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 生产者实现。
 */
@Component
@ConditionalOnBean(RocketMQTemplate.class)
public class RocketMqDispatchProducer implements DispatchProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final ReliabilityProperties reliabilityProperties;

    public RocketMqDispatchProducer(RocketMQTemplate rocketMQTemplate,
                                    ReliabilityProperties reliabilityProperties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.reliabilityProperties = reliabilityProperties;
    }

    @Override
    public void sendSingleDispatch(DispatchMessagePayload payload) {
        String destination = reliabilityProperties.getDispatchTopic() + ":SINGLE";
        rocketMQTemplate.syncSend(destination,
                MessageBuilder.withPayload(payload)
                        .setHeader("KEYS", payload.getServerMsgId())
                        .build());
    }
}
