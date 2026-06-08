package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.infra.exception.AtelierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理 — 保证所有 API 返回 {@link ApiResponse} 格式，前端可正确解包。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AtelierException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleAtelierException(AtelierException ex) {
        return ApiResponse.fail(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return ApiResponse.fail(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleUnexpected(Exception ex) {
        log.error("未处理异常", ex);
        return ApiResponse.fail(ex.getMessage() != null ? ex.getMessage() : "服务器内部错误");
    }
}
