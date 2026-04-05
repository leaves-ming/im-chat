package com.ming.imuserservice.service.impl;

import com.ming.imapicontract.user.UserDTO;
import com.ming.imuserservice.dao.UserCoreDO;
import com.ming.imuserservice.dao.UserProfileDO;
import com.ming.imuserservice.mapper.ProfileMapper;
import com.ming.imuserservice.mapper.UserMapper;
import com.ming.imuserservice.service.UserDomainService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 用户域服务实现。
 */
@Service
public class UserDomainServiceImpl implements UserDomainService {

    private final UserMapper userMapper;
    private final ProfileMapper profileMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserDomainServiceImpl(UserMapper userMapper, ProfileMapper profileMapper) {
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
    }

    @Override
    public Optional<UserCoreDO> findCoreByUsername(String username) {
        return Optional.ofNullable(userMapper.findByUsername(username));
    }

    @Override
    public Optional<UserDTO> findUserById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return buildUser(userMapper.findByUserId(userId));
    }

    @Override
    public Optional<UserDTO> findUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return buildUser(userMapper.findByUsername(username));
    }

    @Override
    public boolean verifyPassword(String rawPassword, String passwordHash) {
        return rawPassword != null && passwordHash != null && passwordEncoder.matches(rawPassword, passwordHash);
    }

    private Optional<UserDTO> buildUser(UserCoreDO core) {
        if (core == null) {
            return Optional.empty();
        }
        UserProfileDO profile = profileMapper.findByUserId(core.getUserId());
        return Optional.of(new UserDTO(
                core.getUserId(),
                core.getAccountNo(),
                core.getUsername(),
                profile == null ? null : profile.getNickname(),
                profile == null ? null : profile.getAvatar(),
                profile == null ? null : profile.getSex(),
                profile == null ? null : profile.getActiveStatus(),
                profile == null ? null : profile.getLastLoginIp()
        ));
    }
}
