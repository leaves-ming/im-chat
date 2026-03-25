package com.ming.imchatserver.service.impl;

import com.ming.imchatserver.dao.UserCoreDO;
import com.ming.imchatserver.dao.UserProfileDO;
import com.ming.imchatserver.mapper.ProfileMapper;
import com.ming.imchatserver.mapper.UserMapper;
import com.ming.imchatserver.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * {@link UserService} 的默认实现。
 * <p>
 * 负责从 Mapper 层读取用户核心信息/资料信息，并提供密码匹配能力。
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final ProfileMapper profileMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * @param userMapper    用户核心信息查询 Mapper
     * @param profileMapper 用户资料查询 Mapper
     */
    public UserServiceImpl(UserMapper userMapper, ProfileMapper profileMapper) {
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
    }

    /**
     * 通过用户名查找用户核心信息。
     */
    @Override
    public Optional<UserCoreDO> findByUsername(String username) {
        return Optional.ofNullable(userMapper.findByUsername(username));
    }

    /**
     * 通过用户 ID 查找用户核心信息。
     */
    @Override
    public Optional<UserCoreDO> findByUserId(Long userId) {
        return Optional.ofNullable(userMapper.findByUserId(userId));
    }

    /**
     * 通过用户 ID 查找用户资料信息。
     */
    @Override
    public Optional<UserProfileDO> findProfile(Long userId) {
        return Optional.ofNullable(profileMapper.findByUserId(userId));
    }

    /**
     * 使用 BCrypt 校验明文密码与哈希密码是否匹配。
     */
    @Override
    public boolean verifyPassword(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
