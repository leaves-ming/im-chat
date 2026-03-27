package com.ming.imchatserver.mapper;

import com.ming.imchatserver.dao.GroupMemberDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 群成员表（im_group_member）访问接口。
 */
@Mapper
public interface GroupMemberMapper {

    @Insert("""
            INSERT INTO im_group_member
            (group_id, user_id, role, member_status, joined_at)
            VALUES
            (#{groupId}, #{userId}, 3, 1, NOW())
            """)
    int insertOwner(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO im_group_member
            (group_id, user_id, role, member_status, joined_at)
            VALUES
            (#{groupId}, #{userId}, #{role}, #{status}, NOW())
            ON DUPLICATE KEY UPDATE
              role = IF(role = 3, role, VALUES(role)),
              member_status = VALUES(member_status),
              joined_at = IF(member_status = 1, joined_at, NOW()),
              updated_at = NOW()
            """)
    int upsertJoin(@Param("groupId") Long groupId,
                   @Param("userId") Long userId,
                   @Param("role") int role,
                   @Param("status") int status);

    @Update("""
            UPDATE im_group_member
            SET member_status = 2,
                updated_at = NOW()
            WHERE group_id = #{groupId}
              AND user_id = #{userId}
              AND member_status = 1
            """)
    int markQuit(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Select("""
            SELECT COUNT(1)
            FROM im_group_member
            WHERE group_id = #{groupId}
              AND member_status = 1
            """)
    int countActiveMembers(@Param("groupId") Long groupId);

    @Select("""
            <script>
            SELECT id, group_id AS groupId, user_id AS userId, role, member_status AS memberStatus,
                   joined_at AS joinedAt, muted_until AS mutedUntil, created_at AS createdAt, updated_at AS updatedAt
            FROM im_group_member
            WHERE group_id = #{groupId}
              AND member_status = 1
              <if test="cursorUserId != null and cursorUserId &gt; 0">
                AND user_id &gt; #{cursorUserId}
              </if>
            ORDER BY user_id ASC
            LIMIT #{limit}
            </script>
            """)
    List<GroupMemberDO> pageActiveMembers(@Param("groupId") Long groupId,
                                          @Param("cursorUserId") Long cursorUserId,
                                          @Param("limit") int limit);

    @Select("""
            SELECT id, group_id AS groupId, user_id AS userId, role, member_status AS memberStatus,
                   joined_at AS joinedAt, muted_until AS mutedUntil, created_at AS createdAt, updated_at AS updatedAt
            FROM im_group_member
            WHERE group_id = #{groupId}
              AND user_id = #{userId}
              AND member_status = 1
            LIMIT 1
            """)
    GroupMemberDO findActiveMember(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Select("""
            SELECT user_id
            FROM im_group_member
            WHERE group_id = #{groupId}
              AND member_status = 1
            ORDER BY user_id ASC
            """)
    List<Long> findActiveUserIds(@Param("groupId") Long groupId);
}
