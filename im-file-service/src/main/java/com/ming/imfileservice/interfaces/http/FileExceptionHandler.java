package com.ming.imfileservice.interfaces.http;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imfileservice.file.FileAccessDeniedException;
import com.ming.imfileservice.file.FileNotFoundBizException;
import com.ming.imfileservice.service.FileTokenBizException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 文件服务统一异常处理。
 */
@RestControllerAdvice
public class FileExceptionHandler {

    @ExceptionHandler(FileTokenBizException.class)
    public ApiResponse<Void> handleFileTokenBizException(FileTokenBizException ex) {
        return ApiResponse.failure(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ApiResponse.failure("INVALID_PARAM", ex.getMessage());
    }

    @ExceptionHandler(FileAccessDeniedException.class)
    public ApiResponse<Void> handleFileAccessDeniedException(FileAccessDeniedException ex) {
        return ApiResponse.failure("FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(FileNotFoundBizException.class)
    public ApiResponse<Void> handleFileNotFoundBizException(FileNotFoundBizException ex) {
        return ApiResponse.failure("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        return ApiResponse.failure("INTERNAL_ERROR", ex.getMessage());
    }
}
