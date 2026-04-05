package com.ming.imuserservice.controller;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imuserservice.service.impl.AuthApplicationServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户服务异常处理。
 */
@RestControllerAdvice
public class UserExceptionHandler {

    @ExceptionHandler(AuthApplicationServiceImpl.TooManyLoginAttemptsException.class)
    public ResponseEntity<Object> handleTooManyLoginAttempts(AuthApplicationServiceImpl.TooManyLoginAttemptsException ex,
                                                             HttpServletRequest request) {
        return responseOf(request, "RATE_LIMITED", ex.getMessage(), 429);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String message = ex.getMessage() == null ? "bad request" : ex.getMessage();
        String code = "INVALID_PARAM";
        int legacyCode = 400;
        if ("invalid credentials".equals(message)) {
            code = "INVALID_CREDENTIALS";
            legacyCode = 401;
        }
        return responseOf(request, code, message, legacyCode);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnknown(Exception ex, HttpServletRequest request) {
        return responseOf(request, "INTERNAL_ERROR", ex.getMessage() == null ? "internal error" : ex.getMessage(), 500);
    }

    private ResponseEntity<Object> responseOf(HttpServletRequest request, String code, String message, int legacyCode) {
        String uri = request == null ? "" : request.getRequestURI();
        if (uri.startsWith("/api/user/")) {
            return ResponseEntity.status(HttpStatus.valueOf(Math.min(Math.max(legacyCode, 200), 599)))
                    .body(ApiResponse.failure(code, message));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", legacyCode);
        payload.put("msg", message);
        return ResponseEntity.status(HttpStatus.valueOf(Math.min(Math.max(legacyCode, 200), 599))).body(payload);
    }
}
