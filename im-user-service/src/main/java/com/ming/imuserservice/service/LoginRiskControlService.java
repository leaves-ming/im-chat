package com.ming.imuserservice.service;

/**
 * 登录风控服务。
 */
public interface LoginRiskControlService {

    void onLoginSuccess(String clientIp, String deviceId, String username);

    boolean onLoginFailure(String clientIp, String deviceId, String username);
}
