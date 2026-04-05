package com.ming.imuserservice.mapper;

import com.ming.imuserservice.dao.UserProfileDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户资料表访问接口。
 */
@Mapper
public interface ProfileMapper {

    @Select("SELECT user_id AS userId, nickname, avatar, sex, active_status AS activeStatus, last_online_at AS lastOnlineAt, last_offline_at AS lastOfflineAt, last_login_ip AS lastLoginIp, updated_at AS updatedAt FROM user_profile WHERE user_id = #{userId} LIMIT 1")
    UserProfileDO findByUserId(Long userId);
}
