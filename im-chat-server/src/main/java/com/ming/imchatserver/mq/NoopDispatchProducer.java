package com.ming.imchatserver.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * MQ 不可用时的降级生产者。
 */
@Component
@ConditionalOnMissingBean(DispatchProducer.class)
public class NoopDispatchProducer implements DispatchProducer {

    private static final Logger logger = LoggerFactory.getLogger(NoopDispatchProducer.class);

    @Override
    public void sendDispatch(String tag, DispatchMessagePayload payload) {
        logger.warn("DispatchProducer fallback active, skip mq send. tag={} serverMsgId={} clientMsgId={}",
                tag, payload.getServerMsgId(), payload.getClientMsgId());
    }
}
