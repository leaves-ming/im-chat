package com.ming.im.apicontract.message;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Date;

/**
 * 单聊离线拉取请求。
 */
public class PullOfflineMessagesRequest {

    @NotNull
    private Long userId;
    @NotBlank
    private String deviceId;
    @Min(1)
    private int limit;
    private boolean useCheckpoint;
    private Date cursorCreatedAt;
    private Long cursorId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public boolean isUseCheckpoint() {
        return useCheckpoint;
    }

    public void setUseCheckpoint(boolean useCheckpoint) {
        this.useCheckpoint = useCheckpoint;
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
