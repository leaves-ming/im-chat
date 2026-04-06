package com.ming.imchatserver.service.query;

import com.ming.imchatserver.application.model.GroupMessageView;

/**
 * 群消息只读查询端口。
 */
public interface GroupMessageQueryPort {

    GroupMessageView findByServerMsgId(String serverMsgId);
}
