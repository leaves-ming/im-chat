package com.ming.imchatserver.service.remote;

import com.ming.common.remote.RemoteCallException;
import com.ming.common.remote.RemoteCallTemplate;
import com.ming.imapicontract.common.ApiResponse;
import com.ming.imapicontract.social.CheckGroupRecallPermissionRequest;
import com.ming.imapicontract.social.CheckGroupRecallPermissionResponse;
import com.ming.imapicontract.social.GetGroupMemberIdsRequest;
import com.ming.imapicontract.social.GetGroupMemberIdsResponse;
import com.ming.imapicontract.social.GroupCreateRequest;
import com.ming.imapicontract.social.GroupCreateResponse;
import com.ming.imapicontract.social.GroupJoinRequest;
import com.ming.imapicontract.social.GroupJoinResponse;
import com.ming.imapicontract.social.GroupMemberDTO;
import com.ming.imapicontract.social.GroupMemberListRequest;
import com.ming.imapicontract.social.GroupMemberListResponse;
import com.ming.imapicontract.social.GroupQuitRequest;
import com.ming.imapicontract.social.GroupQuitResponse;
import com.ming.imchatserver.application.model.GroupJoinResult;
import com.ming.imchatserver.application.model.GroupMemberPage;
import com.ming.imchatserver.application.model.GroupMemberView;
import com.ming.imchatserver.application.model.GroupQuitResult;
import com.ming.imchatserver.config.SocialRouteProperties;
import com.ming.imchatserver.remote.social.SocialServiceClient;
import com.ming.imchatserver.service.GroupBizException;
import com.ming.imchatserver.service.GroupErrorCode;
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

    private static final String SERVICE_NAME = "social-service";

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

    public record CreateGroupResult(Long groupId, String groupNo) {
    }

    public CreateGroupResult createGroup(Long ownerUserId, String name, Integer memberLimit) {
        GroupCreateResponse response = RemoteCallTemplate.execute(() ->
                socialServiceClient.createGroup(new GroupCreateRequest(ownerUserId, name, memberLimit)), SERVICE_NAME);
        socialCacheSupport.invalidateGroup(response.groupId());
        return new CreateGroupResult(response.groupId(), response.groupNo());
    }

    public GroupJoinResult joinGroup(Long groupId, Long userId) {
        GroupJoinResponse response = RemoteCallTemplate.execute(() ->
                socialServiceClient.joinGroup(new GroupJoinRequest(groupId, userId)), SERVICE_NAME);
        socialCacheSupport.invalidateGroup(groupId);
        return new GroupJoinResult(response.joined(), response.idempotent());
    }

    public GroupQuitResult quitGroup(Long groupId, Long userId) {
        GroupQuitResponse response = RemoteCallTemplate.execute(() ->
                socialServiceClient.quitGroup(new GroupQuitRequest(groupId, userId)), SERVICE_NAME);
        socialCacheSupport.invalidateGroup(groupId);
        return new GroupQuitResult(response.quit(), response.idempotent());
    }

    public GroupMemberPage listMembers(Long groupId, Long cursorUserId, Integer limit) {
        GroupMemberListResponse response = RemoteCallTemplate.execute(() ->
                socialServiceClient.listGroupMembers(new GroupMemberListRequest(groupId, cursorUserId, limit)), SERVICE_NAME);
        List<GroupMemberView> items = new ArrayList<>();
        for (GroupMemberDTO item : response.items()) {
            items.add(toGroupMemberView(item));
        }
        return new GroupMemberPage(items, response.nextCursor(), response.hasMore());
    }

    public boolean isActiveMember(Long groupId, Long userId) {
        List<Long> memberIds = getGroupMemberIds(groupId, false);
        return memberIds.contains(userId);
    }

    public boolean canRecallMessage(Long groupId, Long operatorUserId, Long targetUserId) {
        Boolean cached = socialCacheSupport.getGroupRecallPermission(groupId, operatorUserId, targetUserId);
        if (cached != null) {
            return cached;
        }
        try {
            CheckGroupRecallPermissionResponse response = RemoteCallTemplate.execute(() ->
                    socialServiceClient.checkGroupRecallPermission(
                            new CheckGroupRecallPermissionRequest(groupId, operatorUserId, targetUserId)), SERVICE_NAME);
            boolean allowed = response != null && response.allowed();
            socialCacheSupport.putGroupRecallPermission(
                    groupId, operatorUserId, targetUserId, allowed, groupRecallPermissionCacheTtlMillis());
            return allowed;
        } catch (RemoteCallException e) {
            if ("REMOTE_UNAVAILABLE".equals(e.getCode())) {
                throw new SocialRpcException(e.getCode(), e.getMessage());
            }
            throw mapGroupException(e);
        }
    }

    public List<Long> listActiveMemberUserIds(Long groupId) {
        return getGroupMemberIds(groupId, true);
    }

    private List<Long> getGroupMemberIds(Long groupId, boolean tolerant) {
        List<Long> cached = socialCacheSupport.getGroupMemberIds(groupId);
        if (cached != null) {
            return cached;
        }
        try {
            GetGroupMemberIdsResponse response = RemoteCallTemplate.execute(() ->
                    socialServiceClient.getGroupMemberIds(new GetGroupMemberIdsRequest(groupId)), SERVICE_NAME);
            List<Long> ids = response == null || response.memberUserIds() == null
                    ? List.of()
                    : response.memberUserIds();
            socialCacheSupport.putGroupMemberIds(groupId, ids, groupMemberIdsCacheTtlMillis());
            return ids;
        } catch (RemoteCallException e) {
            if (tolerant) {
                if ("REMOTE_UNAVAILABLE".equals(e.getCode())
                        || "GROUP_NOT_FOUND".equals(e.getCode())
                        || "GROUP_NOT_ACTIVE".equals(e.getCode())) {
                    return List.of();
                }
            }
            throw mapGroupException(e);
        }
    }

    private GroupMemberView toGroupMemberView(GroupMemberDTO item) {
        return new GroupMemberView(
                item.groupId(),
                item.userId(),
                item.role(),
                item.memberStatus(),
                item.joinedAt(),
                item.mutedUntil(),
                item.createdAt(),
                item.updatedAt()
        );
    }

    private RuntimeException mapGroupException(RemoteCallException e) {
        switch (e.getCode()) {
            case "GROUP_NOT_FOUND":
                return new GroupBizException(GroupErrorCode.GROUP_NOT_FOUND, e.getMessage());
            case "GROUP_NOT_ACTIVE":
                return new GroupBizException(GroupErrorCode.GROUP_NOT_ACTIVE, e.getMessage());
            case "GROUP_FULL":
                return new GroupBizException(GroupErrorCode.GROUP_FULL, e.getMessage());
            case "OWNER_CANNOT_QUIT":
                return new GroupBizException(GroupErrorCode.OWNER_CANNOT_QUIT, e.getMessage());
            case "REMOTE_UNAVAILABLE":
                return new SocialRpcException(e.getCode(), e.getMessage());
            case "FORBIDDEN":
                return new SecurityException(e.getMessage());
            case "INVALID_PARAM":
                return new IllegalArgumentException(e.getMessage());
            default:
                return new SocialRpcException(e.getCode() == null ? "REMOTE_ERROR" : e.getCode(), e.getMessage());
        }
    }

    private long groupMemberIdsCacheTtlMillis() {
        return Duration.ofSeconds(Math.max(1, socialRouteProperties.getGroupMemberIdsCacheTtlSeconds())).toMillis();
    }

    private long groupRecallPermissionCacheTtlMillis() {
        return Duration.ofSeconds(Math.max(1, socialRouteProperties.getGroupRecallPermissionCacheTtlSeconds())).toMillis();
    }
}
