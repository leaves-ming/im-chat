package com.ming.immessageservice.mq;

import com.ming.immessageservice.config.ReliabilityProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 分发生产者。
 */
@Component
public class RocketMqDispatchProducer implements DispatchProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final ReliabilityProperties reliabilityProperties;

    public RocketMqDispatchProducer(RocketMQTemplate rocketMQTemplate,
                                    ReliabilityProperties reliabilityProperties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.reliabilityProperties = reliabilityProperties;
    }

    @Override
    public void sendDispatch(String tag, DispatchMessagePayload payload) {
        String resolvedTag = (tag == null || tag.isBlank()) ? DispatchMessagePayload.TAG_SINGLE : tag;
        String destination = reliabilityProperties.getDispatchTopic() + ":" + resolvedTag;
        rocketMQTemplate.syncSend(destination,
                MessageBuilder.withPayload(payload)
                        .setHeader("KEYS", payload.getServerMsgId())
                        .build());
    }
}
