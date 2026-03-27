package com.ming.imchatserver.service;

import com.ming.imchatserver.dao.GroupMessageDO;

import java.util.List;

/**
 * 群消息服务接口（MVP）。
 */
public interface GroupMessageService {

    PersistResult persistTextMessage(Long groupId, Long fromUserId, String clientMsgId, String content);

    PullResult pullOffline(Long groupId, Long userId, Long cursorSeq, int limit);

    class PersistResult {
        private final GroupMessageDO message;

        public PersistResult(GroupMessageDO message) {
            this.message = message;
        }

        public GroupMessageDO getMessage() {
            return message;
        }
    }

    class PullResult {
        private final List<GroupMessageDO> messages;
        private final boolean hasMore;
        private final Long nextCursorSeq;

        public PullResult(List<GroupMessageDO> messages, boolean hasMore, Long nextCursorSeq) {
            this.messages = messages;
            this.hasMore = hasMore;
            this.nextCursorSeq = nextCursorSeq;
        }

        public List<GroupMessageDO> getMessages() {
            return messages;
        }

        public boolean isHasMore() {
            return hasMore;
        }

        public Long getNextCursorSeq() {
            return nextCursorSeq;
        }
    }
}
