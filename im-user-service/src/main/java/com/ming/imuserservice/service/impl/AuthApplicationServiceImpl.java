package com.ming.imuserservice.service.impl;

import com.ming.imapicontract.user.IntrospectTokenResponse;
import com.ming.imapicontract.user.LoginRequest;
import com.ming.imapicontract.user.LoginResponse;
import com.ming.imuserservice.dao.UserCoreDO;
import com.ming.imuserservice.service.AuthApplicationService;
import com.ming.imuserservice.service.JwtTokenService;
import com.ming.imuserservice.service.LoginRiskControlService;
import com.ming.imuserservice.service.UserDomainService;
import org.springframework.stereotype.Service;

/**
 * 认证应用服务实现。
 */
@Service
public class AuthApplicationServiceImpl implements AuthApplicationService {

    private final UserDomainService userDomainService;
    private final JwtTokenService jwtTokenService;
    private final LoginRiskControlService loginRiskControlService;

    public AuthApplicationServiceImpl(UserDomainService userDomainService,
                                      JwtTokenService jwtTokenService,
                                      LoginRiskControlService loginRiskControlService) {
        this.userDomainService = userDomainService;
        this.jwtTokenService = jwtTokenService;
        this.loginRiskControlService = loginRiskControlService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        validate(request);
        UserCoreDO userCoreDO = userDomainService.findCoreByUsername(request.username()).orElse(null);
        if (userCoreDO == null) {
            boolean allowed = loginRiskControlService.onLoginFailure(request.clientIp(), request.deviceId(), request.username());
            if (!allowed) {
                throw new TooManyLoginAttemptsException("too many login failures");
            }
            throw new IllegalArgumentException("invalid credentials");
        }
        boolean matched = userDomainService.verifyPassword(request.password(), userCoreDO.getPasswordHash());
        if (!matched) {
            boolean allowed = loginRiskControlService.onLoginFailure(request.clientIp(), request.deviceId(), request.username());
            if (!allowed) {
                throw new TooManyLoginAttemptsException("too many login failures");
            }
            throw new IllegalArgumentException("invalid credentials");
        }
        loginRiskControlService.onLoginSuccess(request.clientIp(), request.deviceId(), request.username());
        String token = jwtTokenService.issueToken(userCoreDO.getUserId(), userCoreDO.getUsername());
        return new LoginResponse(userCoreDO.getUserId(), userCoreDO.getUsername(), token, jwtTokenService.expiresInSeconds());
    }

    @Override
    public IntrospectTokenResponse introspect(String token) {
        return jwtTokenService.introspect(token);
    }

    private void validate(LoginRequest request) {
        if (request == null
                || request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("username and password are required");
        }
    }

    /**
     * 登录失败超过阈值。
     */
    public static class TooManyLoginAttemptsException extends RuntimeException {
        public TooManyLoginAttemptsException(String message) {
            super(message);
        }
    }
}
