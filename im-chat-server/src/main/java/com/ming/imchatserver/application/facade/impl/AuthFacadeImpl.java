package com.ming.imchatserver.application.facade.impl;

import com.ming.imchatserver.application.facade.AuthFacade;
import com.ming.imchatserver.service.MessageService;
import org.springframework.stereotype.Component;

/**
 * 鉴权应用门面默认实现。
 */
@Component
public class AuthFacadeImpl implements AuthFacade {

    private final MessageService messageService;

    public AuthFacadeImpl(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public MessageService.CursorPageResult loadInitialSync(Long userId, String deviceId, int limit) {
        return messageService.pullOfflineFromCheckpoint(userId, deviceId, limit);
    }
}
