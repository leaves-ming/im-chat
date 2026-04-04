package com.ming.imchatserver.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * 消息送达状态表访问接口。
 */
@Mapper
public interface DeliveryMapper {

    @Insert("""
            INSERT INTO im_delivery (message_id, user_id, delivered_at, read_at)
            VALUES (#{messageId}, #{userId}, #{deliveredAt}, #{readAt})
            ON DUPLICATE KEY UPDATE
              delivered_at = COALESCE(im_delivery.delivered_at, VALUES(delivered_at)),
              read_at = COALESCE(im_delivery.read_at, VALUES(read_at)),
              updated_at = NOW()
            """)
    void upsertAck(@Param("messageId") Long messageId,
                  @Param("userId") Long userId,
                  @Param("deliveredAt") Date deliveredAt,
                  @Param("readAt") Date readAt);
}
