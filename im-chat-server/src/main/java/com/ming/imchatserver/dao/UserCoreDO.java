package com.ming.imchatserver.dao;

import lombok.Data;

import java.util.Date;

/**
 * 用户核心信息实体（映射 user_core 表）。
 * @author ming
 */
@Data
public class UserCoreDO {
    /** 用户 ID（主键）。 */
    private Long userId;
    /** 对外账号编号。 */
    private String accountNo;
    /** 用户名（登录标识）。 */
    private String username;
    /** 密码哈希。 */
    private String passwordHash;
    /** 创建时间。 */
    private Date createdAt;
    /** 更新时间。 */
    private Date updatedAt;
}
