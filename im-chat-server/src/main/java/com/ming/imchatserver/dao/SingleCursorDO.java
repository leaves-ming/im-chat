package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 单聊离线同步游标（映射 im_single_cursor）。
 * @author ming
 */
@Data
public class SingleCursorDO {
    /** 主键 ID。 */
    private Long id;
    /** 用户 ID。 */
    private Long userId;
    /** 设备 ID。 */
    private String deviceId;
    /** 上次拉取消息创建时间。 */
    private Date lastPullCreatedAt;
    /** 上次拉取消息 ID。 */
    private Long lastPullMessageId;
    /** 创建时间。 */
    private Date createdAt;
    /** 更新时间。 */
    private Date updatedAt;
}
