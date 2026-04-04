package com.ming.im.apicontract.message;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 单聊离线拉取返回。
 */
public class PullOfflineMessagesResponse {

    private List<MessageDTO> messages = new ArrayList<>();
    private boolean hasMore;
    private Date nextCursorCreatedAt;
    private Long nextCursorId;

    public List<MessageDTO> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageDTO> messages) {
        this.messages = messages;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    public Date getNextCursorCreatedAt() {
        return nextCursorCreatedAt;
    }

    public void setNextCursorCreatedAt(Date nextCursorCreatedAt) {
        this.nextCursorCreatedAt = nextCursorCreatedAt;
    }

    public Long getNextCursorId() {
        return nextCursorId;
    }

    public void setNextCursorId(Long nextCursorId) {
        this.nextCursorId = nextCursorId;
    }
}
