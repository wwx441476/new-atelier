package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.api.dto.MetricQueryApiRequest;
import com.example.atelier.api.dto.WarningRulePreviewRequest;
import com.example.atelier.api.job.JobSseHub;
import com.example.atelier.domain.metric.FilterCondition;
import com.example.atelier.domain.metric.FilterGroup;
import com.example.atelier.domain.metric.FilterOperator;
import com.example.atelier.domain.copilot.CopilotWarningHitResult;
import com.example.atelier.domain.warning.WarningRuleJob;
import com.example.atelier.domain.warning.WarningRuleJobSource;
import com.example.atelier.domain.warning.WarningRuleJobStatus;
import com.example.atelier.warning.spi.WarningRuleJobService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 预警规则异步任务 API。
 */
@RestController
@RequestMapping("/api/v2/warning")
public class WarningRuleJobController {

    private final WarningRuleJobService jobService;
    private final JobSseHub jobSseHub;

    public WarningRuleJobController(WarningRuleJobService jobService, JobSseHub jobSseHub) {
        this.jobService = jobService;
        this.jobSseHub = jobSseHub;
    }

    @PostMapping("/rules/{ruleId}/preview/jobs")
    public ApiResponse<WarningRuleJob> submitPreviewJob(
            @PathVariable String ruleId,
            @RequestBody(required = false) WarningRulePreviewRequest request) {
        WarningRulePreviewRequest body = request != null ? request : new WarningRulePreviewRequest();
        WarningRuleJob job = jobService.submitPreview(
                ruleId,
                body.getPageIndex(),
                body.getPageSize(),
                toFilterConditions(body.getFilters()),
                toFilterGroups(body.getFilterGroups()),
                body.isKeywordOnly(),
                WarningRuleJobSource.PAGE);
        return ApiResponse.ok(job);
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<WarningRuleJob> getJob(@PathVariable String jobId) {
        return jobService.getJob(jobId)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("任务不存在: " + jobId));
    }

    @GetMapping("/jobs/{jobId}/hits")
    public ApiResponse<CopilotWarningHitResult> getJobHits(@PathVariable String jobId) {
        return jobService.getJobHits(jobId)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("任务不存在: " + jobId));
    }

    @GetMapping("/jobs")
    public ApiResponse<List<WarningRuleJob>> listJobs(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        List<WarningRuleJobStatus> statuses = parseStatuses(status);
        return ApiResponse.ok(jobService.listRecent(statuses, limit));
    }

    @GetMapping(value = "/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeJobEvents(@PathVariable String jobId) {
        SseEmitter emitter = jobSseHub.subscribeJob(jobId);
        jobService.getJob(jobId).ifPresent(job -> {
            if (job.getStatus() == WarningRuleJobStatus.SUCCESS) {
                jobSseHub.sendSnapshot(emitter, job, "completed");
            } else if (job.getStatus() == WarningRuleJobStatus.FAILED) {
                jobSseHub.sendSnapshot(emitter, job, "failed");
            }
        });
        return emitter;
    }

    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeNotifications() {
        return jobSseHub.subscribeGlobal();
    }

    private List<WarningRuleJobStatus> parseStatuses(String status) {
        if (status == null || status.trim().isEmpty()) {
            return Arrays.asList(
                    WarningRuleJobStatus.PENDING,
                    WarningRuleJobStatus.RUNNING,
                    WarningRuleJobStatus.SUCCESS);
        }
        return Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(WarningRuleJobStatus::valueOf)
                .collect(Collectors.toList());
    }

    private List<FilterCondition> toFilterConditions(List<MetricQueryApiRequest.FilterDto> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        return filters.stream().map(this::toFilterCondition).collect(Collectors.toList());
    }

    private List<FilterGroup> toFilterGroups(List<MetricQueryApiRequest.FilterGroupDto> filterGroups) {
        if (filterGroups == null || filterGroups.isEmpty()) {
            return null;
        }
        return filterGroups.stream()
                .map(group -> FilterGroup.builder()
                        .conditions(group.getConditions() == null ? null :
                                group.getConditions().stream()
                                        .map(this::toFilterCondition)
                                        .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    private FilterCondition toFilterCondition(MetricQueryApiRequest.FilterDto filter) {
        return FilterCondition.builder()
                .field(filter.getField())
                .operator(parseOperator(filter.getOperator()))
                .values(filter.getValues())
                .build();
    }

    private FilterOperator parseOperator(String operator) {
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("过滤运算符不能为空");
        }
        String normalized = operator.toUpperCase().replace(" ", "_");
        if ("GTE".equals(normalized)) {
            normalized = "GE";
        } else if ("LTE".equals(normalized)) {
            normalized = "LE";
        }
        return FilterOperator.valueOf(normalized);
    }
}
