package com.ming.imuserservice.controller;

import com.ming.imapicontract.common.ApiResponse;
import com.ming.imapicontract.user.IntrospectTokenRequest;
import com.ming.imapicontract.user.IntrospectTokenResponse;
import com.ming.imapicontract.user.QueryUserByIdRequest;
import com.ming.imapicontract.user.QueryUserByUsernameRequest;
import com.ming.imapicontract.user.UserApiPaths;
import com.ming.imapicontract.user.UserDTO;
import com.ming.imuserservice.service.AuthApplicationService;
import com.ming.imuserservice.service.UserDomainService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户域内部接口。
 */
@RestController
@RequestMapping(UserApiPaths.BASE)
public class UserCommandController {

    private final AuthApplicationService authApplicationService;
    private final UserDomainService userDomainService;

    public UserCommandController(AuthApplicationService authApplicationService, UserDomainService userDomainService) {
        this.authApplicationService = authApplicationService;
        this.userDomainService = userDomainService;
    }

    @PostMapping(UserApiPaths.AUTH_INTROSPECT)
    public ApiResponse<IntrospectTokenResponse> introspect(@RequestBody IntrospectTokenRequest request) {
        IntrospectTokenResponse response = authApplicationService.introspect(request == null ? null : request.token());
        if (!response.active()) {
            return ApiResponse.failure("INVALID_TOKEN", "invalid token");
        }
        return ApiResponse.success(response);
    }

    @PostMapping(UserApiPaths.QUERY_BY_ID)
    public ApiResponse<UserDTO> queryById(@RequestBody QueryUserByIdRequest request) {
        return userDomainService.findUserById(request == null ? null : request.userId())
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.failure("USER_NOT_FOUND", "user not found"));
    }

    @PostMapping(UserApiPaths.QUERY_BY_USERNAME)
    public ApiResponse<UserDTO> queryByUsername(@RequestBody QueryUserByUsernameRequest request) {
        return userDomainService.findUserByUsername(request == null ? null : request.username())
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.failure("USER_NOT_FOUND", "user not found"));
    }
}
