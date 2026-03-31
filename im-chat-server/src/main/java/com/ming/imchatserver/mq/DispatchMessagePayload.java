package com.ming.imchatserver.mq;

import lombok.Data;

/**
 * 分发到 MQ 的消息载荷。
 */
@Data
public class DispatchMessagePayload {
    public static final String EVENT_TYPE_MESSAGE = "MESSAGE";
    public static final String EVENT_TYPE_RECALL = "RECALL";

    private String eventId;
    private String eventType;
    private String serverMsgId;
    private String clientMsgId;
    private Long fromUserId;
    private Long toUserId;
    private String content;
    private String msgType;
    private String status;
    private String retractedAt;
    private Long retractedBy;
}
