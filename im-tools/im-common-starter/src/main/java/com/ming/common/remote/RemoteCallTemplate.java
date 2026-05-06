package com.ming.common.remote;

import com.ming.imapicontract.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * 远程调用通用模板，统一异常处理、结果解析、降级逻辑
 */
public class RemoteCallTemplate {

    private static final Logger log = LoggerFactory.getLogger(RemoteCallTemplate.class);

    /**
     * 执行远程调用，自动解析ApiResponse结果，处理异常
     * @param supplier 远程调用逻辑
     * @param serviceName 服务名称，用于日志和异常提示
     * @return 解析后的业务数据
     * @param <T> 返回数据类型
     */
    public static <T> T execute(Supplier<ApiResponse<T>> supplier, String serviceName) {
        ApiResponse<T> response;
        try {
            response = supplier.get();
        } catch (RuntimeException e) {
            log.error("调用{}服务异常", serviceName, e);
            throw new RemoteCallException("REMOTE_UNAVAILABLE", serviceName + "服务调用失败: " + e.getMessage());
        }

        if (response == null) {
            log.error("调用{}服务返回空响应", serviceName);
            throw new RemoteCallException("REMOTE_UNAVAILABLE", serviceName + "服务返回空响应");
        }

        if (response.isSuccess()) {
            return response.getData();
        }

        log.warn("调用{}服务返回错误，code: {}, message: {}", serviceName, response.getCode(), response.getMessage());
        throw mapToException(response, serviceName);
    }

    /**
     * 执行远程调用，带降级逻辑，调用失败时返回默认值
     * @param supplier 远程调用逻辑
     * @param serviceName 服务名称
     * @param fallback 降级默认值
     * @return 解析后的业务数据或降级值
     * @param <T> 返回数据类型
     */
    public static <T> T executeWithFallback(Supplier<ApiResponse<T>> supplier, String serviceName, T fallback) {
        try {
            return execute(supplier, serviceName);
        } catch (Exception e) {
            log.warn("调用{}服务失败，使用降级值", serviceName, e);
            return fallback;
        }
    }

    /**
     * 异常映射，将远程返回的错误码转换为对应异常
     */
    private static RuntimeException mapToException(ApiResponse<?> response, String serviceName) {
        String code = response.getCode() == null ? "REMOTE_ERROR" : response.getCode();
        String message = response.getMessage() == null || response.getMessage().isBlank()
                ? serviceName + "服务调用失败"
                : response.getMessage();

        switch (code) {
            case "FORBIDDEN":
                return new SecurityException(message);
            case "INVALID_PARAM":
                return new IllegalArgumentException(message);
            case "REMOTE_UNAVAILABLE":
                return new RemoteCallException(code, message);
            default:
                return new RemoteCallException(code, message);
        }
    }
}
