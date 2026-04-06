package com.ming.imchatserver.service.remote;

import com.ming.imapicontract.common.ApiResponse;
import com.ming.imapicontract.message.GetGroupMessageRequest;
import com.ming.imapicontract.message.GroupMessageDTO;
import com.ming.imchatserver.application.model.GroupMessageView;
import com.ming.imchatserver.remote.message.MessageServiceClient;
import com.ming.imchatserver.service.MessageRecallException;
import com.ming.imchatserver.service.query.GroupMessageQueryPort;
import org.springframework.stereotype.Component;

/**
 * 群消息查询远程适配器。
 */
@Component
public class RemoteGroupMessageQueryService implements GroupMessageQueryPort {

    private final MessageServiceClient messageServiceClient;

    public RemoteGroupMessageQueryService(MessageServiceClient messageServiceClient) {
        this.messageServiceClient = messageServiceClient;
    }

    @Override
    public GroupMessageView findByServerMsgId(String serverMsgId) {
        return toGroupMessageView(unwrap(messageServiceClient.getGroupMessage(new GetGroupMessageRequest(serverMsgId))).message());
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
