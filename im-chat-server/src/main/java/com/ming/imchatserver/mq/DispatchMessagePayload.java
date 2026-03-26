package com.ming.imchatserver.mq;

import lombok.Data;

/**
 * 分发到 MQ 的消息载荷。
 */
@Data
public class DispatchMessagePayload {
    private String eventId;
    private String serverMsgId;
    private String clientMsgId;
    private Long fromUserId;
    private Long toUserId;
    private String content;
    private String msgType;
}
