package com.ming.imchatserver.application.facade;

import com.ming.imchatserver.dao.MessageDO;
import com.ming.imchatserver.service.MessageService;

import java.util.Date;

/**
 * 单聊消息应用门面。
 */
public interface MessageFacade {

    ChatPersistResult sendChat(Long fromUserId,
                               Long targetUserId,
                               String clientMsgId,
                               String msgType,
                               String content);

    AckReportResult reportAck(Long reporterUserId, String serverMsgId, String targetStatus);

    boolean enqueueStatusNotify(MessageDO message, String status);

    MessageService.CursorPageResult pullOffline(Long userId,
                                                String deviceId,
                                                MessageService.SyncCursor syncCursor,
                                                int limit);

    MessageService.CursorPageResult loadInitialSync(Long userId, String deviceId, int limit);

    void advanceSyncCursor(Long userId, String deviceId, MessageService.CursorPageResult pageResult);

    MessageDO recallMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds);

    record ChatPersistResult(String clientMsgId, String serverMsgId, boolean createdNew) {
    }

    record AckReportResult(MessageDO message, String status, int updated, Date ackAt) {
    }
}
