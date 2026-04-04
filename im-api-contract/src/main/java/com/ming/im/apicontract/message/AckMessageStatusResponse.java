package com.ming.im.apicontract.message;

import java.util.Date;

/**
 * 单聊 ACK 状态推进返回。
 */
public class AckMessageStatusResponse {

    private MessageDTO message;
    private String status;
    private int updated;
    private Date ackAt;

    public MessageDTO getMessage() {
        return message;
    }

    public void setMessage(MessageDTO message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public Date getAckAt() {
        return ackAt;
    }

    public void setAckAt(Date ackAt) {
        this.ackAt = ackAt;
    }
}
