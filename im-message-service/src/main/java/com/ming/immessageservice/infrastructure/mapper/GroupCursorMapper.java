package com.ming.immessageservice.infrastructure.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 群游标访问接口。
 */
@Mapper
public interface GroupCursorMapper {

    @Select("""
            SELECT last_pull_seq
            FROM im_group_cursor
            WHERE group_id = #{groupId}
              AND user_id = #{userId}
            LIMIT 1
            """)
    Long findLastPullSeq(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO im_group_cursor
            (group_id, user_id, last_pull_seq)
            VALUES
            (#{groupId}, #{userId}, #{lastPullSeq})
            ON DUPLICATE KEY UPDATE
              last_pull_seq = GREATEST(last_pull_seq, VALUES(lastPullSeq)),
              updated_at = NOW()
            """)
    int upsertLastPullSeq(@Param("groupId") Long groupId,
                          @Param("userId") Long userId,
                          @Param("lastPullSeq") Long lastPullSeq);
}
