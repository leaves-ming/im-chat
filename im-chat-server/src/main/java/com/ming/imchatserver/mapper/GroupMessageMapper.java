package com.ming.imchatserver.mapper;

import com.ming.imchatserver.dao.GroupMessageDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
                   created_at AS createdAt,
                   retracted_at AS retractedAt,
                   retracted_by AS retractedBy
            FROM im_group_message
            WHERE group_id = #{groupId}
              AND seq > #{cursorSeq}
            ORDER BY seq ASC
            LIMIT #{limit}
            """)
    List<GroupMessageDO> findAfterSeq(@Param("groupId") Long groupId,
                                      @Param("cursorSeq") Long cursorSeq,
                                      @Param("limit") int limit);

    @Select("""
            SELECT 1
            FROM im_group_message gm
            JOIN im_group_member gmem ON gmem.group_id = gm.group_id
            WHERE gm.msg_type = 'FILE'
              AND JSON_UNQUOTE(JSON_EXTRACT(gm.content, '$.fileId')) = #{fileId}
              AND gmem.user_id = #{userId}
              AND gmem.member_status = 1
            LIMIT 1
            """)
    Integer existsFileForActiveMember(@Param("fileId") String fileId,
                                      @Param("userId") Long userId);

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
                   created_at AS createdAt,
                   retracted_at AS retractedAt,
                   retracted_by AS retractedBy
            FROM im_group_message
            WHERE server_msg_id = #{serverMsgId}
            LIMIT 1
            """)
    GroupMessageDO findByServerMsgId(@Param("serverMsgId") String serverMsgId);

    @Update("""
            UPDATE im_group_message
            SET status = #{status},
                retracted_at = #{retractedAt},
                retracted_by = #{retractedBy}
            WHERE server_msg_id = #{serverMsgId}
              AND retracted_at IS NULL
              AND status <> #{status}
            """)
    int updateRetractionByServerMsgId(@Param("serverMsgId") String serverMsgId,
                                      @Param("status") Integer status,
                                      @Param("retractedAt") java.util.Date retractedAt,
                                      @Param("retractedBy") Long retractedBy);
}
