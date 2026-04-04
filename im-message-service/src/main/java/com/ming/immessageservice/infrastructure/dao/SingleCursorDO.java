package com.ming.immessageservice.infrastructure.dao;

import lombok.Data;

import java.util.Date;

/**
 * 单聊游标表映射。
 */
@Data
public class SingleCursorDO {
    private Long id;
    private Long userId;
    private String deviceId;
    private Date lastPullCreatedAt;
    private Long lastPullMessageId;
    private Date createdAt;
    private Date updatedAt;
}
