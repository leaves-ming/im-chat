package com.ming.imchatserver.mq;

import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 群聊分发消费者：消费 MQ 后推送当前节点上的在线成员。
 */
@Component
@RocketMQMessageListener(
        topic = "${im.reliability.dispatch-topic:im.msg.dispatch}",
        consumerGroup = "${rocketmq.consumer.group:im-chat-consumer-group}",
        selectorExpression = "GROUP",
        messageModel = MessageModel.BROADCASTING
)
public class DispatchGroupConsumer implements RocketMQListener<DispatchMessagePayload> {

    private static final Logger logger = LoggerFactory.getLogger(DispatchGroupConsumer.class);

    private final DispatchPushService dispatchPushService;

    public DispatchGroupConsumer(DispatchPushService dispatchPushService) {
        this.dispatchPushService = dispatchPushService;
    }

    @Override
    public void onMessage(DispatchMessagePayload payload) {
        try {
            dispatchPushService.dispatchGroup(payload);
        } catch (Exception ex) {
            logger.error("dispatch group consume failed serverMsgId={} groupId={}",
                    payload.getServerMsgId(), payload.getGroupId(), ex);
            throw new IllegalStateException("dispatch group consume failed", ex);
        }
    }
}
