package com.ming.imchatserver.application.facade;

import com.ming.imchatserver.application.model.GroupMessagePage;
import com.ming.imchatserver.application.model.GroupMessagePersistResult;
import com.ming.imchatserver.application.model.GroupMessageView;
import com.ming.imchatserver.application.model.SingleMessagePage;
import com.ming.imchatserver.application.model.SingleMessageView;
import com.ming.imchatserver.application.model.SingleSyncCursor;

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

    boolean enqueueStatusNotify(SingleMessageView message, String status);

    SingleMessagePage pullOffline(Long userId, String deviceId, SingleSyncCursor syncCursor, int limit);

    SingleMessagePage loadInitialSync(Long userId, String deviceId, int limit);

    void advanceSyncCursor(Long userId, String deviceId, SingleMessagePage pageResult);

    SingleMessageView recallMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds);

    GroupMessagePersistResult sendGroupChat(Long groupId,
                                            Long fromUserId,
                                            String clientMsgId,
                                            String msgType,
                                            String content);

    GroupMessagePage pullGroupOffline(Long groupId, Long userId, Long cursorSeq, int limit);

    GroupMessageView recallGroupMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds);

    record ChatPersistResult(String clientMsgId, String serverMsgId, boolean createdNew) {
    }

    record AckReportResult(SingleMessageView message, String status, int updated, Date ackAt) {
    }
}
