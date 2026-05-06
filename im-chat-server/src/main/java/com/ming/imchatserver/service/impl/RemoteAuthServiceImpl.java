package com.ming.imchatserver.service.impl;

import com.ming.common.remote.RemoteCallTemplate;
import com.ming.imapicontract.common.ApiResponse;
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

    private static final String SERVICE_NAME = "user-service";

    private final UserServiceClient userServiceClient;

    public RemoteAuthServiceImpl(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    @Override
    public AuthResult login(String username, String password, String clientIp, String deviceId) {
        try {
            LoginResponse data = RemoteCallTemplate.execute(() ->
                    userServiceClient.login(new LoginRequest(username, password, clientIp, deviceId)), SERVICE_NAME);
            AuthResult result = new AuthResult();
            result.success = true;
            result.userId = data.userId() == null ? 0L : data.userId();
            result.token = data.token();
            result.expiresIn = data.expiresIn() == null ? 0L : data.expiresIn();
            return result;
        } catch (Exception e) {
            AuthResult result = new AuthResult();
            result.success = false;
            result.errorCode = "REMOTE_UNAVAILABLE";
            result.errorMessage = e.getMessage();
            return result;
        }
    }

    @Override
    public boolean verifyToken(String token) {
        return parseToken(token).isPresent();
    }

    @Override
    public Optional<AuthUser> parseToken(String token) {
        try {
            IntrospectTokenResponse data = RemoteCallTemplate.execute(() ->
                    userServiceClient.introspect(new IntrospectTokenRequest(token)), SERVICE_NAME);
            if (data == null || !data.active()) {
                return Optional.empty();
            }
            AuthUser user = new AuthUser();
            user.userId = data.userId() == null ? 0L : data.userId();
            user.username = data.username();
            return Optional.of(user);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
