package com.ming.im.apicontract.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 单聊 ACK 状态推进请求。
 */
public class AckMessageStatusRequest {

    @NotNull
    private Long reporterUserId;
    @NotBlank
    private String serverMsgId;
    @NotBlank
    private String targetStatus;

    public Long getReporterUserId() {
        return reporterUserId;
    }

    public void setReporterUserId(Long reporterUserId) {
        this.reporterUserId = reporterUserId;
    }

    public String getServerMsgId() {
        return serverMsgId;
    }

    public void setServerMsgId(String serverMsgId) {
        this.serverMsgId = serverMsgId;
    }

    public String getTargetStatus() {
        return targetStatus;
    }

    public void setTargetStatus(String targetStatus) {
        this.targetStatus = targetStatus;
    }
}
