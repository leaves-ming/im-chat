package com.ming.imchatserver.netty;

import com.ming.imchatserver.application.model.GroupMessageView;
import io.netty.channel.Channel;

/**
 * 群消息网关推送端口。
 */
public interface GroupPushDispatcher {

    void dispatchGroupPush(Long groupId, GroupMessageView message) throws Exception;

    void notifyGroupRecall(Channel requester, GroupMessageView message) throws Exception;
}
