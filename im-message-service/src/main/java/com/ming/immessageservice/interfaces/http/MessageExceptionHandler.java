package com.ming.immessageservice.interfaces.http;

import com.ming.imapicontract.common.ApiResponse;
import com.ming.immessageservice.domain.exception.MessageRpcException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 消息服务统一异常处理。
 */
@RestControllerAdvice
public class MessageExceptionHandler {

    @ExceptionHandler(MessageRpcException.class)
    public ApiResponse<Void> handleMessageRpcException(MessageRpcException ex) {
        return ApiResponse.failure(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ApiResponse.failure("INVALID_PARAM", ex.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ApiResponse<Void> handleSecurityException(SecurityException ex) {
        return ApiResponse.failure("FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        return ApiResponse.failure("INTERNAL_ERROR", ex.getMessage());
    }
}
