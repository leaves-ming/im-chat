package com.ming.imchatserver.service.support;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * social 远程调用本地缓存。
 */
@Component
public class SocialCacheSupport {

    private final Map<ContactActiveKey, CacheValue<Boolean>> contactActiveCache = new ConcurrentHashMap<>();
    private final Map<SingleChatPermissionKey, CacheValue<Boolean>> singleChatPermissionCache = new ConcurrentHashMap<>();
    private final Map<Long, CacheValue<List<Long>>> groupMemberIdsCache = new ConcurrentHashMap<>();
    private final Map<GroupRecallPermissionKey, CacheValue<Boolean>> groupRecallPermissionCache = new ConcurrentHashMap<>();

    public Boolean getContactActive(Long ownerUserId, Long peerUserId) {
        return getFreshValue(contactActiveCache, new ContactActiveKey(ownerUserId, peerUserId));
    }

    public void putContactActive(Long ownerUserId, Long peerUserId, boolean active, long ttlMillis) {
        putValue(contactActiveCache, new ContactActiveKey(ownerUserId, peerUserId), active, ttlMillis);
    }

    public Boolean getSingleChatPermission(Long fromUserId, Long toUserId) {
        return getFreshValue(singleChatPermissionCache, new SingleChatPermissionKey(fromUserId, toUserId));
    }

    public void putSingleChatPermission(Long fromUserId, Long toUserId, boolean allowed, long ttlMillis) {
        putValue(singleChatPermissionCache, new SingleChatPermissionKey(fromUserId, toUserId), allowed, ttlMillis);
    }

    public List<Long> getGroupMemberIds(Long groupId) {
        List<Long> value = getFreshValue(groupMemberIdsCache, groupId);
        return value == null ? null : new ArrayList<>(value);
    }

    public void putGroupMemberIds(Long groupId, List<Long> memberIds, long ttlMillis) {
        putValue(groupMemberIdsCache, groupId, memberIds == null ? List.of() : new ArrayList<>(memberIds), ttlMillis);
    }

    public Boolean getGroupRecallPermission(Long groupId, Long operatorUserId, Long targetUserId) {
        return getFreshValue(groupRecallPermissionCache, new GroupRecallPermissionKey(groupId, operatorUserId, targetUserId));
    }

    public void putGroupRecallPermission(Long groupId, Long operatorUserId, Long targetUserId, boolean allowed, long ttlMillis) {
        putValue(groupRecallPermissionCache, new GroupRecallPermissionKey(groupId, operatorUserId, targetUserId), allowed, ttlMillis);
    }

    public void invalidateContactPair(Long leftUserId, Long rightUserId) {
        contactActiveCache.remove(new ContactActiveKey(leftUserId, rightUserId));
        contactActiveCache.remove(new ContactActiveKey(rightUserId, leftUserId));
        singleChatPermissionCache.remove(new SingleChatPermissionKey(leftUserId, rightUserId));
        singleChatPermissionCache.remove(new SingleChatPermissionKey(rightUserId, leftUserId));
    }

    public void invalidateGroup(Long groupId) {
        groupMemberIdsCache.remove(groupId);
        groupRecallPermissionCache.keySet().removeIf(key -> key.groupId.equals(groupId));
    }

    private <K, V> V getFreshValue(Map<K, CacheValue<V>> cache, K key) {
        CacheValue<V> value = cache.get(key);
        if (value == null) {
            return null;
        }
        if (value.expireAtMillis < System.currentTimeMillis()) {
            cache.remove(key);
            return null;
        }
        return value.value;
    }

    private <K, V> void putValue(Map<K, CacheValue<V>> cache, K key, V value, long ttlMillis) {
        long safeTtlMillis = Math.max(ttlMillis, 1L);
        cache.put(key, new CacheValue<>(value, System.currentTimeMillis() + safeTtlMillis));
    }

    private record CacheValue<V>(V value, long expireAtMillis) {
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
