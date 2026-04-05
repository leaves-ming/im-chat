package com.ming.imuserservice.service.impl;

import com.ming.imuserservice.config.LoginRiskControlProperties;
import com.ming.imuserservice.service.LoginRiskControlService;
import com.ming.imuserservice.service.RateLimitService;
import org.springframework.stereotype.Service;

/**
 * 登录失败风控实现。
 */
@Service
public class LoginRiskControlServiceImpl implements LoginRiskControlService {

    private final RateLimitService rateLimitService;
    private final LoginRiskControlProperties properties;

    public LoginRiskControlServiceImpl(RateLimitService rateLimitService, LoginRiskControlProperties properties) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
    }

    @Override
    public void onLoginSuccess(String clientIp, String deviceId, String username) {
        reset("ip", clientIp, properties.getIp().getWindowSeconds());
        reset("device", deviceId, properties.getDevice().getWindowSeconds());
        reset("username", username, properties.getUsername().getWindowSeconds());
    }

    @Override
    public boolean onLoginFailure(String clientIp, String deviceId, String username) {
        boolean ipAllowed = check("ip", clientIp, properties.getIp().getLimit(), properties.getIp().getWindowSeconds());
        boolean deviceAllowed = check("device", deviceId, properties.getDevice().getLimit(), properties.getDevice().getWindowSeconds());
        boolean usernameAllowed = check("username", username, properties.getUsername().getLimit(), properties.getUsername().getWindowSeconds());
        return ipAllowed && deviceAllowed && usernameAllowed;
    }

    private boolean check(String dimension, String subject, long limit, long windowSeconds) {
        return rateLimitService.checkAndIncrement("login_fail", dimension, subject, limit, windowSeconds).allowed();
    }

    private void reset(String dimension, String subject, long windowSeconds) {
        rateLimitService.reset("login_fail", dimension, subject, windowSeconds);
    }
}
