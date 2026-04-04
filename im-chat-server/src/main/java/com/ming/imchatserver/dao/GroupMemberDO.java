package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 群成员实体（映射 im_group_member）。
 * @author ming
 */
@Data
public class GroupMemberDO {
    /** 主键 ID。 */
    private Long id;
    /** 群 ID。 */
    private Long groupId;
    /** 成员用户 ID。 */
    private Long userId;
    /** 群角色。 */
    private Integer role;
    /** 成员状态。 */
    private Integer memberStatus;
    /** 入群时间。 */
    private Date joinedAt;
    /** 禁言到期时间。 */
    private Date mutedUntil;
    /** 创建时间。 */
    private Date createdAt;
    /** 更新时间。 */
    private Date updatedAt;
}
