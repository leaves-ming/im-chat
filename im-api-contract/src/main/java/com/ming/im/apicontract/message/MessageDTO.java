package com.ming.im.apicontract.message;

import java.util.Date;

/**
 * 单聊消息传输对象。
 */
public class MessageDTO {

    private Long id;
    private String serverMsgId;
    private String clientMsgId;
    private Long fromUserId;
    private Long toUserId;
    private String msgType;
    private String content;
    private String status;
    private Date createdAt;
    private Date deliveredAt;
    private Date ackedAt;
    private Date retractedAt;
    private Long retractedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getServerMsgId() {
        return serverMsgId;
    }

    public void setServerMsgId(String serverMsgId) {
        this.serverMsgId = serverMsgId;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }

    public Long getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(Long fromUserId) {
        this.fromUserId = fromUserId;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public void setToUserId(Long toUserId) {
        this.toUserId = toUserId;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Date deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public Date getAckedAt() {
        return ackedAt;
    }

    public void setAckedAt(Date ackedAt) {
        this.ackedAt = ackedAt;
    }

    public Date getRetractedAt() {
        return retractedAt;
    }

    public void setRetractedAt(Date retractedAt) {
        this.retractedAt = retractedAt;
    }

    public Long getRetractedBy() {
        return retractedBy;
    }

    public void setRetractedBy(Long retractedBy) {
        this.retractedBy = retractedBy;
    }
}
