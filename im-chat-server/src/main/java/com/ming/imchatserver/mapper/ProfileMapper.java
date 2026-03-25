package com.ming.imchatserver.mapper;

import com.ming.imchatserver.dao.UserProfileDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户资料表（user_profile）访问接口。
 * <p>
 * 提供昵称、头像、在线状态等资料信息读取能力。
 */
@Mapper
public interface ProfileMapper {

    /**
     * 根据用户 ID 查询用户资料。
     *
     * @param userId 用户 ID（与 user_core.user_id 一一对应）
     * @return 命中返回用户资料；未命中返回 null
     */
    @Select("SELECT user_id AS userId, nickname, avatar, sex, active_status AS activeStatus, last_online_at AS lastOnlineAt, last_offline_at AS lastOfflineAt, last_login_ip AS lastLoginIp, updated_at AS updatedAt FROM user_profile WHERE user_id = #{userId} LIMIT 1")
    UserProfileDO findByUserId(Long userId);
}
