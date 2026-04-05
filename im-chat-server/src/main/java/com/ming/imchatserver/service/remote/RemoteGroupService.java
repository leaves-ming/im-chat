package com.ming.imchatserver.service.remote;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.social.CheckGroupRecallPermissionRequest;
import com.ming.imapicontract.social.CheckGroupRecallPermissionResponse;
import com.ming.imapicontract.social.GetGroupMemberIdsRequest;
import com.ming.imapicontract.social.GetGroupMemberIdsResponse;
import com.ming.imapicontract.social.GroupJoinRequest;
import com.ming.imapicontract.social.GroupJoinResponse;
import com.ming.imapicontract.social.GroupMemberDTO;
import com.ming.imapicontract.social.GroupMemberListRequest;
import com.ming.imapicontract.social.GroupMemberListResponse;
import com.ming.imapicontract.social.GroupQuitRequest;
import com.ming.imapicontract.social.GroupQuitResponse;
import com.ming.imchatserver.config.SocialRouteProperties;
import com.ming.imchatserver.dao.GroupMemberDO;
import com.ming.imchatserver.remote.social.SocialServiceClient;
import com.ming.imchatserver.service.GroupBizException;
import com.ming.imchatserver.service.GroupErrorCode;
import com.ming.imchatserver.service.GroupService;
import com.ming.imchatserver.service.SocialRpcException;
import com.ming.imchatserver.service.support.SocialCacheSupport;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * social 群关系远程服务包装。
 */
@Component
public class RemoteGroupService {

    private final SocialServiceClient socialServiceClient;
    private final SocialCacheSupport socialCacheSupport;
    private final SocialRouteProperties socialRouteProperties;

    public RemoteGroupService(SocialServiceClient socialServiceClient,
                              SocialCacheSupport socialCacheSupport,
                              SocialRouteProperties socialRouteProperties) {
        this.socialServiceClient = socialServiceClient;
        this.socialCacheSupport = socialCacheSupport;
        this.socialRouteProperties = socialRouteProperties;
    }

    public GroupService.JoinGroupResult joinGroup(Long groupId, Long userId) {
        GroupJoinResponse response = unwrap(socialServiceClient.joinGroup(new GroupJoinRequest(groupId, userId)));
        socialCacheSupport.invalidateGroup(groupId);
        return new GroupService.JoinGroupResult(response.joined(), response.idempotent());
    }

    public GroupService.QuitGroupResult quitGroup(Long groupId, Long userId) {
        GroupQuitResponse response = unwrap(socialServiceClient.quitGroup(new GroupQuitRequest(groupId, userId)));
        socialCacheSupport.invalidateGroup(groupId);
        return new GroupService.QuitGroupResult(response.quit(), response.idempotent());
    }

    public GroupService.MemberPageResult listMembers(Long groupId, Long cursorUserId, Integer limit) {
        GroupMemberListResponse response = unwrap(socialServiceClient.listGroupMembers(
                new GroupMemberListRequest(groupId, cursorUserId, limit)));
        List<GroupMemberDO> items = new ArrayList<>();
        for (GroupMemberDTO item : response.items()) {
            items.add(toGroupMemberDO(item));
        }
        return new GroupService.MemberPageResult(items, response.nextCursor(), response.hasMore());
    }

    public boolean isActiveMember(Long groupId, Long userId) {
        List<Long> memberIds = getGroupMemberIds(groupId, false);
        return memberIds.contains(userId);
    }

    public GroupMemberDO getActiveMember(Long groupId, Long userId) {
        List<Long> memberIds = getGroupMemberIds(groupId, false);
        if (!memberIds.contains(userId)) {
            return null;
        }
        GroupMemberDO target = new GroupMemberDO();
        target.setGroupId(groupId);
        target.setUserId(userId);
        target.setMemberStatus(1);
        return target;
    }

    public boolean canRecallMessage(Long groupId, Long operatorUserId, Long targetUserId) {
        Boolean cached = socialCacheSupport.getGroupRecallPermission(groupId, operatorUserId, targetUserId);
        if (cached != null) {
            return cached;
        }
        ApiResponse<CheckGroupRecallPermissionResponse> response = socialServiceClient.checkGroupRecallPermission(
                new CheckGroupRecallPermissionRequest(groupId, operatorUserId, targetUserId));
        if (response != null && response.isSuccess()) {
            boolean allowed = response.getData() != null && response.getData().allowed();
            socialCacheSupport.putGroupRecallPermission(
                    groupId, operatorUserId, targetUserId, allowed, groupRecallPermissionCacheTtlMillis());
            return allowed;
        }
        if (isRemoteUnavailable(response)) {
            throw new SocialRpcException("REMOTE_UNAVAILABLE", messageOf(response, "group recall permission unavailable"));
        }
        throw mapToException(response);
    }

