package com.ming.imuserservice.dao;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户资料信息。
 */
@Data
public class UserProfileDO {
    private Long userId;
    private String nickname;
    private String avatar;
    private Integer sex;
    private Integer activeStatus;
    private LocalDateTime lastOnlineAt;
    private LocalDateTime lastOfflineAt;
    private String lastLoginIp;
    private LocalDateTime updatedAt;
}
