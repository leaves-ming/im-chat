package com.ming.imchatserver.application.facade;

import com.ming.imchatserver.application.model.ContactOperationResult;
import com.ming.imchatserver.application.model.ContactPage;
import com.ming.imchatserver.application.model.GroupJoinResult;
import com.ming.imchatserver.application.model.GroupMemberPage;
import com.ming.imchatserver.application.model.GroupMessagePage;
import com.ming.imchatserver.application.model.GroupMessagePersistResult;
import com.ming.imchatserver.application.model.GroupMessageView;
import com.ming.imchatserver.application.model.GroupQuitResult;

import java.util.List;

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

    GroupMessagePersistResult sendGroupChat(Long groupId, Long fromUserId, String clientMsgId, String msgType, String content);

    GroupMessagePage pullGroupOffline(Long groupId, Long userId, Long cursorSeq, int limit);

    GroupMessageView recallGroupMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds);

    void dispatchGroupPush(Long groupId, GroupMessageView message) throws Exception;

    boolean isSingleChatAllowed(Long fromUserId, Long toUserId);

    boolean isGroupMember(Long groupId, Long userId);

    List<Long> listActiveMemberUserIds(Long groupId);
}
