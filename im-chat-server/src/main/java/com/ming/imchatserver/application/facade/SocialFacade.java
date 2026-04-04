package com.ming.imchatserver.application.facade;

import com.ming.imchatserver.dao.ContactDO;
import com.ming.imchatserver.dao.GroupMemberDO;
import com.ming.imchatserver.dao.GroupMessageDO;
import com.ming.imchatserver.service.ContactService;
import com.ming.imchatserver.service.GroupMessageService;
import com.ming.imchatserver.service.GroupService;

import java.util.List;

/**
 * 联系人与群组应用门面。
 */
public interface SocialFacade {

    ContactService.Result addContact(Long ownerUserId, Long peerUserId);

    ContactService.Result removeContact(Long ownerUserId, Long peerUserId);

    ContactService.ContactPageResult listContacts(Long ownerUserId, Long cursorPeerUserId, int limit);

    GroupService.JoinGroupResult joinGroup(Long groupId, Long userId);

    GroupService.QuitGroupResult quitGroup(Long groupId, Long userId);

    GroupService.MemberPageResult listMembers(Long groupId, Long cursorUserId, int limit);

    GroupMessageService.PersistResult sendGroupChat(Long groupId,
                                                    Long fromUserId,
                                                    String clientMsgId,
                                                    String msgType,
                                                    String content);

    GroupMessageService.PullResult pullGroupOffline(Long groupId, Long userId, Long cursorSeq, int limit);

    GroupMessageDO recallGroupMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds);

    void dispatchGroupPush(Long groupId, GroupMessageDO message) throws Exception;

    boolean isSingleChatAllowed(Long fromUserId, Long toUserId);

    boolean isGroupMember(Long groupId, Long userId);

    List<Long> listActiveMemberUserIds(Long groupId);
}
