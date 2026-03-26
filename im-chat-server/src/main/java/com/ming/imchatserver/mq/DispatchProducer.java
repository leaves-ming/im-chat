package com.ming.imchatserver.mq;

/**
 * MQ 分发生产者。
 */
public interface DispatchProducer {

    void sendSingleDispatch(DispatchMessagePayload payload);
}
