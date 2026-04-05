package com.ming.imchatserver.service.remote;

import com.ming.imchatserver.config.SocialRouteProperties;
import com.ming.imchatserver.service.ContactService;
import com.ming.imchatserver.service.SingleChatPermissionCapable;
import com.ming.imchatserver.service.impl.ContactServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 联系人服务路由实现。
 */
@Primary
@Component
public class RoutingContactService implements ContactService, SingleChatPermissionCapable {

    private final ContactServiceImpl localContactService;
    private final RemoteContactService remoteContactService;
    private final SocialRouteProperties socialRouteProperties;

    public RoutingContactService(ContactServiceImpl localContactService,
                                 RemoteContactService remoteContactService,
                                 SocialRouteProperties socialRouteProperties) {
        this.localContactService = localContactService;
        this.remoteContactService = remoteContactService;
        this.socialRouteProperties = socialRouteProperties;
    }

    @Override
    public Result addOrActivateContact(Long ownerUserId, Long peerUserId) {
        return socialRouteProperties.isRemoteEnabled()
                ? remoteContactService.addOrActivateContact(ownerUserId, peerUserId)
                : localContactService.addOrActivateContact(ownerUserId, peerUserId);
    }

    @Override
    public Result removeOrDeactivateContact(Long ownerUserId, Long peerUserId) {
        return socialRouteProperties.isRemoteEnabled()
                ? remoteContactService.removeOrDeactivateContact(ownerUserId, peerUserId)
                : localContactService.removeOrDeactivateContact(ownerUserId, peerUserId);
    }

    @Override
    public ContactPageResult listActiveContacts(Long ownerUserId, Long cursorPeerUserId, Integer limit) {
        return socialRouteProperties.isRemoteEnabled()
                ? remoteContactService.listActiveContacts(ownerUserId, cursorPeerUserId, limit)
                : localContactService.listActiveContacts(ownerUserId, cursorPeerUserId, limit);
    }

    @Override
    public boolean isActiveContact(Long ownerUserId, Long peerUserId) {
        return socialRouteProperties.isRemoteEnabled()
                ? remoteContactService.isActiveContact(ownerUserId, peerUserId)
                : localContactService.isActiveContact(ownerUserId, peerUserId);
    }

    @Override
    public boolean isSingleChatAllowed(Long fromUserId, Long toUserId) {
        return socialRouteProperties.isRemoteEnabled()
                ? remoteContactService.isSingleChatAllowed(fromUserId, toUserId)
                : localContactService.isActiveContact(fromUserId, toUserId)
                && localContactService.isActiveContact(toUserId, fromUserId);
    }
}
