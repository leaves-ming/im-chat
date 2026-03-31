package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 单聊离线同步游标（映射 im_single_cursor）。
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
