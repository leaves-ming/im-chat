package com.ming.imchatserver.netty;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同一 group 的实时推送串行协调器。
 * <p>
 * 顺序语义：
 * - 同一 groupId 的多条消息按入队顺序串行 fanout
 * - 不同 groupId 之间允许并行
 */
@Component
public class GroupPushCoordinator {

    private final ConcurrentHashMap<Long, CompletableFuture<Void>> groupPushTails = new ConcurrentHashMap<>();

    public void enqueue(Long groupId, GroupPushTask task) {
        if (groupId == null || task == null) {
            return;
        }
        groupPushTails.compute(groupId, (key, tail) -> {
            CompletableFuture<Void> previous = tail == null ? CompletableFuture.completedFuture(null) : tail.exceptionally(ex -> null);
            CompletableFuture<Void> next = previous.thenCompose(ignored -> task.run());
            next.whenComplete((ignored, ex) -> groupPushTails.compute(key, (innerKey, current) -> current == next ? null : current));
            return next;
        });
    }

    @FunctionalInterface
    public interface GroupPushTask {
        CompletableFuture<Void> run();
    }
}
