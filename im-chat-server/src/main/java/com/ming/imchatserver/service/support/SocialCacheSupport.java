package com.ming.imchatserver.service.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * social 远程调用本地缓存，Caffeine LRU实现，自动过期+容量上限，避免OOM。
 */
@Component
public class SocialCacheSupport {

    // 每个缓存最大容量10000条，LRU自动淘汰
    private static final int MAX_CACHE_SIZE = 10000;
    
    private final Cache<ContactActiveKey, Boolean> contactActiveCache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();
    
    private final Cache<SingleChatPermissionKey, Boolean> singleChatPermissionCache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();
    
    private final Cache<Long, List<Long>> groupMemberIdsCache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .build();
    
    private final Cache<GroupRecallPermissionKey, Boolean> groupRecallPermissionCache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    public Boolean getContactActive(Long ownerUserId, Long peerUserId) {
        return contactActiveCache.getIfPresent(new ContactActiveKey(ownerUserId, peerUserId));
    }

    public void putContactActive(Long ownerUserId, Long peerUserId, boolean active, long ttlMillis) {
        contactActiveCache.put(new ContactActiveKey(ownerUserId, peerUserId), active);
    }

    public Boolean getSingleChatPermission(Long fromUserId, Long toUserId) {
        return singleChatPermissionCache.getIfPresent(new SingleChatPermissionKey(fromUserId, toUserId));
    }

    public void putSingleChatPermission(Long fromUserId, Long toUserId, boolean allowed, long ttlMillis) {
        singleChatPermissionCache.put(new SingleChatPermissionKey(fromUserId, toUserId), allowed);
    }

    public List<Long> getGroupMemberIds(Long groupId) {
        List<Long> value = groupMemberIdsCache.getIfPresent(groupId);
        return value == null ? null : new ArrayList<>(value);
    }

    public void putGroupMemberIds(Long groupId, List<Long> memberIds, long ttlMillis) {
        groupMemberIdsCache.put(groupId, memberIds == null ? List.of() : new ArrayList<>(memberIds));
    }

    public Boolean getGroupRecallPermission(Long groupId, Long operatorUserId, Long targetUserId) {
        return groupRecallPermissionCache.getIfPresent(new GroupRecallPermissionKey(groupId, operatorUserId, targetUserId));
    }

    public void putGroupRecallPermission(Long groupId, Long operatorUserId, Long targetUserId, boolean allowed, long ttlMillis) {
        groupRecallPermissionCache.put(new GroupRecallPermissionKey(groupId, operatorUserId, targetUserId), allowed);
    }

    public void invalidateContactPair(Long leftUserId, Long rightUserId) {
        contactActiveCache.invalidate(new ContactActiveKey(leftUserId, rightUserId));
        contactActiveCache.invalidate(new ContactActiveKey(rightUserId, leftUserId));
        singleChatPermissionCache.invalidate(new SingleChatPermissionKey(leftUserId, rightUserId));
        singleChatPermissionCache.invalidate(new SingleChatPermissionKey(rightUserId, leftUserId));
    }

    public void invalidateGroup(Long groupId) {
        groupMemberIdsCache.invalidate(groupId);
        groupRecallPermissionCache.asMap().keySet().removeIf(key -> key.groupId.equals(groupId));
    }

    private record ContactActiveKey(Long ownerUserId, Long peerUserId) {
    }

    private record SingleChatPermissionKey(Long fromUserId, Long toUserId) {
    }

    private static final class GroupRecallPermissionKey {
        private final Long groupId;
        private final Long operatorUserId;
        private final Long targetUserId;

        private GroupRecallPermissionKey(Long groupId, Long operatorUserId, Long targetUserId) {
            this.groupId = groupId;
            this.operatorUserId = operatorUserId;
            this.targetUserId = targetUserId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GroupRecallPermissionKey that)) {
                return false;
            }
            return Objects.equals(groupId, that.groupId)
                    && Objects.equals(operatorUserId, that.operatorUserId)
                    && Objects.equals(targetUserId, that.targetUserId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, operatorUserId, targetUserId);
        }
    }
}
