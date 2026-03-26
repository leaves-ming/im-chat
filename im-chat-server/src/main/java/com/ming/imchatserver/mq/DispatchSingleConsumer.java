package com.ming.imchatserver.mq;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 单聊分发消费者：消费 MQ 后推送在线用户。
 */
@Component
@RocketMQMessageListener(
        topic = "${im.reliability.dispatch-topic:im.msg.dispatch}",
        consumerGroup = "${rocketmq.consumer.group:im-chat-consumer-group}",
        selectorExpression = "SINGLE"
)
public class DispatchSingleConsumer implements RocketMQListener<DispatchMessagePayload> {

    private static final Logger logger = LoggerFactory.getLogger(DispatchSingleConsumer.class);

    private final DispatchPushService dispatchPushService;

    public DispatchSingleConsumer(DispatchPushService dispatchPushService) {
        this.dispatchPushService = dispatchPushService;
    }

    @Override
    public void onMessage(DispatchMessagePayload payload) {
        try {
            dispatchPushService.dispatchSingle(payload);
        } catch (Exception ex) {
            logger.error("dispatch single consume failed serverMsgId={} clientMsgId={}",
                    payload.getServerMsgId(), payload.getClientMsgId(), ex);
            throw new IllegalStateException("dispatch single consume failed", ex);
        }
    }
}
