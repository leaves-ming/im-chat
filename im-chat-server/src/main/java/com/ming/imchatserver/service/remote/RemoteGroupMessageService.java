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
import com.ming.imchatserver.dao.GroupMessageDO;
import com.ming.imchatserver.remote.message.MessageServiceClient;
import com.ming.imchatserver.service.GroupMessageService;
import com.ming.imchatserver.service.MessageRecallException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 群消息远程服务适配器。
 */
@Primary
@Component
public class RemoteGroupMessageService implements GroupMessageService {

    private final MessageServiceClient messageServiceClient;

    public RemoteGroupMessageService(MessageServiceClient messageServiceClient) {
        this.messageServiceClient = messageServiceClient;
    }

    @Override
    public PersistResult persistMessage(Long groupId, Long fromUserId, String clientMsgId, String msgType, String content) {
        PersistGroupMessageResponse response = unwrap(messageServiceClient.persistGroupMessage(
                new PersistGroupMessageRequest(groupId, fromUserId, clientMsgId, msgType, content)));
        return new PersistResult(toGroupMessageDO(response.message()));
    }

    @Override
    public PullResult pullOffline(Long groupId, Long userId, Long cursorSeq, int limit) {
        PullGroupOfflineResponse response = unwrap(messageServiceClient.pullGroupOffline(
                new PullGroupOfflineRequest(groupId, userId, cursorSeq, limit)));
        List<GroupMessageDO> messages = new ArrayList<>();
        if (response.messages() != null) {
            for (GroupMessageDTO item : response.messages()) {
                messages.add(toGroupMessageDO(item));
            }
        }
        return new PullResult(messages, response.hasMore(), response.nextCursorSeq());
    }

    @Override
    public GroupMessageDO findByServerMsgId(String serverMsgId) {
        return toGroupMessageDO(unwrap(messageServiceClient.getGroupMessage(new GetGroupMessageRequest(serverMsgId))).message());
    }

    @Override
    public GroupMessageDO recallMessage(Long operatorUserId, String serverMsgId, long recallWindowSeconds) {
        RecallGroupMessageResponse response = unwrap(messageServiceClient.recallGroupMessage(
                new RecallGroupMessageRequest(operatorUserId, serverMsgId, recallWindowSeconds)));
        return toGroupMessageDO(response.message());
    }

    private GroupMessageDO toGroupMessageDO(GroupMessageDTO item) {
        if (item == null) {
            return null;
        }
        GroupMessageDO target = new GroupMessageDO();
        target.setId(item.id());
        target.setGroupId(item.groupId());
        target.setSeq(item.seq());
        target.setServerMsgId(item.serverMsgId());
        target.setClientMsgId(item.clientMsgId());
        target.setFromUserId(item.fromUserId());
        target.setMsgType(item.msgType());
        target.setContent(item.content());
        target.setStatus(item.status());
        target.setCreatedAt(item.createdAt());
        target.setRetractedAt(item.retractedAt());
        target.setRetractedBy(item.retractedBy());
        return target;
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
