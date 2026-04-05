package com.ming.imuserservice.service;

import com.ming.imapicontract.user.UserDTO;
import com.ming.imuserservice.dao.UserCoreDO;

import java.util.Optional;

/**
 * 用户域服务。
 */
public interface UserDomainService {

    Optional<UserCoreDO> findCoreByUsername(String username);

    Optional<UserDTO> findUserById(Long userId);

    Optional<UserDTO> findUserByUsername(String username);

    boolean verifyPassword(String rawPassword, String passwordHash);
}
