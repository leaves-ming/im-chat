package com.ming.common.exception;

import com.ming.common.remote.RemoteCallException;
import com.ming.imapicontract.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局统一异常处理器，所有服务自动复用
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        return ApiResponse.failure("PARAM_ERROR", e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ApiResponse<Void> handleSecurityException(SecurityException e) {
        return ApiResponse.failure("PERMISSION_DENIED", e.getMessage());
    }

    @ExceptionHandler(RemoteCallException.class)
    public ApiResponse<Void> handleRemoteCallException(RemoteCallException e) {
        log.warn("RPC调用失败: {}", e.getMessage());
        return ApiResponse.failure("REMOTE_CALL_ERROR", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        return ApiResponse.failure("SYSTEM_ERROR", "系统繁忙，请稍后重试");
    }
}
