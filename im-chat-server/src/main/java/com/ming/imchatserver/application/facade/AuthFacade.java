package com.ming.imchatserver.application.facade;

import com.ming.imchatserver.service.MessageService;

/**
 * 鉴权与会话同步门面。
 */
public interface AuthFacade {

    MessageService.CursorPageResult loadInitialSync(Long userId, String deviceId, int limit);
}
