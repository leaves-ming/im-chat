package com.ming.imchatserver.mq;

import com.ming.imchatserver.config.ReliabilityProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 基于 RocketMQ 的分发生产者实现。
 *
 * <p>该组件负责将待分发的消息负载发送到可靠投递链路使用的 RocketMQ Topic，
 * 供后续消费者执行在线推送或其他分发处理。</p>
 *
 * <p>发送目标由 {@code dispatchTopic:tag} 组成：</p>
 * <p>1. Topic 来自 {@link ReliabilityProperties} 的配置。</p>
 * <p>2. Tag 由调用方传入；为空时默认使用单聊分发标签。</p>
 *
 * <p>同时会把 {@code serverMsgId} 写入 RocketMQ 消息键，便于消息追踪和问题排查。</p>
 */
@Component
@ConditionalOnBean(RocketMQTemplate.class)
public class RocketMqDispatchProducer implements DispatchProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final ReliabilityProperties reliabilityProperties;

    /**
     * @param rocketMQTemplate RocketMQ 发送模板
     * @param reliabilityProperties 可靠投递相关配置
     */
    public RocketMqDispatchProducer(RocketMQTemplate rocketMQTemplate,
                                    ReliabilityProperties reliabilityProperties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.reliabilityProperties = reliabilityProperties;
    }

    /**
     * 发送一条分发消息到 RocketMQ。
     *
     * <p>当调用方未显式指定 Tag 时，默认按单聊分发标签发送。</p>
     *
     * @param tag 分发标签，用于区分单聊、群聊或其他事件类型
     * @param payload 分发消息负载
     */
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
