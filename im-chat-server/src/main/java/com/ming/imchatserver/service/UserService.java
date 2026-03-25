package com.ming.imchatserver.service;

import com.ming.imchatserver.dao.UserCoreDO;
import com.ming.imchatserver.dao.UserProfileDO;

import java.util.Optional;

/**
 * 用户领域服务接口。
 * <p>
 * 对外提供用户基础信息查询与密码校验能力，供登录鉴权和用户资料展示使用。
 */
public interface UserService {

    /**
     * 根据用户名查询用户核心信息。
     *
     * @param username 用户名
     * @return 命中时返回用户核心信息，否则返回空
     */
    Optional<UserCoreDO> findByUsername(String username);

    /**
     * 根据用户 ID 查询用户核心信息。
     *
     * @param userId 用户 ID
     * @return 命中时返回用户核心信息，否则返回空
     */
    Optional<UserCoreDO> findByUserId(Long userId);

    /**
     * 根据用户 ID 查询用户资料信息。
     *
     * @param userId 用户 ID
     * @return 命中时返回用户资料，否则返回空
     */
    Optional<UserProfileDO> findProfile(Long userId);

    /**
     * 校验明文密码与加密密码是否匹配。
     *
     * @param rawPassword  明文密码
     * @param passwordHash 数据库存储的哈希密码
     * @return 匹配返回 true，否则 false
     */
    boolean verifyPassword(String rawPassword, String passwordHash);
}
