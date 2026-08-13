package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.document.job.DocumentCompareProperties;
import com.example.atelier.infra.exception.AtelierException;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * 统一异常处理 — 保证所有 API 返回 {@link ApiResponse} 格式，前端可正确解包。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Autowired(required = false)
    private DocumentCompareProperties documentCompareProperties;

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

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleUploadSize(Exception ex) {
        String limit = documentCompareProperties != null
                ? documentCompareProperties.formatMaxFileSize()
                : "配置上限";
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof FileSizeLimitExceededException) {
                FileSizeLimitExceededException f = (FileSizeLimitExceededException) cause;
                return ApiResponse.fail("上传文件过大（字段 "
                        + f.getFieldName() + "），单文件上限 " + limit);
            }
            if (cause instanceof SizeLimitExceededException) {
                return ApiResponse.fail("上传请求过大，请减小文件或分批对比（当前单文件上限 " + limit + "）");
            }
            cause = cause.getCause();
        }
        if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("size")) {
            return ApiResponse.fail("上传文件过大，单文件上限 " + limit);
        }
        log.warn("multipart 异常: {}", ex.getMessage());
        return ApiResponse.fail(ex.getMessage() != null ? ex.getMessage() : "文件上传失败");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleUnexpected(Exception ex) {
        log.error("未处理异常", ex);
        return ApiResponse.fail(ex.getMessage() != null ? ex.getMessage() : "服务器内部错误");
    }
}