    public List<Long> listActiveMemberUserIds(Long groupId) {
        return getGroupMemberIds(groupId, true);
    }

    private List<Long> getGroupMemberIds(Long groupId, boolean tolerant) {
        List<Long> cached = socialCacheSupport.getGroupMemberIds(groupId);
        if (cached != null) {
            return cached;
        }
        ApiResponse<GetGroupMemberIdsResponse> response = socialServiceClient.getGroupMemberIds(new GetGroupMemberIdsRequest(groupId));
        if (response != null && response.isSuccess()) {
            List<Long> ids = response.getData() == null || response.getData().memberUserIds() == null
                    ? List.of()
                    : response.getData().memberUserIds();
            socialCacheSupport.putGroupMemberIds(groupId, ids, groupMemberIdsCacheTtlMillis());
            return ids;
        }
        if (tolerant) {
            if (isRemoteUnavailable(response)) {
                return List.of();
            }
            if (response != null && ("GROUP_NOT_FOUND".equals(response.getCode()) || "GROUP_NOT_ACTIVE".equals(response.getCode()))) {
                return List.of();
            }
        }
        throw mapToException(response);
    }

    private GroupMemberDO toGroupMemberDO(GroupMemberDTO item) {
        GroupMemberDO target = new GroupMemberDO();
        target.setGroupId(item.groupId());
        target.setUserId(item.userId());
        target.setRole(item.role());
        target.setMemberStatus(item.memberStatus());
        target.setJoinedAt(item.joinedAt());
        target.setMutedUntil(item.mutedUntil());
        target.setCreatedAt(item.createdAt());
        target.setUpdatedAt(item.updatedAt());
        return target;
    }

    private <T> T unwrap(ApiResponse<T> response) {
        if (response == null) {
            throw new SocialRpcException("REMOTE_UNAVAILABLE", "social service response is null");
        }
        if (response.isSuccess()) {
            return response.getData();
        }
        throw mapToException(response);
    }

    private RuntimeException mapToException(ApiResponse<?> response) {
        String code = response == null ? "REMOTE_UNAVAILABLE" : response.getCode();
        String message = messageOf(response, "social service call failed");
        if ("INVALID_PARAM".equals(code)) {
            return new IllegalArgumentException(message);
        }
        if ("GROUP_NOT_FOUND".equals(code)) {
            return new GroupBizException(GroupErrorCode.GROUP_NOT_FOUND, message);
        }
        if ("GROUP_NOT_ACTIVE".equals(code)) {
            return new GroupBizException(GroupErrorCode.GROUP_NOT_ACTIVE, message);
        }
        if ("GROUP_FULL".equals(code)) {
            return new GroupBizException(GroupErrorCode.GROUP_FULL, message);
        }
        if ("OWNER_CANNOT_QUIT".equals(code)) {
            return new GroupBizException(GroupErrorCode.OWNER_CANNOT_QUIT, message);
        }
        if ("REMOTE_UNAVAILABLE".equals(code)) {
            return new SocialRpcException(code, message);
        }
        if ("FORBIDDEN".equals(code)) {
            return new SecurityException(message);
        }
        return new SocialRpcException(code == null ? "REMOTE_ERROR" : code, message);
    }

    private boolean isRemoteUnavailable(ApiResponse<?> response) {
        return response == null || "REMOTE_UNAVAILABLE".equals(response.getCode());
    }

    private String messageOf(ApiResponse<?> response, String fallback) {
        return response == null || response.getMessage() == null || response.getMessage().isBlank()
                ? fallback
                : response.getMessage();
    }

    private long groupMemberIdsCacheTtlMillis() {
        return Duration.ofSeconds(Math.max(1, socialRouteProperties.getGroupMemberIdsCacheTtlSeconds())).toMillis();
    }

    private long groupRecallPermissionCacheTtlMillis() {
        return Duration.ofSeconds(Math.max(1, socialRouteProperties.getGroupRecallPermissionCacheTtlSeconds())).toMillis();
    }
}
