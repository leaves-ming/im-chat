package com.ming.imchatserver.application.facade.impl;

import com.ming.imchatserver.application.facade.MessageFacade;
import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.service.MessageService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 单聊消息远程门面。
 */
@Primary
@Component
public class RoutingMessageFacade implements MessageFacade {

    private final RemoteMessageFacade remoteMessageFacade;

    public RoutingMessageFacade(RemoteMessageFacade remoteMessageFacade) {
        this.remoteMessageFacade = remoteMessageFacade;
    }

    @Override
    public ChatPersistResult sendChat(Long fromUserId, Long targetUserId, String clientMsgId, String msgType, String content) {
        return remoteMessageFacade.sendChat(fromUserId, targetUserId, clientMsgId, msgType, content);
    }

    @Override
    public AckReportResult reportAck(Long reporterUserId, String serverMsgId, String targetStatus) {
        return remoteMessageFacade.reportAck(reporterUserId, serverMsgId, targetStatus);
    }

    @Override
    public boolean enqueueStatusNotify(MessageDO message, String status) {
        return false;
    }

    @Override
    public MessageService.CursorPageResult pullOffline(Long userId, String deviceId, MessageService.SyncCursor syncCursor, int limit) {
        return remoteMessageFacade.pullOffline(userId, deviceId, syncCursor, limit);
    }

    @Override
    public MessageService.CursorPageResult loadInitialSync(Long userId, String deviceId, int limit) {
        return remoteMessageFacade.loadInitialSync(userId, deviceId, limit);
    }

    @Override
    public void advanceSyncCursor(Long userId, String deviceId, MessageService.CursorPageResult pageResult) {
        remoteMessageFacade.advanceSyncCursor(userId, deviceId, pageResult);
    }

    @Override
    public MessageDO recallMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds) {
        return remoteMessageFacade.recallMessage(operatorUserId, serverMsgId, recallWindowSeconds);
    }
}
