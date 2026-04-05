package com.ming.imsocialservice.controller;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imsocialservice.service.GroupBizException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * social 服务统一异常处理。
 */
@RestControllerAdvice
public class SocialExceptionHandler {

    @ExceptionHandler(GroupBizException.class)
    public ApiResponse<Void> handleGroupBizException(GroupBizException ex) {
        return ApiResponse.failure(ex.getCode().name(), ex.getMessage());
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
