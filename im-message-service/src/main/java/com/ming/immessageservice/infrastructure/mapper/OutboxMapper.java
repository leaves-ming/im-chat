package com.ming.immessageservice.infrastructure.mapper;

import com.ming.immessageservice.infrastructure.dao.OutboxMessageDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/**
 * outbox 访问接口。
 */
@Mapper
public interface OutboxMapper {

    @Insert("""
            INSERT INTO im_message_outbox
            (event_id, message_id, topic, tag, payload, status, retry_count, next_retry_at, processing_at)
            VALUES
            (#{eventId}, #{messageId}, #{topic}, #{tag}, #{payload}, #{status}, #{retryCount}, #{nextRetryAt}, #{processingAt})
            """)
    int insert(OutboxMessageDO outboxMessageDO);

    @Select("""
            <script>
            SELECT id, event_id AS eventId, message_id AS messageId, topic, tag, payload, status,
                   retry_count AS retryCount, next_retry_at AS nextRetryAt, processing_at AS processingAt, sent_at AS sentAt,
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
            SET status = 4,
                processing_at = #{now},
                updated_at = NOW()
            WHERE id = #{id}
              AND status IN (0, 2)
              AND next_retry_at <= #{now}
            """)
    int claimForProcessing(@Param("id") Long id, @Param("now") Date now);

    @Update("""
            UPDATE im_message_outbox
            SET status = 1,
                processing_at = NULL,
                sent_at = NOW(),
                fail_reason = NULL,
                updated_at = NOW()
            WHERE id = #{id}
              AND status = 4
            """)
    int markSent(@Param("id") Long id);

    @Update("""
            UPDATE im_message_outbox
            SET status = #{status},
                retry_count = #{retryCount},
                next_retry_at = #{nextRetryAt},
                processing_at = NULL,
                fail_reason = #{failReason},
                updated_at = NOW()
            WHERE id = #{id}
              AND status = 4
            """)
    int markRetryOrDlq(@Param("id") Long id,
                       @Param("status") int status,
                       @Param("retryCount") int retryCount,
                       @Param("nextRetryAt") Date nextRetryAt,
                       @Param("failReason") String failReason);

    @Update("""
            UPDATE im_message_outbox
            SET status = 2,
                next_retry_at = #{now},
                processing_at = NULL,
                fail_reason = 'processing timeout reclaimed',
                updated_at = NOW()
            WHERE status = 4
              AND (
                    (processing_at IS NOT NULL AND processing_at <= #{timeoutBefore})
                 OR (processing_at IS NULL AND updated_at <= #{timeoutBefore})
              )
            """)
    int reclaimTimeoutProcessing(@Param("timeoutBefore") Date timeoutBefore, @Param("now") Date now);
}
