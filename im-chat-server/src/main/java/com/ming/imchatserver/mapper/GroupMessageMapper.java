package com.ming.imchatserver.mapper;

import com.ming.imchatserver.dao.GroupMessageDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 群消息表（im_group_message）访问接口。
 */
@Mapper
public interface GroupMessageMapper {

    @Select("""
            SELECT COALESCE(MAX(seq), 0)
            FROM im_group_message
            WHERE group_id = #{groupId}
            """)
    Long findMaxSeq(@Param("groupId") Long groupId);

    @Insert("""
            INSERT INTO im_group_message
            (group_id, seq, server_msg_id, client_msg_id, from_user_id, msg_type, content, status, created_at)
            VALUES
            (#{groupId}, #{seq}, #{serverMsgId}, #{clientMsgId}, #{fromUserId}, #{msgType}, CAST(#{content} AS JSON), #{status}, #{createdAt})
            """)
    int insert(GroupMessageDO message);

    @Select("""
            SELECT id,
                   group_id AS groupId,
                   seq,
                   server_msg_id AS serverMsgId,
                   client_msg_id AS clientMsgId,
                   from_user_id AS fromUserId,
                   msg_type AS msgType,
                   content,
                   status,
                   created_at AS createdAt
            FROM im_group_message
            WHERE group_id = #{groupId}
              AND seq > #{cursorSeq}
            ORDER BY seq ASC
            LIMIT #{limit}
            """)
    List<GroupMessageDO> findAfterSeq(@Param("groupId") Long groupId,
                                      @Param("cursorSeq") Long cursorSeq,
                                      @Param("limit") int limit);
}
