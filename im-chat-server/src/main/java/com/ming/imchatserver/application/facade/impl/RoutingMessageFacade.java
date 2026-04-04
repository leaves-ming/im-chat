package com.ming.imchatserver.application.facade.impl;

import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.config.MessageRouteProperties;
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 单聊消息路由门面，支持本地/远程灰度切换。
 */
@Primary
@Component
public class RoutingMessageFacade implements MessageFacade {

    private static final Logger logger = LoggerFactory.getLogger(RoutingMessageFacade.class);

    private final MessageFacadeImpl localMessageFacade;
    private final RemoteMessageFacade remoteMessageFacade;
    private final MessageRouteProperties messageRouteProperties;

    public RoutingMessageFacade(MessageFacadeImpl localMessageFacade,
                                RemoteMessageFacade remoteMessageFacade,
                                MessageRouteProperties messageRouteProperties) {
        this.localMessageFacade = localMessageFacade;
        this.remoteMessageFacade = remoteMessageFacade;
        this.messageRouteProperties = messageRouteProperties;
    }

    @Override
    public ChatPersistResult sendChat(Long fromUserId, Long targetUserId, String clientMsgId, String msgType, String content) {
        return execute(() -> remoteMessageFacade.sendChat(fromUserId, targetUserId, clientMsgId, msgType, content),
                () -> localMessageFacade.sendChat(fromUserId, targetUserId, clientMsgId, msgType, content),
                "sendChat");
    }

    @Override
    public AckReportResult reportAck(Long reporterUserId, String serverMsgId, String targetStatus) {
        return execute(() -> remoteMessageFacade.reportAck(reporterUserId, serverMsgId, targetStatus),
                () -> localMessageFacade.reportAck(reporterUserId, serverMsgId, targetStatus),
                "reportAck");
    }

    @Override
    public boolean enqueueStatusNotify(MessageDO message, String status) {
        if (!messageRouteProperties.isRemoteEnabled()) {
            return localMessageFacade.enqueueStatusNotify(message, status);
        }
        return false;
    }

    @Override
    public MessageService.CursorPageResult pullOffline(Long userId, String deviceId, MessageService.SyncCursor syncCursor, int limit) {
        return execute(() -> remoteMessageFacade.pullOffline(userId, deviceId, syncCursor, limit),
                () -> localMessageFacade.pullOffline(userId, deviceId, syncCursor, limit),
                "pullOffline");
    }

    @Override
    public MessageService.CursorPageResult loadInitialSync(Long userId, String deviceId, int limit) {
        return execute(() -> remoteMessageFacade.loadInitialSync(userId, deviceId, limit),
                () -> localMessageFacade.loadInitialSync(userId, deviceId, limit),
                "loadInitialSync");
    }

    @Override
    public void advanceSyncCursor(Long userId, String deviceId, MessageService.CursorPageResult pageResult) {
        executeVoid(() -> remoteMessageFacade.advanceSyncCursor(userId, deviceId, pageResult),
                () -> localMessageFacade.advanceSyncCursor(userId, deviceId, pageResult),
                "advanceSyncCursor");
    }

    @Override
    public MessageDO recallMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds) {
        return execute(() -> remoteMessageFacade.recallMessage(operatorUserId, serverMsgId, recallWindowSeconds),
                () -> localMessageFacade.recallMessage(operatorUserId, serverMsgId, recallWindowSeconds),
                "recallMessage");
    }

    private <T> T execute(ThrowingSupplier<T> remoteSupplier, ThrowingSupplier<T> localSupplier, String action) {
        if (!messageRouteProperties.isRemoteEnabled()) {
            return localSupplier.get();
        }
        try {
            return remoteSupplier.get();
        } catch (RuntimeException ex) {
            if (!messageRouteProperties.isFallbackToLocalOnError()) {
                throw ex;
            }
            logger.warn("message remote route failed, fallback to local action={}", action, ex);
            return localSupplier.get();
        }
    }

    private void executeVoid(ThrowingRunnable remoteRunnable, ThrowingRunnable localRunnable, String action) {
        execute(() -> {
            remoteRunnable.run();
            return null;
        }, () -> {
            localRunnable.run();
            return null;
        }, action);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
