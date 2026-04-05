package com.ming.immessageservice.mq;

import lombok.Data;

/**
 * 分发消息载荷。
 */
@Data
public class DispatchMessagePayload {
    public static final String EVENT_TYPE_MESSAGE = "MESSAGE";
    public static final String EVENT_TYPE_RECALL = "RECALL";
    public static final String EVENT_TYPE_STATUS_NOTIFY = "STATUS_NOTIFY";
    public static final String TAG_SINGLE = "SINGLE";
    public static final String TAG_GROUP = "GROUP";

    private String eventId;
    private String eventType;
    private String originServerId;
    private String serverMsgId;
    private String clientMsgId;
    private Long fromUserId;
    private Long toUserId;
    private Long notifyUserId;
    private Long groupId;
    private Long seq;
    private String content;
    private String msgType;
    private String status;
    private String createdAt;
    private String retractedAt;
    private Long retractedBy;
}
