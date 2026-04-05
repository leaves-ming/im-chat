package com.ming.imsocialservice.mapper;

import com.ming.imsocialservice.dao.ContactRelationDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 联系人关系 Mapper。
 */
@Mapper
public interface ContactRelationMapper {

    @Insert("""
            INSERT INTO im_contact
            (owner_user_id, peer_user_id, relation_status, source)
            VALUES
            (#{ownerUserId}, #{peerUserId}, #{relationStatus}, #{source})
            ON DUPLICATE KEY UPDATE
              relation_status = VALUES(relation_status),
              source = COALESCE(source, VALUES(source)),
              updated_at = NOW()
            """)
    int upsertRelation(@Param("ownerUserId") Long ownerUserId,
                       @Param("peerUserId") Long peerUserId,
                       @Param("relationStatus") Integer relationStatus,
                       @Param("source") String source);

    @Update("""
            UPDATE im_contact
            SET relation_status = #{relationStatus},
                updated_at = NOW()
            WHERE owner_user_id = #{ownerUserId}
              AND peer_user_id = #{peerUserId}
            """)
    int updateRelation(@Param("ownerUserId") Long ownerUserId,
                       @Param("peerUserId") Long peerUserId,
                       @Param("relationStatus") Integer relationStatus);

    @Select("""
            SELECT id,
                   owner_user_id AS ownerUserId,
                   peer_user_id AS peerUserId,
                   relation_status AS relationStatus,
                   source,
                   alias,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM im_contact
            WHERE owner_user_id = #{ownerUserId}
              AND peer_user_id = #{peerUserId}
            LIMIT 1
            """)
    ContactRelationDO findByOwnerAndPeer(@Param("ownerUserId") Long ownerUserId, @Param("peerUserId") Long peerUserId);

    @Select("""
            <script>
            SELECT id,
                   owner_user_id AS ownerUserId,
                   peer_user_id AS peerUserId,
                   relation_status AS relationStatus,
                   source,
                   alias,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM im_contact
            WHERE owner_user_id = #{ownerUserId}
              AND relation_status = 1
              <if test="cursorPeerUserId != null and cursorPeerUserId &gt; 0">
                AND peer_user_id &gt; #{cursorPeerUserId}
              </if>
            ORDER BY peer_user_id ASC
            LIMIT #{limit}
            </script>
            """)
    List<ContactRelationDO> pageActiveContacts(@Param("ownerUserId") Long ownerUserId,
                                               @Param("cursorPeerUserId") Long cursorPeerUserId,
                                               @Param("limit") int limit);
}
