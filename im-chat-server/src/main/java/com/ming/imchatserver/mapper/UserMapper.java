package com.ming.imchatserver.mapper;

import com.ming.imchatserver.dao.UserCoreDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户核心表（user_core）访问接口。
 * <p>
 * 主要提供登录鉴权和用户基础信息查询所需的数据读取能力。
 */
@Mapper
public interface UserMapper {

    /**
     * 根据用户名查询用户核心信息。
     *
     * @param username 用户名（唯一）
     * @return 命中返回用户核心信息；未命中返回 null
     */
    @Select("SELECT user_id AS userId, account_no AS accountNo, username, password_hash AS passwordHash, created_at AS createdAt, updated_at AS updatedAt FROM user_core WHERE username = #{username} LIMIT 1")
    UserCoreDO findByUsername(String username);

    /**
     * 根据用户 ID 查询用户核心信息。
     *
     * @param userId 用户 ID（主键）
     * @return 命中返回用户核心信息；未命中返回 null
     */
    @Select("SELECT user_id AS userId, account_no AS accountNo, username, password_hash AS passwordHash, created_at AS createdAt, updated_at AS updatedAt FROM user_core WHERE user_id = #{userId} LIMIT 1")
    UserCoreDO findByUserId(Long userId);
}
