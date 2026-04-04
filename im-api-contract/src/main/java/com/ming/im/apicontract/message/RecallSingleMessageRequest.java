package com.ming.im.apicontract.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 单聊撤回请求。
 */
public class RecallSingleMessageRequest {

    @NotNull
    private Long operatorUserId;
    @NotBlank
    private String serverMsgId;
    @NotNull
    private Long recallWindowSeconds;

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public String getServerMsgId() {
        return serverMsgId;
    }

    public void setServerMsgId(String serverMsgId) {
        this.serverMsgId = serverMsgId;
    }

    public Long getRecallWindowSeconds() {
        return recallWindowSeconds;
    }

    public void setRecallWindowSeconds(Long recallWindowSeconds) {
        this.recallWindowSeconds = recallWindowSeconds;
    }
}
