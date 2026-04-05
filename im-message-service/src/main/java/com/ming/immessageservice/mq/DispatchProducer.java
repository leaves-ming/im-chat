package com.ming.immessageservice.mq;

/**
 * 分发消息生产者。
 */
public interface DispatchProducer {

    void sendDispatch(String tag, DispatchMessagePayload payload);
}
