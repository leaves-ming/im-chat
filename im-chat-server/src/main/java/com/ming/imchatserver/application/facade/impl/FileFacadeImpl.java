package com.ming.imchatserver.application.facade.impl;

import com.ming.imchatserver.application.facade.FileFacade;
import com.ming.imchatserver.dao.FileRecordDO;
import com.ming.imchatserver.service.FileService;
import org.springframework.stereotype.Component;

/**
 * 文件应用门面默认实现。
 */
@Component
public class FileFacadeImpl implements FileFacade {

    private final FileService fileService;

    public FileFacadeImpl(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public FileRecordDO findByFileId(String fileId) {
        return fileService == null ? null : fileService.findByFileId(fileId);
    }
}
