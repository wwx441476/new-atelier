package com.example.atelier.warning.evaluator;

import com.example.atelier.infra.exception.AtelierException;

/**
 * 限制并发 LLM HTTP 调用数，避免批量预览压垮接口。
 */
public final class LlmConcurrencyLimiter {

    private static final int MAX_CONCURRENT = 4;
    private static final java.util.concurrent.Semaphore SEMAPHORE = new java.util.concurrent.Semaphore(MAX_CONCURRENT);

    private LlmConcurrencyLimiter() {
    }

    public static <T> T withPermit(java.util.concurrent.Callable<T> action) {
        boolean acquired = false;
        try {
            SEMAPHORE.acquire();
            acquired = true;
            return action.call();
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("LLM 调用失败: " + e.getMessage(), e);
        } finally {
            if (acquired) {
                SEMAPHORE.release();
            }
        }
    }
}
