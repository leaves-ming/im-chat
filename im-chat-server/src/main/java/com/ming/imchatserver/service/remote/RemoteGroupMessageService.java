package com.ming.imchatserver.service.remote;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.message.GetGroupMessageRequest;
import com.ming.imapicontract.message.GroupMessageDTO;
import com.ming.imapicontract.message.PersistGroupMessageRequest;
import com.ming.imapicontract.message.PersistGroupMessageResponse;
import com.ming.imapicontract.message.PullGroupOfflineRequest;
import com.ming.imapicontract.message.PullGroupOfflineResponse;
import com.ming.imapicontract.message.RecallGroupMessageRequest;
import com.ming.imapicontract.message.RecallGroupMessageResponse;
import com.ming.imchatserver.application.model.GroupMessagePage;
import com.ming.imchatserver.application.model.GroupMessagePersistResult;
import com.ming.imchatserver.application.model.GroupMessageView;
import com.ming.imchatserver.remote.message.MessageServiceClient;
import com.ming.imchatserver.service.MessageRecallException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 群消息远程服务适配器。
 */
@Component
public class RemoteGroupMessageService {

    private final MessageServiceClient messageServiceClient;

    public RemoteGroupMessageService(MessageServiceClient messageServiceClient) {
        this.messageServiceClient = messageServiceClient;
    }

    public GroupMessagePersistResult persistMessage(Long groupId, Long fromUserId, String clientMsgId, String msgType, String content) {
        PersistGroupMessageResponse response = unwrap(messageServiceClient.persistGroupMessage(
                new PersistGroupMessageRequest(groupId, fromUserId, clientMsgId, msgType, content)));
        return new GroupMessagePersistResult(toGroupMessageView(response.message()));
    }

    public GroupMessagePage pullOffline(Long groupId, Long userId, Long cursorSeq, int limit) {
        PullGroupOfflineResponse response = unwrap(messageServiceClient.pullGroupOffline(
                new PullGroupOfflineRequest(groupId, userId, cursorSeq, limit)));
        List<GroupMessageView> messages = new ArrayList<>();
        if (response.messages() != null) {
            for (GroupMessageDTO item : response.messages()) {
                messages.add(toGroupMessageView(item));
            }
        }
        return new GroupMessagePage(messages, response.hasMore(), response.nextCursorSeq());
    }

    public GroupMessageView findByServerMsgId(String serverMsgId) {
        return toGroupMessageView(unwrap(messageServiceClient.getGroupMessage(new GetGroupMessageRequest(serverMsgId))).message());
    }

    public GroupMessageView recallMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds) {
        RecallGroupMessageResponse response = unwrap(messageServiceClient.recallGroupMessage(
                new RecallGroupMessageRequest(operatorUserId, serverMsgId, recallWindowSeconds)));
        return toGroupMessageView(response.message());
    }

    private GroupMessageView toGroupMessageView(GroupMessageDTO item) {
        if (item == null) {
            return null;
        }
        return new GroupMessageView(
                item.id(),
                item.groupId(),
                item.seq(),
                item.serverMsgId(),
                item.clientMsgId(),
                item.fromUserId(),
                item.msgType(),
                item.content(),
                item.status(),
                item.createdAt(),
                item.retractedAt(),
                item.retractedBy()
        );
    }

    private <T> T unwrap(ApiResponse<T> response) {
        if (response == null) {
            throw new IllegalStateException("message service response is null");
        }
        if (response.isSuccess()) {
            return response.getData();
        }
        String code = response.getCode();
        String message = response.getMessage();
        if ("FORBIDDEN".equals(code)) {
            throw new SecurityException(message);
        }
        if ("INVALID_PARAM".equals(code)) {
            throw new IllegalArgumentException(message);
        }
        throw new MessageRecallException(code == null ? "REMOTE_ERROR" : code, message);
    }
}
