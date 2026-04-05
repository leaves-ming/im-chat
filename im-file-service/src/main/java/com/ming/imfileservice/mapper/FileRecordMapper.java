package com.ming.imfileservice.mapper;

import com.ming.imfileservice.dao.FileRecordDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文件元数据访问接口。
 */
@Mapper
public interface FileRecordMapper {

    @Insert("""
            INSERT INTO im_file_record
            (file_id, owner_user_id, content_type, size, storage_key, original_file_name, created_at)
            VALUES
            (#{fileId}, #{ownerUserId}, #{contentType}, #{size}, #{storageKey}, #{originalFileName}, NOW())
            """)
    int insert(FileRecordDO record);

    @Select("""
            SELECT id,
                   file_id AS fileId,
                   owner_user_id AS ownerUserId,
                   content_type AS contentType,
                   size,
                   storage_key AS storageKey,
                   original_file_name AS originalFileName,
                   created_at AS createdAt
            FROM im_file_record
            WHERE file_id = #{fileId}
            LIMIT 1
            """)
    FileRecordDO findByFileId(@Param("fileId") String fileId);
}
