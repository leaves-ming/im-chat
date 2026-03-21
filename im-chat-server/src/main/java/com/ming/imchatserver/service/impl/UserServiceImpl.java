package com.ming.imchatserver.service.impl;

import com.ming.imchatserver.dao.UserCoreDO;
import com.ming.imchatserver.dao.UserProfileDO;
import com.ming.imchatserver.mapper.ProfileMapper;
import com.ming.imchatserver.mapper.UserMapper;
import com.ming.imchatserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {


    private final UserMapper userMapper;

    private final ProfileMapper profileMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(UserMapper userMapper, ProfileMapper profileMapper) {
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
    }

    @Override
    public Optional<UserCoreDO> findByUsername(String username) {
        return Optional.ofNullable(userMapper.findByUsername(username));
    }

    @Override
    public Optional<UserCoreDO> findByUserId(Long userId) {
        return Optional.ofNullable(userMapper.findByUserId(userId));
    }

    @Override
    public Optional<UserProfileDO> findProfile(Long userId) {
        return Optional.ofNullable(profileMapper.findByUserId(userId));
    }

    @Override
    public boolean verifyPassword(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}

