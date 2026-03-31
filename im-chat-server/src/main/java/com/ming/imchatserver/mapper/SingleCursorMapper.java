package com.ming.imchatserver.mapper;

import com.ming.imchatserver.dao.SingleCursorDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;

/**
 * 单聊同步游标表（im_single_cursor）访问接口。
 * 当前按 userId + deviceId 维度存储，多设备独立 checkpoint。
 */
@Mapper
public interface SingleCursorMapper {

    @Select("""
            SELECT id,
                   user_id AS userId,
                   device_id AS deviceId,
                   last_pull_created_at AS lastPullCreatedAt,
                   last_pull_message_id AS lastPullMessageId,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM im_single_cursor
            WHERE user_id = #{userId}
              AND device_id = #{deviceId}
            LIMIT 1
            """)
    SingleCursorDO findByUserIdAndDeviceId(@Param("userId") Long userId,
                                           @Param("deviceId") String deviceId);

    @Insert("""
            INSERT INTO im_single_cursor
            (user_id, device_id, last_pull_created_at, last_pull_message_id)
            VALUES
            (#{userId}, #{deviceId}, #{lastPullCreatedAt}, #{lastPullMessageId})
            ON DUPLICATE KEY UPDATE
              last_pull_created_at = CASE
                WHEN last_pull_created_at IS NULL THEN VALUES(last_pull_created_at)
                WHEN VALUES(last_pull_created_at) IS NULL THEN last_pull_created_at
                WHEN VALUES(last_pull_created_at) > last_pull_created_at THEN VALUES(last_pull_created_at)
                WHEN VALUES(last_pull_created_at) = last_pull_created_at
                  AND VALUES(last_pull_message_id) > last_pull_message_id THEN VALUES(last_pull_created_at)
                ELSE last_pull_created_at
              END,
              last_pull_message_id = CASE
                WHEN last_pull_created_at IS NULL THEN VALUES(last_pull_message_id)
                WHEN VALUES(last_pull_created_at) IS NULL THEN last_pull_message_id
                WHEN VALUES(last_pull_created_at) > last_pull_created_at THEN VALUES(last_pull_message_id)
                WHEN VALUES(last_pull_created_at) = last_pull_created_at
                  AND VALUES(last_pull_message_id) > last_pull_message_id THEN VALUES(last_pull_message_id)
                ELSE last_pull_message_id
              END,
              updated_at = NOW()
            """)
    int upsertLastPullCursor(@Param("userId") Long userId,
                             @Param("deviceId") String deviceId,
                             @Param("lastPullCreatedAt") Date lastPullCreatedAt,
                             @Param("lastPullMessageId") Long lastPullMessageId);
}
