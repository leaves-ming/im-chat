package com.ming.imchatserver.netty;

import com.ming.imchatserver.service.DistributedCoordinationService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroupPushCoordinatorTest {

    @Test
    void shouldSkipTaskWhenDistributedLockIsNotAcquired() {
        DistributedCoordinationService distributedCoordinationService = mock(DistributedCoordinationService.class);
        when(distributedCoordinationService.executeWithLockOrLocalFallback(
                eq("group_push"),
                eq("101"),
                any(Duration.class),
                any(Duration.class),
                any(),
                any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<CompletableFuture<Void>> fallback = invocation.getArgument(5);
            return fallback.get();
        });

        GroupPushCoordinator coordinator = new GroupPushCoordinator(distributedCoordinationService);
        AtomicInteger runs = new AtomicInteger();

        coordinator.enqueue(101L, () -> {
            runs.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });

        assertEquals(0, runs.get());
    }

    @Test
    void shouldContinueFollowingTasksAfterSkippedExecution() {
        DistributedCoordinationService distributedCoordinationService = mock(DistributedCoordinationService.class);
        AtomicInteger invocations = new AtomicInteger();
        when(distributedCoordinationService.executeWithLockOrLocalFallback(
                eq("group_push"),
                eq("101"),
                any(Duration.class),
                any(Duration.class),
                any(),
                any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<CompletableFuture<Void>> task = invocation.getArgument(4);
            @SuppressWarnings("unchecked")
            Supplier<CompletableFuture<Void>> fallback = invocation.getArgument(5);
            return invocations.incrementAndGet() == 1 ? fallback.get() : task.get();
        });

        GroupPushCoordinator coordinator = new GroupPushCoordinator(distributedCoordinationService);
        AtomicInteger runs = new AtomicInteger();

        coordinator.enqueue(101L, () -> {
            runs.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });
        coordinator.enqueue(101L, () -> {
            runs.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });

        assertEquals(1, runs.get());
    }
}
