package com.ming.imuserservice.dao;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户核心信息。
 */
@Data
public class UserCoreDO {
    private Long userId;
    private String accountNo;
    private String username;
    private String passwordHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
