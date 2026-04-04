package com.ming.im.apicontract.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Date;

/**
 * 游标推进请求。
 */
public class AdvanceCursorRequest {

    @NotNull
    private Long userId;
    @NotBlank
    private String deviceId;
    @NotNull
    private Date cursorCreatedAt;
    @NotNull
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
