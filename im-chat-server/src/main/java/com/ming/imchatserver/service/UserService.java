package com.ming.imchatserver.service;

import com.ming.imchatserver.dao.UserCoreDO;
import com.ming.imchatserver.dao.UserProfileDO;

import java.util.Optional;

public interface UserService {
    Optional<UserCoreDO> findByUsername(String username);
    Optional<UserCoreDO> findByUserId(Long userId);
    Optional<UserProfileDO> findProfile(Long userId);
    boolean verifyPassword(String rawPassword, String passwordHash);
}
