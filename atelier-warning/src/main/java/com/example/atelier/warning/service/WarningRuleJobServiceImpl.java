package com.example.atelier.warning.service;

import com.example.atelier.domain.metric.FilterCondition;
import com.example.atelier.domain.metric.FilterGroup;
import com.example.atelier.domain.copilot.CopilotWarningHitResult;
import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.domain.warning.WarningRuleJob;
import com.example.atelier.domain.warning.WarningRuleJobParams;
import com.example.atelier.domain.warning.WarningRuleJobSource;
import com.example.atelier.domain.warning.WarningRuleJobStatus;
import com.example.atelier.domain.warning.WarningRulePreviewResult;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.persistence.entity.WarningRuleJobEntity;
import com.example.atelier.infra.persistence.jpa.WarningRuleJobJpaRepository;
import com.example.atelier.infra.persistence.mapper.WarningRuleJobMapper;
import com.example.atelier.warning.evaluator.SemanticEvaluationOptions;
import com.example.atelier.warning.event.WarningRuleJobEvent;
import com.example.atelier.warning.spi.WarningRuleJobService;
import com.example.atelier.warning.spi.WarningRuleService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class WarningRuleJobServiceImpl implements WarningRuleJobService {

    private final WarningRuleJobJpaRepository jobRepository;
    private final WarningRuleService warningRuleService;
    private final WarningRulePreviewService previewService;
    private final ApplicationEventPublisher eventPublisher;
    private final Executor warningJobExecutor;

    public WarningRuleJobServiceImpl(WarningRuleJobJpaRepository jobRepository,
                                     WarningRuleService warningRuleService,
                                     WarningRulePreviewService previewService,
                                     ApplicationEventPublisher eventPublisher,
                                     @Qualifier("warningJobExecutor") Executor warningJobExecutor) {
        this.jobRepository = jobRepository;
        this.warningRuleService = warningRuleService;
        this.previewService = previewService;
        this.eventPublisher = eventPublisher;
        this.warningJobExecutor = warningJobExecutor;
    }

    @Override
    @Transactional
    public WarningRuleJob submitPreview(String ruleId, int pageIndex, int pageSize,
                                        List<FilterCondition> filters, List<FilterGroup> filterGroups,
                                        boolean keywordOnly, WarningRuleJobSource source) {
        WarningRule rule = warningRuleService.getRule(ruleId)
                .orElseThrow(() -> new AtelierException("预警规则不存在: " + ruleId));
        WarningRuleJobParams params = WarningRuleJobParams.builder()
                .pageIndex(pageIndex)
                .pageSize(pageSize)
                .keywordOnly(keywordOnly)
                .filters(filters)
                .filterGroups(filterGroups)
                .build();
        WarningRuleJobEntity entity = newJobEntity(rule, source, params);
        WarningRuleJobEntity saved = jobRepository.save(entity);
        WarningRuleJob job = toDomain(saved, false);
        scheduleRunAfterCommit(saved.getPkJob());
        publish(job, "submitted");
        return job;
    }

    @Override
    @Transactional
    public WarningRuleJob submitPreviewByCode(String ruleCode, int pageIndex, int pageSize,
                                              boolean keywordOnly, WarningRuleJobSource source) {
        WarningRule rule = warningRuleService.getByCode(ruleCode)
                .orElseThrow(() -> new AtelierException("预警规则不存在: " + ruleCode));
        return submitPreview(rule.getId(), pageIndex, pageSize, null, null, keywordOnly, source);
    }

    @Override
    public Optional<WarningRuleJob> getJob(String jobId) {
        return jobRepository.findById(jobId).map(entity -> toDomain(entity, true));
    }

    @Override
    public Optional<CopilotWarningHitResult> getJobHits(String jobId) {
        return getJob(jobId).map(this::toHitResult);
    }

    @Override
    public List<WarningRuleJob> listRecent(List<WarningRuleJobStatus> statuses, int limit) {
        List<String> statusNames = statuses == null || statuses.isEmpty()
                ? null
                : statuses.stream().map(Enum::name).collect(Collectors.toList());
        List<WarningRuleJobEntity> entities = statusNames == null
                ? jobRepository.findAll()
                : jobRepository.findByJobStatusInOrderByCreateTimeDesc(statusNames);
        return entities.stream()
                .sorted((a, b) -> {
                    LocalDateTime left = a.getCreateTime() != null ? a.getCreateTime() : LocalDateTime.MIN;
                    LocalDateTime right = b.getCreateTime() != null ? b.getCreateTime() : LocalDateTime.MIN;
                    return right.compareTo(left);
                })
                .limit(limit <= 0 ? 20 : limit)
                .map(entity -> toDomain(entity, true))
                .collect(Collectors.toList());
    }

    @Override
    public void runJob(String jobId) {
        WarningRuleJobEntity entity = jobRepository.findById(jobId)
                .orElseThrow(() -> new AtelierException("任务不存在: " + jobId));
        if (isTerminal(entity.getJobStatus())) {
            return;
        }
        updateStatus(entity, WarningRuleJobStatus.RUNNING, 10, null);
        publish(toDomain(entity, false), "progress");

        try {
            WarningRule rule = warningRuleService.getRule(entity.getPkWarningRule())
                    .orElseThrow(() -> new AtelierException("预警规则不存在: " + entity.getPkWarningRule()));
            WarningRuleJobParams params = WarningRuleJobMapper.paramsFromJson(entity.getParamsJson());
            int pageIndex = params.getPageIndex();
            int pageSize = params.getPageSize();
            boolean keywordOnly = params.isKeywordOnly();
            List<FilterCondition> filters = params.getFilters();
            List<FilterGroup> filterGroups = params.getFilterGroups();

            SemanticEvaluationOptions options = keywordOnly
                    ? SemanticEvaluationOptions.keywordOnly()
                    : SemanticEvaluationOptions.defaults();
            WarningRulePreviewResult result = previewService.preview(
                    rule, pageIndex, pageSize, filters, filterGroups, options);

            entity.setResultJson(WarningRuleJobMapper.resultToJson(result));
            entity.setTotalRows(result.getTotal());
            entity.setMatchedCount(result.getMatchedCount());
            entity.setProgress(100);
            entity.setJobStatus(WarningRuleJobStatus.SUCCESS.name());
            entity.setErrorMsg(null);
            entity.setFinishTime(LocalDateTime.now());
            entity.setModifyTime(LocalDateTime.now());
            jobRepository.save(entity);
            publish(toDomain(entity, true), "completed");
        } catch (Exception e) {
            entity.setJobStatus(WarningRuleJobStatus.FAILED.name());
            entity.setErrorMsg(truncate(e.getMessage(), 500));
            entity.setProgress(100);
            entity.setFinishTime(LocalDateTime.now());
            entity.setModifyTime(LocalDateTime.now());
            jobRepository.save(entity);
            publish(toDomain(entity, true), "failed");
        }
    }

    private void scheduleRunAfterCommit(String jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    warningJobExecutor.execute(() -> runJob(jobId));
                }
            });
        } else {
            warningJobExecutor.execute(() -> runJob(jobId));
        }
    }

    private WarningRuleJobEntity newJobEntity(WarningRule rule, WarningRuleJobSource source,
                                              WarningRuleJobParams params) {
        LocalDateTime now = LocalDateTime.now();
        return WarningRuleJobEntity.builder()
                .pkJob(UUID.randomUUID().toString())
                .pkWarningRule(rule.getId())
                .ruleCode(rule.getCode())
                .ruleName(rule.getName())
                .jobStatus(WarningRuleJobStatus.PENDING.name())
                .jobSource(source != null ? source.name() : WarningRuleJobSource.PAGE.name())
                .progress(0)
                .paramsJson(WarningRuleJobMapper.paramsToJson(params))
                .createTime(now)
                .modifyTime(now)
                .build();
    }

    private void updateStatus(WarningRuleJobEntity entity, WarningRuleJobStatus status,
                              int progress, String errorMessage) {
        entity.setJobStatus(status.name());
        entity.setProgress(progress);
        entity.setErrorMsg(errorMessage);
        entity.setModifyTime(LocalDateTime.now());
        jobRepository.save(entity);
    }

    private CopilotWarningHitResult toHitResult(WarningRuleJob job) {
        if (job.getResult() == null) {
            throw new AtelierException("任务暂无结果数据: " + job.getId());
        }
        WarningRulePreviewResult result = job.getResult();
        WarningRuleJobParams params = job.getParams() != null
                ? job.getParams()
                : WarningRuleJobParams.builder().build();
        List<java.util.Map<String, Object>> matchedRows =
                WarningRuleJobHitSupport.filterMatchedRows(result);
        return CopilotWarningHitResult.builder()
                .jobId(job.getId())
                .ruleId(job.getRuleId())
                .ruleCode(job.getRuleCode())
                .ruleName(job.getRuleName())
                .expression(result.getExpression())
                .total(result.getTotal())
                .pageMatchedCount(result.getMatchedCount())
                .pageIndex(params.getPageIndex())
                .pageSize(params.getPageSize())
                .matchedRows(matchedRows)
                .headers(result.getHeaders())
                .build();
    }

    private WarningRuleJob toDomain(WarningRuleJobEntity entity, boolean includeResult) {
        WarningRuleJob.WarningRuleJobBuilder builder = WarningRuleJob.builder()
                .id(entity.getPkJob())
                .ruleId(entity.getPkWarningRule())
                .ruleCode(entity.getRuleCode())
                .ruleName(entity.getRuleName())
                .status(parseStatus(entity.getJobStatus()))
                .source(parseSource(entity.getJobSource()))
                .progress(entity.getProgress() != null ? entity.getProgress() : 0)
                .errorMessage(entity.getErrorMsg())
                .total(entity.getTotalRows())
                .matchedCount(entity.getMatchedCount())
                .params(WarningRuleJobMapper.paramsFromJson(entity.getParamsJson()))
                .createdAt(entity.getCreateTime())
                .finishedAt(entity.getFinishTime());
        if (includeResult && entity.getResultJson() != null) {
            builder.result(WarningRuleJobMapper.resultFromJson(entity.getResultJson()));
        }
        return builder.build();
    }

    private void publish(WarningRuleJob job, String eventName) {
        eventPublisher.publishEvent(new WarningRuleJobEvent(this, job, eventName));
    }

    private boolean isTerminal(String status) {
        return WarningRuleJobStatus.SUCCESS.name().equals(status)
                || WarningRuleJobStatus.FAILED.name().equals(status);
    }

    private WarningRuleJobStatus parseStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return WarningRuleJobStatus.PENDING;
        }
        return WarningRuleJobStatus.valueOf(status);
    }

    private WarningRuleJobSource parseSource(String source) {
        if (source == null || source.trim().isEmpty()) {
            return WarningRuleJobSource.PAGE;
        }
        return WarningRuleJobSource.valueOf(source);
    }

    private String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}
