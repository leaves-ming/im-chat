package com.ming.imchatserver.dao;

import java.util.Date;

public class UserProfileDO {
    private Long userId;
    private String nickname;
    private String avatar;
    private Integer sex;
    private Integer activeStatus;
    private Date lastOnlineAt;
    private Date lastOfflineAt;
    private String lastLoginIp;
    private Date updatedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public Integer getSex() { return sex; }
    public void setSex(Integer sex) { this.sex = sex; }
    public Integer getActiveStatus() { return activeStatus; }
    public void setActiveStatus(Integer activeStatus) { this.activeStatus = activeStatus; }
    public Date getLastOnlineAt() { return lastOnlineAt; }
    public void setLastOnlineAt(Date lastOnlineAt) { this.lastOnlineAt = lastOnlineAt; }
    public Date getLastOfflineAt() { return lastOfflineAt; }
    public void setLastOfflineAt(Date lastOfflineAt) { this.lastOfflineAt = lastOfflineAt; }
    public String getLastLoginIp() { return lastLoginIp; }
    public void setLastLoginIp(String lastLoginIp) { this.lastLoginIp = lastLoginIp; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
