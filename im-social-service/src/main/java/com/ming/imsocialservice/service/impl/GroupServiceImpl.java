package com.ming.imsocialservice.service.impl;

import com.ming.imsocialservice.dao.SocialGroupDO;
import com.ming.imsocialservice.dao.SocialGroupMemberDO;
import com.ming.imsocialservice.group.GroupDomainConstants;
import com.ming.imsocialservice.mapper.SocialGroupMapper;
import com.ming.imsocialservice.mapper.SocialGroupMemberMapper;
import com.ming.imsocialservice.service.GroupBizException;
import com.ming.imsocialservice.service.GroupErrorCode;
import com.ming.imsocialservice.service.GroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 群关系服务默认实现。
 */
@Service
public class GroupServiceImpl implements GroupService {

    private static final int DEFAULT_MEMBER_LIMIT = 1000;
    private static final int MAX_LIST_LIMIT = 200;

    private final SocialGroupMapper socialGroupMapper;
    private final SocialGroupMemberMapper socialGroupMemberMapper;

    public GroupServiceImpl(SocialGroupMapper socialGroupMapper, SocialGroupMemberMapper socialGroupMemberMapper) {
        this.socialGroupMapper = socialGroupMapper;
        this.socialGroupMemberMapper = socialGroupMemberMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateGroupResult createGroup(Long ownerUserId, String name, Integer memberLimit) {
        if (ownerUserId == null || ownerUserId <= 0 || name == null || name.trim().isEmpty()) {
            throw new GroupBizException(GroupErrorCode.INVALID_PARAM, "invalid create group params");
        }
        int finalMemberLimit = memberLimit == null || memberLimit <= 0 ? DEFAULT_MEMBER_LIMIT : memberLimit;

        SocialGroupDO group = new SocialGroupDO();
        group.setGroupNo(generateGroupNo());
        group.setOwnerUserId(ownerUserId);
        group.setName(name.trim());
        group.setStatus(GroupDomainConstants.GROUP_STATUS_ACTIVE);
        group.setMemberLimit(finalMemberLimit);

        socialGroupMapper.insertGroup(group);
        socialGroupMemberMapper.insertOwner(group.getId(), ownerUserId);
        return new CreateGroupResult(group.getId(), group.getGroupNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JoinGroupResult joinGroup(Long groupId, Long userId) {
        if (groupId == null || groupId <= 0 || userId == null || userId <= 0) {
            throw new GroupBizException(GroupErrorCode.INVALID_PARAM, "invalid join params");
        }
        SocialGroupDO group = requireActiveGroup(groupId);
        SocialGroupMemberDO activeMember = socialGroupMemberMapper.findActiveMember(groupId, userId);
        if (activeMember != null) {
            return new JoinGroupResult(true, true);
        }
        int activeCount = socialGroupMemberMapper.countActiveMembers(groupId);
        if (activeCount >= group.getMemberLimit()) {
            throw new GroupBizException(GroupErrorCode.GROUP_FULL, "group is full");
        }
        socialGroupMemberMapper.upsertJoin(
                groupId,
                userId,
                GroupDomainConstants.MEMBER_ROLE_MEMBER,
                GroupDomainConstants.MEMBER_STATUS_ACTIVE
        );
        int latestCount = socialGroupMemberMapper.countActiveMembers(groupId);
        if (latestCount > group.getMemberLimit()) {
            throw new GroupBizException(GroupErrorCode.GROUP_FULL, "group is full");
        }
        return new JoinGroupResult(true, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuitGroupResult quitGroup(Long groupId, Long userId) {
        if (groupId == null || groupId <= 0 || userId == null || userId <= 0) {
            throw new GroupBizException(GroupErrorCode.INVALID_PARAM, "invalid quit params");
        }
        SocialGroupDO group = socialGroupMapper.findById(groupId);
        if (group == null) {
            throw new GroupBizException(GroupErrorCode.GROUP_NOT_FOUND, "group not found");
        }
        if (group.getOwnerUserId() != null && group.getOwnerUserId().equals(userId)) {
            throw new GroupBizException(GroupErrorCode.OWNER_CANNOT_QUIT, "owner cannot quit");
        }
        int updated = socialGroupMemberMapper.markQuit(groupId, userId);
        return new QuitGroupResult(true, updated == 0);
    }

    @Override
    public MemberPageResult listMembers(Long groupId, Long cursorUserId, Integer limit) {
        if (groupId == null || groupId <= 0) {
            throw new GroupBizException(GroupErrorCode.INVALID_PARAM, "invalid groupId");
        }
        requireActiveGroup(groupId);
        int pageSize = normalizeLimit(limit);
        long cursor = cursorUserId == null ? 0L : Math.max(0L, cursorUserId);
        List<SocialGroupMemberDO> fetched = socialGroupMemberMapper.pageActiveMembers(groupId, cursor, pageSize + 1);
        boolean hasMore = fetched.size() > pageSize;
        List<SocialGroupMemberDO> items = hasMore ? new ArrayList<>(fetched.subList(0, pageSize)) : fetched;
        Long nextCursor = items.isEmpty() ? null : items.getLast().getUserId();
        return new MemberPageResult(items, nextCursor, hasMore);
    }

    @Override
    public boolean canRecallMessage(Long groupId, Long operatorUserId, Long targetUserId) {
        SocialGroupMemberDO operator = getActiveMember(groupId, operatorUserId);
        if (operator == null || targetUserId == null || targetUserId <= 0) {
            return false;
        }
        if (operatorUserId.equals(targetUserId)) {
            return true;
        }
        SocialGroupMemberDO target = getActiveMember(groupId, targetUserId);
        if (target == null) {
            return false;
        }
        return roleLevel(operator.getRole()) > roleLevel(target.getRole());
    }

    @Override
    public List<Long> listActiveMemberUserIds(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new GroupBizException(GroupErrorCode.INVALID_PARAM, "invalid groupId");
        }
        requireActiveGroup(groupId);
        return socialGroupMemberMapper.findActiveUserIds(groupId);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 50;
        }
        return Math.min(limit, MAX_LIST_LIMIT);
    }

    private SocialGroupMemberDO getActiveMember(Long groupId, Long userId) {
        if (groupId == null || groupId <= 0 || userId == null || userId <= 0) {
            return null;
        }
        requireActiveGroup(groupId);
        return socialGroupMemberMapper.findActiveMember(groupId, userId);
    }

    private SocialGroupDO requireActiveGroup(Long groupId) {
        SocialGroupDO group = socialGroupMapper.findById(groupId);
        if (group == null) {
            throw new GroupBizException(GroupErrorCode.GROUP_NOT_FOUND, "group not found");
        }
        if (!Integer.valueOf(GroupDomainConstants.GROUP_STATUS_ACTIVE).equals(group.getStatus())) {
            throw new GroupBizException(GroupErrorCode.GROUP_NOT_ACTIVE, "group is not active");
        }
        return group;
    }

    private int roleLevel(Integer role) {
        if (role == null) {
            return 0;
        }
        return switch (role) {
            case GroupDomainConstants.MEMBER_ROLE_OWNER -> 3;
            case GroupDomainConstants.MEMBER_ROLE_ADMIN -> 2;
            case GroupDomainConstants.MEMBER_ROLE_MEMBER -> 1;
            default -> 0;
        };
    }

    private String generateGroupNo() {
        return "g" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
