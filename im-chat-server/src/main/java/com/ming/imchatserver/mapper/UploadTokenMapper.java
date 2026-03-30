package com.ming.imchatserver.mapper;

import com.ming.imchatserver.dao.UploadTokenDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;

/**
 * 上传凭证访问接口。
 */
@Mapper
public interface UploadTokenMapper {

    @Insert("""
            INSERT INTO im_upload_token
            (upload_token, file_id, owner_user_id, status, expire_at, bound_at, created_at)
            VALUES
            (#{uploadToken}, #{fileId}, #{ownerUserId}, #{status}, #{expireAt}, #{boundAt}, NOW())
            """)
    int insert(UploadTokenDO token);

    @Select("""
            SELECT id,
                   upload_token AS uploadToken,
                   file_id AS fileId,
                   owner_user_id AS ownerUserId,
                   status,
                   expire_at AS expireAt,
                   bound_at AS boundAt,
                   created_at AS createdAt
            FROM im_upload_token
            WHERE upload_token = #{uploadToken}
            LIMIT 1
            """)
    UploadTokenDO findByUploadToken(@Param("uploadToken") String uploadToken);

    @Update("""
            UPDATE im_upload_token
            SET status = 'BOUND',
                bound_at = NOW()
            WHERE upload_token = #{uploadToken}
              AND owner_user_id = #{ownerUserId}
              AND status = 'UPLOADED'
              AND expire_at > #{now}
            """)
    int consumeUploadedToken(@Param("uploadToken") String uploadToken,
                             @Param("ownerUserId") Long ownerUserId,
                             @Param("now") Date now);
}

