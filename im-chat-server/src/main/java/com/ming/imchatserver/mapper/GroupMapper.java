package com.ming.imchatserver.mapper;

import com.ming.imchatserver.dao.GroupDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 群表（im_group）访问接口。
 */
@Mapper
public interface GroupMapper {

    @Insert("""
            INSERT INTO im_group
            (group_no, owner_user_id, name, status, member_limit)
            VALUES
            (#{groupNo}, #{ownerUserId}, #{name}, #{status}, #{memberLimit})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertGroup(GroupDO group);

    @Select("""
            SELECT id, group_no AS groupNo, owner_user_id AS ownerUserId, name, notice, status,
                   member_limit AS memberLimit, created_at AS createdAt, updated_at AS updatedAt
            FROM im_group
            WHERE id = #{id}
            LIMIT 1
            """)
    GroupDO findById(@Param("id") Long id);

    @Select("""
            SELECT id, group_no AS groupNo, owner_user_id AS ownerUserId, name, notice, status,
                   member_limit AS memberLimit, created_at AS createdAt, updated_at AS updatedAt
            FROM im_group
            WHERE group_no = #{groupNo}
            LIMIT 1
            """)
    GroupDO findByGroupNo(@Param("groupNo") String groupNo);
}
