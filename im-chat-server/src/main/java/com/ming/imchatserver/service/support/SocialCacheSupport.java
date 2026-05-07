package com.ming.imchatserver.service.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.ming.imchatserver.config.CacheProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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

    private final Cache<ContactActiveKey, Boolean> contactActiveCache;
    private final Cache<SingleChatPermissionKey, Boolean> singleChatPermissionCache;
    private final Cache<Long, List<Long>> groupMemberIdsCache;
    private final Cache<GroupRecallPermissionKey, Boolean> groupRecallPermissionCache;
    private final MeterRegistry meterRegistry;

    public SocialCacheSupport(CacheProperties cacheProperties, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        CacheProperties.CacheConfig contactActiveConfig = cacheProperties.getContactActive();
        this.contactActiveCache = Caffeine.newBuilder()
                .maximumSize(contactActiveConfig.getMaxSize())
                .expireAfterWrite(contactActiveConfig.getExpire(), contactActiveConfig.getTimeUnit())
                .recordStats()
                .build();
        bindCacheMetrics("contact_active_cache", contactActiveCache);

        CacheProperties.CacheConfig singleChatConfig = cacheProperties.getSingleChatPermission();
        this.singleChatPermissionCache = Caffeine.newBuilder()
                .maximumSize(singleChatConfig.getMaxSize())
                .expireAfterWrite(singleChatConfig.getExpire(), singleChatConfig.getTimeUnit())
                .recordStats()
                .build();
        bindCacheMetrics("single_chat_permission_cache", singleChatPermissionCache);

        CacheProperties.CacheConfig groupMemberConfig = cacheProperties.getGroupMemberIds();
        this.groupMemberIdsCache = Caffeine.newBuilder()
                .maximumSize(groupMemberConfig.getMaxSize())
                .expireAfterWrite(groupMemberConfig.getExpire(), groupMemberConfig.getTimeUnit())
                .recordStats()
                .build();
        bindCacheMetrics("group_member_ids_cache", groupMemberIdsCache);

        CacheProperties.CacheConfig recallConfig = cacheProperties.getGroupRecallPermission();
        this.groupRecallPermissionCache = Caffeine.newBuilder()
                .maximumSize(recallConfig.getMaxSize())
                .expireAfterWrite(recallConfig.getExpire(), recallConfig.getTimeUnit())
                .recordStats()
                .build();
        bindCacheMetrics("group_recall_permission_cache", groupRecallPermissionCache);
    }

    /**
     * 绑定缓存Metrics到Prometheus
     */
    private <K, V> void bindCacheMetrics(String name, Cache<K, V> cache) {
        CacheStats stats = cache.stats();
        Gauge.builder(name + "_hit_rate", stats, CacheStats::hitRate)
                .description("缓存命中率")
                .register(meterRegistry);
        Gauge.builder(name + "_eviction_count", stats, CacheStats::evictionCount)
                .description("缓存驱逐次数")
                .register(meterRegistry);
        Gauge.builder(name + "_size", cache, Cache::estimatedSize)
                .description("缓存当前大小")
                .register(meterRegistry);
        Gauge.builder(name + "_miss_count", stats, CacheStats::missCount)
                .description("缓存未命中次数")
                .register(meterRegistry);
    }

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
