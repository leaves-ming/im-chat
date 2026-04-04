package com.ming.im.apicontract.message;

import java.util.Date;

/**
 * 单聊同步游标 DTO。
 */
public class SyncCursorDTO {

    private Date cursorCreatedAt;
    private Long cursorId;

    public SyncCursorDTO() {
    }

    public SyncCursorDTO(Date cursorCreatedAt, Long cursorId) {
        this.cursorCreatedAt = cursorCreatedAt;
        this.cursorId = cursorId;
    }

    public Date getCursorCreatedAt() {
        return cursorCreatedAt;
    }

    public void setCursorCreatedAt(Date cursorCreatedAt) {
        this.cursorCreatedAt = cursorCreatedAt;
    }

    public Long getCursorId() {
        return cursorId;
    }

    public void setCursorId(Long cursorId) {
        this.cursorId = cursorId;
    }
}
