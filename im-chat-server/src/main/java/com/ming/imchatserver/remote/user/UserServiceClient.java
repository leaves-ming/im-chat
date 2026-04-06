package com.ming.imchatserver.remote.user;

import com.ming.imapicontract.common.ApiResponse;
import com.ming.imapicontract.user.IntrospectTokenRequest;
import com.ming.imapicontract.user.IntrospectTokenResponse;
import com.ming.imapicontract.user.LoginRequest;
import com.ming.imapicontract.user.LoginResponse;
import com.ming.imapicontract.user.QueryUserByIdRequest;
import com.ming.imapicontract.user.QueryUserByUsernameRequest;
import com.ming.imapicontract.user.UserApiPaths;
import com.ming.imapicontract.user.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 用户服务远程客户端。
 */
@FeignClient(
        name = "im-user-service",
        contextId = "userServiceClient",
        path = UserApiPaths.BASE
)
public interface UserServiceClient {

    @PostMapping(UserApiPaths.AUTH_LOGIN)
    ApiResponse<LoginResponse> login(@RequestBody LoginRequest request);

    @PostMapping(UserApiPaths.AUTH_INTROSPECT)
    ApiResponse<IntrospectTokenResponse> introspect(@RequestBody IntrospectTokenRequest request);

    @PostMapping(UserApiPaths.QUERY_BY_ID)
    ApiResponse<UserDTO> queryById(@RequestBody QueryUserByIdRequest request);

    @PostMapping(UserApiPaths.QUERY_BY_USERNAME)
    ApiResponse<UserDTO> queryByUsername(@RequestBody QueryUserByUsernameRequest request);
}
