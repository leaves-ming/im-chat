package com.ming.imchatserver.application.facade.impl;

import com.ming.imchatserver.application.facade.AuthFacade;
import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.application.model.SingleMessagePage;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 会话初始同步路由门面。
 */
@Primary
@Component
public class RoutingAuthFacade implements AuthFacade {

    private final MessageFacade messageFacade;

    public RoutingAuthFacade(MessageFacade messageFacade) {
        this.messageFacade = messageFacade;
    }

    @Override
    public SingleMessagePage loadInitialSync(Long userId, String deviceId, int limit) {
        return messageFacade.loadInitialSync(userId, deviceId, limit);
    }
}
