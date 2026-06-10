package com.example.atelier.api.job;

import com.example.atelier.domain.warning.WarningRuleJob;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class JobSseHub {

    private static final Logger log = LoggerFactory.getLogger(JobSseHub.class);
    private static final long SSE_TIMEOUT_MS = 30L * 60 * 1000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> jobEmitters =
            new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribeJob(String jobId) {
        SseEmitter emitter = createEmitter();
        jobEmitters.computeIfAbsent(jobId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeJobEmitter(jobId, emitter));
        emitter.onTimeout(() -> removeJobEmitter(jobId, emitter));
        emitter.onError(error -> removeJobEmitter(jobId, emitter));
        return emitter;
    }

    public SseEmitter subscribeGlobal() {
        SseEmitter emitter = createEmitter();
        globalEmitters.add(emitter);
        emitter.onCompletion(() -> globalEmitters.remove(emitter));
        emitter.onTimeout(() -> globalEmitters.remove(emitter));
        emitter.onError(error -> globalEmitters.remove(emitter));
        return emitter;
    }

    public void publish(WarningRuleJob job, String eventName) {
        String payload = toJson(job, eventName);
        sendToJob(job.getId(), eventName, payload);
        if ("completed".equals(eventName) || "failed".equals(eventName)) {
            sendToGlobal("job_" + eventName, payload);
        }
    }

    public void sendSnapshot(SseEmitter emitter, WarningRuleJob job, String eventName) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(toJson(job, eventName)));
            if (isTerminal(job)) {
                emitter.complete();
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void sendToJob(String jobId, String eventName, String payload) {
        List<SseEmitter> emitters = jobEmitters.get(jobId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
                if ("completed".equals(eventName) || "failed".equals(eventName)) {
                    emitter.complete();
                }
            } catch (IOException e) {
                log.debug("任务 SSE 发送失败: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        }
    }

    private void sendToGlobal(String eventName, String payload) {
        for (SseEmitter emitter : globalEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                log.debug("全局 SSE 发送失败: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        }
    }

    private SseEmitter createEmitter() {
        return new SseEmitter(SSE_TIMEOUT_MS);
    }

    private void removeJobEmitter(String jobId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = jobEmitters.get(jobId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                jobEmitters.remove(jobId, emitters);
            }
        }
    }

    private String toJson(WarningRuleJob job, String eventName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", eventName);
        payload.put("jobId", job.getId());
        payload.put("ruleId", job.getRuleId());
        payload.put("ruleCode", job.getRuleCode());
        payload.put("ruleName", job.getRuleName());
        payload.put("status", job.getStatus() != null ? job.getStatus().name() : null);
        payload.put("progress", job.getProgress());
        payload.put("total", job.getTotal());
        payload.put("matchedCount", job.getMatchedCount());
        if (job.getParams() != null) {
            payload.put("pageIndex", job.getParams().getPageIndex());
            payload.put("pageSize", job.getParams().getPageSize());
        }
        payload.put("errorMessage", job.getErrorMessage());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("SSE 载荷序列化失败", e);
        }
    }

    private boolean isTerminal(WarningRuleJob job) {
        return job.getStatus() != null
                && ("SUCCESS".equals(job.getStatus().name()) || "FAILED".equals(job.getStatus().name()));
    }
}
