package com.ming.imapicontract.message;

import java.util.List;

/**
 * 群离线消息拉取响应。
 */
public record PullGroupOfflineResponse(List<GroupMessageDTO> messages,
                                       boolean hasMore,
                                       Long nextCursorSeq) {
}
