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
 * 外盒消息访问接口。
 */
@Mapper
public interface OutboxMapper {

    @Insert("""
            INSERT INTO im_message_outbox
            (event_id, message_id, topic, tag, payload, status, retry_count, next_retry_at)
            VALUES
            (#{eventId}, #{messageId}, #{topic}, #{tag}, #{payload}, #{status}, #{retryCount}, #{nextRetryAt})
            """)
    int insert(OutboxMessageDO outboxMessageDO);

    @Select("""
            <script>
            SELECT id, event_id AS eventId, message_id AS messageId, topic, tag, payload, status,
                   retry_count AS retryCount, next_retry_at AS nextRetryAt, sent_at AS sentAt,
                   fail_reason AS failReason, created_at AS createdAt, updated_at AS updatedAt
            FROM im_message_outbox
            WHERE status IN (0, 2)
              AND next_retry_at &lt;= #{now}
            ORDER BY id ASC
            LIMIT #{limit}
            </script>
            """)
    List<OutboxMessageDO> findReadyBatch(@Param("now") Date now, @Param("limit") int limit);

    @Update("""
            UPDATE im_message_outbox
            SET status = 1,
                sent_at = NOW(),
                fail_reason = NULL,
                updated_at = NOW()
            WHERE id = #{id}
              AND status IN (0, 2)
            """)
    int markSent(@Param("id") Long id);

    @Update("""
            UPDATE im_message_outbox
            SET status = #{status},
                retry_count = #{retryCount},
                next_retry_at = #{nextRetryAt},
                fail_reason = #{failReason},
                updated_at = NOW()
            WHERE id = #{id}
            """)
    int markRetryOrDlq(@Param("id") Long id,
                       @Param("status") int status,
                       @Param("retryCount") int retryCount,
                       @Param("nextRetryAt") Date nextRetryAt,
                       @Param("failReason") String failReason);

    @Select("""
            SELECT COUNT(1)
            FROM im_message_outbox
            WHERE status IN (0, 2)
            """)
    long countBacklog();
}
