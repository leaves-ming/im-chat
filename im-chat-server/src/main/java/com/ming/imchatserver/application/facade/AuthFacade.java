package com.ming.imchatserver.application.facade;

import com.ming.imchatserver.application.model.SingleMessagePage;

/**
 * 鉴权与会话同步门面。
 */
public interface AuthFacade {

    SingleMessagePage loadInitialSync(Long userId, String deviceId, int limit);
}
