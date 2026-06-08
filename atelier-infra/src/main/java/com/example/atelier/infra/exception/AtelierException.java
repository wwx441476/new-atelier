package com.example.atelier.infra.exception;

/**
 * 统一运行时异常 — 对应 bd-platform 的 DmpException。
 */
public class AtelierException extends RuntimeException {

    public AtelierException(String message) {
        super(message);
    }

    public AtelierException(String message, Throwable cause) {
        super(message, cause);
    }

    public AtelierException(Throwable cause) {
        super(cause);
    }
}
