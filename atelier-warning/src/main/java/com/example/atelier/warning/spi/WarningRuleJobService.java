package com.example.atelier.warning.spi;

import com.example.atelier.domain.metric.FilterCondition;
import com.example.atelier.domain.metric.FilterGroup;
import com.example.atelier.domain.copilot.CopilotWarningHitResult;
import com.example.atelier.domain.warning.WarningRuleJob;
import com.example.atelier.domain.warning.WarningRuleJobSource;
import com.example.atelier.domain.warning.WarningRuleJobStatus;

import java.util.List;
import java.util.Optional;

/**
 * 预警规则异步任务 SPI。
 */
public interface WarningRuleJobService {

    WarningRuleJob submitPreview(String ruleId, int pageIndex, int pageSize,
                                 List<FilterCondition> filters, List<FilterGroup> filterGroups,
                                 boolean keywordOnly, WarningRuleJobSource source);

    WarningRuleJob submitPreviewByCode(String ruleCode, int pageIndex, int pageSize,
                                       boolean keywordOnly, WarningRuleJobSource source);

    Optional<WarningRuleJob> getJob(String jobId);

    Optional<CopilotWarningHitResult> getJobHits(String jobId);

    List<WarningRuleJob> listRecent(List<WarningRuleJobStatus> statuses, int limit);

    void runJob(String jobId);
}
