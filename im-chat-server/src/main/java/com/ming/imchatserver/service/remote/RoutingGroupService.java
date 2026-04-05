package com.ming.imchatserver.service.remote;

import com.ming.imchatserver.config.SocialRouteProperties;
import com.ming.imchatserver.dao.GroupMemberDO;
import com.ming.imchatserver.service.GroupService;
import com.ming.imchatserver.service.impl.GroupServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 群关系服务路由实现。
 */
@Primary
@Component
public class RoutingGroupService implements GroupService {

    private final GroupServiceImpl localGroupService;
    private final RemoteGroupService remoteGroupService;
    private final SocialRouteProperties socialRouteProperties;

    public RoutingGroupService(GroupServiceImpl localGroupService,
                               RemoteGroupService remoteGroupService,
                               SocialRouteProperties socialRouteProperties) {
        this.localGroupService = localGroupService;
        this.remoteGroupService = remoteGroupService;
        this.socialRouteProperties = socialRouteProperties;
    }

    @Override
    public CreateGroupResult createGroup(Long ownerUserId, String name, Integer memberLimit) {
        return localGroupService.createGroup(ownerUserId, name, memberLimit);
    }

    @Override
    public JoinGroupResult joinGroup(Long groupId, Long userId) {
        return socialRouteProperties.isRemoteEnabled()
                ? remoteGroupService.joinGroup(groupId, userId)
                : localGroupService.joinGroup(groupId, userId);
    }

    @Override
    public QuitGroupResult quitGroup(Long groupId, Long userId) {
        return socialRouteProperties.isRemoteEnabled()
                ? remoteGroupService.quitGroup(groupId, userId)
                : localGroupService.quitGroup(groupId, userId);
    }

    @Override
    public MemberPageResult listMembers(Long groupId, Long cursorUserId, Integer limit) {
        return socialRouteProperties.isRemoteEnabled()
                ? remoteGroupService.listMembers(groupId, cursorUserId, limit)
                : localGroupService.listMembers(groupId, cursorUserId, limit);
    }

    @Override
    public boolean isActiveMember(Long groupId, Long userId) {
        return socialRouteProperties.isRemoteEnabled()
                ? remoteGroupService.isActiveMember(groupId, userId)
                : localGroupService.isActiveMember(groupId, userId);
    }

    @Override
    public GroupMemberDO getActiveMember(Long groupId, Long userId) {
        return socialRouteProperties.isRemoteEnabled()
                ? remoteGroupService.getActiveMember(groupId, userId)
                : localGroupService.getActiveMember(groupId, userId);
    }

    @Override
    public boolean canRecallMessage(Long groupId, Long operatorUserId, Long targetUserId) {
        return socialRouteProperties.isRemoteEnabled()
                ? remoteGroupService.canRecallMessage(groupId, operatorUserId, targetUserId)
                : localGroupService.canRecallMessage(groupId, operatorUserId, targetUserId);
    }

    @Override
    public List<Long> listActiveMemberUserIds(Long groupId) {
        return socialRouteProperties.isRemoteEnabled()
                ? remoteGroupService.listActiveMemberUserIds(groupId)
                : localGroupService.listActiveMemberUserIds(groupId);
    }
}
