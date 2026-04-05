package com.ming.imuserservice.service;

import com.ming.imapicontract.user.IntrospectTokenResponse;
import com.ming.imapicontract.user.LoginRequest;
import com.ming.imapicontract.user.LoginResponse;

/**
 * 认证应用服务。
 */
public interface AuthApplicationService {

    LoginResponse login(LoginRequest request);

    IntrospectTokenResponse introspect(String token);
}
