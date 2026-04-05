package com.ming.imchatserver.service.remote;

import com.ming.imchatserver.dao.GroupMemberDO;
import com.ming.imchatserver.service.GroupService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 群关系服务路由实现。
 */
@Primary
@Component
public class RoutingGroupService implements GroupService {

    private final RemoteGroupService remoteGroupService;

    public RoutingGroupService(RemoteGroupService remoteGroupService) {
        this.remoteGroupService = remoteGroupService;
    }

    @Override
    public CreateGroupResult createGroup(Long ownerUserId, String name, Integer memberLimit) {
        return remoteGroupService.createGroup(ownerUserId, name, memberLimit);
    }

    @Override
    public JoinGroupResult joinGroup(Long groupId, Long userId) {
        return remoteGroupService.joinGroup(groupId, userId);
    }

    @Override
    public QuitGroupResult quitGroup(Long groupId, Long userId) {
        return remoteGroupService.quitGroup(groupId, userId);
    }

    @Override
    public MemberPageResult listMembers(Long groupId, Long cursorUserId, Integer limit) {
        return remoteGroupService.listMembers(groupId, cursorUserId, limit);
    }

    @Override
    public boolean isActiveMember(Long groupId, Long userId) {
        return remoteGroupService.isActiveMember(groupId, userId);
    }

    @Override
    public GroupMemberDO getActiveMember(Long groupId, Long userId) {
        return remoteGroupService.getActiveMember(groupId, userId);
    }

    @Override
    public boolean canRecallMessage(Long groupId, Long operatorUserId, Long targetUserId) {
        return remoteGroupService.canRecallMessage(groupId, operatorUserId, targetUserId);
    }

    @Override
    public List<Long> listActiveMemberUserIds(Long groupId) {
        return remoteGroupService.listActiveMemberUserIds(groupId);
    }
}
