package com.ming.imchatserver.mapper;

import com.ming.imchatserver.dao.OutboxMessageDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/**
 * 本地消息表Mapper。
 */
@Mapper
public interface OutboxMapper {

    @Insert("""
            INSERT INTO outbox_message
            (event_id, message_id, client_msg_id, from_user_id, topic, tag, payload, status, ack_status, retry_count, max_retry_count, next_retry_at, processing_at, sent_at, fail_reason, created_at, updated_at)
            VALUES
            (#{eventId}, #{messageId}, #{clientMsgId}, #{fromUserId}, #{topic}, #{tag}, #{payload}, #{status}, #{ackStatus}, #{retryCount}, #{maxRetryCount}, #{nextRetryAt}, #{processingAt}, #{sentAt}, #{failReason}, #{createdAt}, #{updatedAt})
            """)
    int insert(OutboxMessageDO outbox);

    @Update("""
            UPDATE outbox_message
            SET message_id = #{messageId},
                status = #{status},
                ack_status = #{ackStatus},
                retry_count = #{retryCount},
                next_retry_at = #{nextRetryAt},
                sent_at = #{sentAt},
                fail_reason = #{failReason},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateById(OutboxMessageDO outbox);

    @Select("""
            SELECT id,
                   event_id AS eventId,
                   message_id AS messageId,
                   client_msg_id AS clientMsgId,
                   from_user_id AS fromUserId,
                   topic,
                   tag,
                   payload,
                   status,
                   ack_status AS ackStatus,
                   retry_count AS retryCount,
                   max_retry_count AS maxRetryCount,
                   next_retry_at AS nextRetryAt,
                   processing_at AS processingAt,
                   sent_at AS sentAt,
                   fail_reason AS failReason,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM outbox_message
            WHERE status = 0
              AND retry_count < 3
              AND next_retry_at < #{now}
            LIMIT #{limit}
            """)
    List<OutboxMessageDO> selectPendingMessages(@Param("now") Date now, @Param("limit") int limit);

    @Update("UPDATE outbox_message SET ack_status = #{ackStatus}, status = #{status}, updated_at = NOW() WHERE message_id = #{messageId} AND from_user_id = #{fromUserId}")
    int updateAckStatus(@Param("messageId") Long messageId, @Param("fromUserId") Long fromUserId, @Param("ackStatus") int ackStatus, @Param("status") int status);
}