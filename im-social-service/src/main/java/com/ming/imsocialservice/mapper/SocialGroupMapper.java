package com.ming.imsocialservice.mapper;

import com.ming.imsocialservice.dao.SocialGroupDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 群资料 Mapper。
 */
@Mapper
public interface SocialGroupMapper {

    @Insert("""
            INSERT INTO im_group
            (group_no, owner_user_id, name, status, member_limit)
            VALUES
            (#{groupNo}, #{ownerUserId}, #{name}, #{status}, #{memberLimit})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertGroup(SocialGroupDO group);

    @Select("""
            SELECT id, group_no AS groupNo, owner_user_id AS ownerUserId, name, notice, status,
                   member_limit AS memberLimit, created_at AS createdAt, updated_at AS updatedAt
            FROM im_group
            WHERE id = #{id}
            LIMIT 1
            """)
    SocialGroupDO findById(@Param("id") Long id);
}
