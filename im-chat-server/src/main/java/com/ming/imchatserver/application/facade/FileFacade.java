package com.ming.imchatserver.application.facade;

import com.ming.imchatserver.dao.FileRecordDO;

/**
 * 文件业务门面。
 * <p>
 * 当前为后续独立文件服务拆分预留收口点。
 */
public interface FileFacade {

    FileRecordDO findByFileId(String fileId);
}
