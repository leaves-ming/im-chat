package com.ming.imchatserver.application.facade.impl;

import com.ming.imchatserver.application.facade.SocialFacade;
import com.ming.imchatserver.application.model.ContactOperationResult;
import com.ming.imchatserver.application.model.ContactPage;
import com.ming.imchatserver.application.model.GroupJoinResult;
import com.ming.imchatserver.application.model.GroupMemberPage;
import com.ming.imchatserver.application.model.GroupQuitResult;
import com.ming.imchatserver.service.remote.RemoteContactService;
import com.ming.imchatserver.service.remote.RemoteGroupService;
import org.springframework.stereotype.Component;

/**
 * 联系人与群组应用门面默认实现。
 */
@Component
public class SocialFacadeImpl implements SocialFacade {

    private final RemoteContactService contactService;
    private final RemoteGroupService groupService;

    public SocialFacadeImpl(RemoteContactService contactService,
                            RemoteGroupService groupService) {
        this.contactService = contactService;
        this.groupService = groupService;
    }

    @Override
    public ContactOperationResult addContact(Long ownerUserId, Long peerUserId) {
        return contactService.addOrActivateContact(ownerUserId, peerUserId);
    }

    @Override
    public ContactOperationResult removeContact(Long ownerUserId, Long peerUserId) {
        return contactService.removeOrDeactivateContact(ownerUserId, peerUserId);
    }

    @Override
    public ContactPage listContacts(Long ownerUserId, Long cursorPeerUserId, int limit) {
        return contactService.listActiveContacts(ownerUserId, cursorPeerUserId, limit);
    }

    @Override
    public GroupJoinResult joinGroup(Long groupId, Long userId) {
        return groupService.joinGroup(groupId, userId);
    }

    @Override
    public GroupQuitResult quitGroup(Long groupId, Long userId) {
        return groupService.quitGroup(groupId, userId);
    }

    @Override
    public GroupMemberPage listMembers(Long groupId, Long cursorUserId, int limit) {
        return groupService.listMembers(groupId, cursorUserId, limit);
    }

    @Override
    public boolean isSingleChatAllowed(Long fromUserId, Long toUserId) {
        return contactService.isSingleChatAllowed(fromUserId, toUserId);
    }

    @Override
    public boolean isGroupMember(Long groupId, Long userId) {
        return groupService != null && groupService.isActiveMember(groupId, userId);
    }
}
