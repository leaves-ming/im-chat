package com.ming.imchatserver.application.facade;

import com.ming.imchatserver.application.model.ContactOperationResult;
import com.ming.imchatserver.application.model.ContactPage;
import com.ming.imchatserver.application.model.GroupJoinResult;
import com.ming.imchatserver.application.model.GroupMemberPage;
import com.ming.imchatserver.application.model.GroupQuitResult;

/**
 * 联系人与群组应用门面。
 */
public interface SocialFacade {

    ContactOperationResult addContact(Long ownerUserId, Long peerUserId);

    ContactOperationResult removeContact(Long ownerUserId, Long peerUserId);

    ContactPage listContacts(Long ownerUserId, Long cursorPeerUserId, int limit);

    GroupJoinResult joinGroup(Long groupId, Long userId);

    GroupQuitResult quitGroup(Long groupId, Long userId);

    GroupMemberPage listMembers(Long groupId, Long cursorUserId, int limit);

    boolean isSingleChatAllowed(Long fromUserId, Long toUserId);

    boolean isGroupMember(Long groupId, Long userId);
}
