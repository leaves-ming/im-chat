package com.ming.imchatserver.service.remote;

import com.ming.imchatserver.service.ContactService;
import com.ming.imchatserver.service.SingleChatPermissionCapable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 联系人服务远程适配器。
 */
@Primary
@Component
public class RoutingContactService implements ContactService, SingleChatPermissionCapable {

    private final RemoteContactService remoteContactService;

    public RoutingContactService(RemoteContactService remoteContactService) {
        this.remoteContactService = remoteContactService;
    }

    @Override
    public Result addOrActivateContact(Long ownerUserId, Long peerUserId) {
        return remoteContactService.addOrActivateContact(ownerUserId, peerUserId);
    }

    @Override
    public Result removeOrDeactivateContact(Long ownerUserId, Long peerUserId) {
        return remoteContactService.removeOrDeactivateContact(ownerUserId, peerUserId);
    }

    @Override
    public ContactPageResult listActiveContacts(Long ownerUserId, Long cursorPeerUserId, Integer limit) {
        return remoteContactService.listActiveContacts(ownerUserId, cursorPeerUserId, limit);
    }

    @Override
    public boolean isActiveContact(Long ownerUserId, Long peerUserId) {
        return remoteContactService.isActiveContact(ownerUserId, peerUserId);
    }

    @Override
    public boolean isSingleChatAllowed(Long fromUserId, Long toUserId) {
        return remoteContactService.isSingleChatAllowed(fromUserId, toUserId);
    }
}
