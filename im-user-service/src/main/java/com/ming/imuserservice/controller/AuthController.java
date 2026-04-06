package com.ming.imuserservice.controller;

import com.ming.imapicontract.common.ApiResponse;
import com.ming.imapicontract.user.LoginRequest;
import com.ming.imapicontract.user.LoginResponse;
import com.ming.imapicontract.user.UserApiPaths;
import com.ming.imuserservice.service.AuthApplicationService;
import com.ming.imuserservice.service.impl.AuthApplicationServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证入口。
 */
@RestController
public class AuthController {

    private final AuthApplicationService authApplicationService;

    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    @PostMapping("/api/auth/login")
    public Map<String, Object> login(@RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        LoginResponse response = authApplicationService.login(enrich(request, httpServletRequest));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", response.token());
        data.put("userId", response.userId());
        data.put("expiresIn", response.expiresIn());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", 0);
        payload.put("msg", "ok");
        payload.put("data", data);
        return payload;
    }

    @PostMapping(UserApiPaths.BASE + UserApiPaths.AUTH_LOGIN)
    public ApiResponse<LoginResponse> internalLogin(@RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        try {
            return ApiResponse.success(authApplicationService.login(enrich(request, httpServletRequest)));
        } catch (AuthApplicationServiceImpl.TooManyLoginAttemptsException ex) {
            return ApiResponse.failure("RATE_LIMITED", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() == null ? "bad request" : ex.getMessage();
            if ("invalid credentials".equals(message)) {
                return ApiResponse.failure("INVALID_CREDENTIALS", message);
            }
            return ApiResponse.failure("INVALID_PARAM", message);
        } catch (Exception ex) {
            return ApiResponse.failure("INTERNAL_ERROR", ex.getMessage() == null ? "internal error" : ex.getMessage());
        }
    }

    private LoginRequest enrich(LoginRequest request, HttpServletRequest httpServletRequest) {
        if (request == null) {
            return null;
        }
        String clientIp = request.clientIp();
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = resolveClientIp(httpServletRequest);
        }
        String deviceId = request.deviceId();
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = httpServletRequest.getHeader("X-Device-Id");
        }
        return new LoginRequest(request.username(), request.password(), clientIp, deviceId);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                ip = xff.split(",")[0].trim();
            }
        }
        return ip == null || ip.isBlank() ? request.getRemoteAddr() : ip;
    }
}
