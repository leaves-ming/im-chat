package com.ming.imchatserver.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Date;

/**
 * 投递回执表访问接口。
 * <p>
 * 当前仅保留测试兼容与历史数据更新能力。
 */
@Mapper
public interface DeliveryMapper {

    @Update("""
            INSERT INTO im_message_delivery
            (message_id, user_id, delivered_at, acked_at)
            VALUES
            (#{messageId}, #{userId}, #{deliveredAt}, #{ackedAt})
            ON DUPLICATE KEY UPDATE
              delivered_at = COALESCE(VALUES(delivered_at), delivered_at),
              acked_at = COALESCE(VALUES(acked_at), acked_at),
              updated_at = NOW()
            """)
    int upsertAck(@Param("messageId") Long messageId,
                  @Param("userId") Long userId,
                  @Param("deliveredAt") Date deliveredAt,
                  @Param("ackedAt") Date ackedAt);
}
