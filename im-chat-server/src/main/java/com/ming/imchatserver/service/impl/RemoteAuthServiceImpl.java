package com.ming.imchatserver.service.impl;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.user.IntrospectTokenRequest;
import com.ming.imapicontract.user.IntrospectTokenResponse;
import com.ming.imapicontract.user.LoginRequest;
import com.ming.imapicontract.user.LoginResponse;
import com.ming.imchatserver.remote.user.UserServiceClient;
import com.ming.imchatserver.service.AuthService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * gateway 侧认证适配，核心认证逻辑全部下沉到 im-user-service。
 */
@Service
public class RemoteAuthServiceImpl implements AuthService {

    private final UserServiceClient userServiceClient;

    public RemoteAuthServiceImpl(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    @Override
    public AuthResult login(String username, String password, String clientIp, String deviceId) {
        ApiResponse<LoginResponse> response;
        try {
            response = userServiceClient.login(new LoginRequest(username, password, clientIp, deviceId));
        } catch (RuntimeException ex) {
            AuthResult result = new AuthResult();
            result.success = false;
            result.errorCode = "REMOTE_UNAVAILABLE";
            result.errorMessage = ex.getMessage();
            return result;
        }
        if (response == null) {
            AuthResult result = new AuthResult();
            result.success = false;
            result.errorCode = "REMOTE_UNAVAILABLE";
            result.errorMessage = "user service response is null";
            return result;
        }
        if (!response.isSuccess() || response.getData() == null) {
            AuthResult result = new AuthResult();
            result.success = false;
            result.errorCode = response.getCode();
            result.errorMessage = response.getMessage();
            return result;
        }
        LoginResponse data = response.getData();
        AuthResult result = new AuthResult();
        result.success = true;
        result.userId = data.userId() == null ? 0L : data.userId();
        result.token = data.token();
        result.expiresIn = data.expiresIn() == null ? 0L : data.expiresIn();
        return result;
    }

    @Override
    public boolean verifyToken(String token) {
        return parseToken(token).isPresent();
    }

    @Override
    public Optional<AuthUser> parseToken(String token) {
        ApiResponse<IntrospectTokenResponse> response;
        try {
            response = userServiceClient.introspect(new IntrospectTokenRequest(token));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
        if (response == null || !response.isSuccess() || response.getData() == null || !response.getData().active()) {
            return Optional.empty();
        }
        IntrospectTokenResponse data = response.getData();
        AuthUser user = new AuthUser();
        user.userId = data.userId() == null ? 0L : data.userId();
        user.username = data.username();
        return Optional.of(user);
    }
}
