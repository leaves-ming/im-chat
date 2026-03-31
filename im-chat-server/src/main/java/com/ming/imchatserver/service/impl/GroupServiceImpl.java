package com.ming.imchatserver.service.impl;

import com.ming.imchatserver.dao.GroupDO;
import com.ming.imchatserver.dao.GroupMemberDO;
import com.ming.imchatserver.group.GroupDomainConstants;
import com.ming.imchatserver.mapper.GroupMapper;
import com.ming.imchatserver.mapper.GroupMemberMapper;
import com.ming.imchatserver.service.GroupBizException;
import com.ming.imchatserver.service.GroupErrorCode;
import com.ming.imchatserver.service.GroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@link GroupService} 的默认实现。
 */
@Service
public class GroupServiceImpl implements GroupService {

    private static final int DEFAULT_MEMBER_LIMIT = 1000;
    private static final int MAX_LIST_LIMIT = 200;

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;

    public GroupServiceImpl(GroupMapper groupMapper, GroupMemberMapper groupMemberMapper) {
        this.groupMapper = groupMapper;
        this.groupMemberMapper = groupMemberMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateGroupResult createGroup(Long ownerUserId, String name, Integer memberLimit) {
        if (ownerUserId == null || ownerUserId <= 0 || name == null || name.trim().isEmpty()) {
            throw new GroupBizException(GroupErrorCode.INVALID_PARAM, "invalid create group params");
        }
        int finalMemberLimit = memberLimit == null || memberLimit <= 0 ? DEFAULT_MEMBER_LIMIT : memberLimit;

        GroupDO group = new GroupDO();
        group.setGroupNo(generateGroupNo());
        group.setOwnerUserId(ownerUserId);
        group.setName(name.trim());
        group.setStatus(GroupDomainConstants.GROUP_STATUS_ACTIVE);
        group.setMemberLimit(finalMemberLimit);

        groupMapper.insertGroup(group);
        groupMemberMapper.insertOwner(group.getId(), ownerUserId);
        return new CreateGroupResult(group.getId(), group.getGroupNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JoinGroupResult joinGroup(Long groupId, Long userId) {
        if (groupId == null || groupId <= 0 || userId == null || userId <= 0) {
            throw new GroupBizException(GroupErrorCode.INVALID_PARAM, "invalid join params");
        }

        GroupDO group = requireActiveGroup(groupId);

        GroupMemberDO activeMember = groupMemberMapper.findActiveMember(groupId, userId);
        if (activeMember != null) {
            return new JoinGroupResult(true, true);
        }

        int activeCount = groupMemberMapper.countActiveMembers(groupId);
        if (activeCount >= group.getMemberLimit()) {
            throw new GroupBizException(GroupErrorCode.GROUP_FULL, "group is full");
        }

        groupMemberMapper.upsertJoin(
                groupId,
                userId,
                GroupDomainConstants.MEMBER_ROLE_MEMBER,
                GroupDomainConstants.MEMBER_STATUS_ACTIVE
        );

        // 并发兜底：插入后再次校验，不满足则回滚。
        int latestCount = groupMemberMapper.countActiveMembers(groupId);
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

        GroupDO group = groupMapper.findById(groupId);
        if (group == null) {
            throw new GroupBizException(GroupErrorCode.GROUP_NOT_FOUND, "group not found");
        }
        if (group.getOwnerUserId() != null && group.getOwnerUserId().equals(userId)) {
            throw new GroupBizException(GroupErrorCode.OWNER_CANNOT_QUIT, "owner cannot quit");
        }

        int updated = groupMemberMapper.markQuit(groupId, userId);
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

        List<GroupMemberDO> fetched = groupMemberMapper.pageActiveMembers(groupId, cursor, pageSize + 1);
        boolean hasMore = fetched.size() > pageSize;
        List<GroupMemberDO> items = hasMore ? new ArrayList<>(fetched.subList(0, pageSize)) : fetched;
        Long nextCursor = items.isEmpty() ? null : items.get(items.size() - 1).getUserId();
        return new MemberPageResult(items, nextCursor, hasMore);
    }

    @Override
    public boolean isActiveMember(Long groupId, Long userId) {
        if (groupId == null || groupId <= 0 || userId == null || userId <= 0) {
            return false;
        }
        requireActiveGroup(groupId);
        return groupMemberMapper.findActiveMember(groupId, userId) != null;
    }

    @Override
    public GroupMemberDO getActiveMember(Long groupId, Long userId) {
        if (groupId == null || groupId <= 0 || userId == null || userId <= 0) {
            return null;
        }
        requireActiveGroup(groupId);
        return groupMemberMapper.findActiveMember(groupId, userId);
    }

    @Override
    public boolean canRecallMessage(Long groupId, Long operatorUserId, Long targetUserId) {
        GroupMemberDO operator = getActiveMember(groupId, operatorUserId);
        if (operator == null || targetUserId == null || targetUserId <= 0) {
            return false;
        }
        if (operatorUserId != null && operatorUserId.equals(targetUserId)) {
            return true;
        }
        GroupMemberDO target = getActiveMember(groupId, targetUserId);
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
        return groupMemberMapper.findActiveUserIds(groupId);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 50;
        }
        return Math.min(limit, MAX_LIST_LIMIT);
    }

    private GroupDO requireActiveGroup(Long groupId) {
        GroupDO group = groupMapper.findById(groupId);
        if (group == null) {
            throw new GroupBizException(GroupErrorCode.GROUP_NOT_FOUND, "group not found");
        }
        if (!Integer.valueOf(GroupDomainConstants.GROUP_STATUS_ACTIVE).equals(group.getStatus())) {
            throw new GroupBizException(GroupErrorCode.GROUP_NOT_ACTIVE, "group is not active");
        }
        return group;
    }

    private String generateGroupNo() {
        return "g" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
}
