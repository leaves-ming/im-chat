package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 用户资料实体（映射 user_profile 表）。
 */
@Data
public class UserProfileDO {
    /** 用户 ID（与 user_core 对应）。 */
    private Long userId;
    /** 昵称。 */
    private String nickname;
    /** 头像 URL。 */
    private String avatar;
    /** 性别。 */
    private Integer sex;
    /** 活跃状态。 */
    private Integer activeStatus;
    /** 最近上线时间。 */
    private Date lastOnlineAt;
    /** 最近下线时间。 */
    private Date lastOfflineAt;
    /** 最近登录 IP。 */
    private String lastLoginIp;
    /** 更新时间。 */
    private Date updatedAt;
}
