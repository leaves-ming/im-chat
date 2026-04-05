package com.ming.imuserservice.mapper;

import com.ming.imuserservice.dao.UserCoreDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户核心表访问接口。
 */
@Mapper
public interface UserMapper {

    @Select("SELECT user_id AS userId, account_no AS accountNo, username, password_hash AS passwordHash, created_at AS createdAt, updated_at AS updatedAt FROM user_core WHERE username = #{username} LIMIT 1")
    UserCoreDO findByUsername(String username);

    @Select("SELECT user_id AS userId, account_no AS accountNo, username, password_hash AS passwordHash, created_at AS createdAt, updated_at AS updatedAt FROM user_core WHERE user_id = #{userId} LIMIT 1")
    UserCoreDO findByUserId(Long userId);
}
