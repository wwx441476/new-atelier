package com.example.atelier.warning.service;

import com.example.atelier.domain.warning.WarningRuleJobStatus;
import com.example.atelier.infra.persistence.entity.WarningRuleJobEntity;
import com.example.atelier.infra.persistence.jpa.WarningRuleJobJpaRepository;
import com.example.atelier.warning.spi.WarningRuleJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 恢复长时间卡在 RUNNING 的任务（如进程重启导致中断）。
 */
@Service
public class WarningRuleJobRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(WarningRuleJobRecoveryService.class);

    private final WarningRuleJobJpaRepository jobRepository;
    private final WarningRuleJobService jobService;
    private final Executor warningJobExecutor;

    public WarningRuleJobRecoveryService(WarningRuleJobJpaRepository jobRepository,
                                         WarningRuleJobService jobService,
                                         @Qualifier("warningJobExecutor") Executor warningJobExecutor) {
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.warningJobExecutor = warningJobExecutor;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void recoverStaleJobs() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        List<WarningRuleJobEntity> stale = jobRepository.findByJobStatusAndModifyTimeBefore(
                WarningRuleJobStatus.RUNNING.name(), threshold);
        for (WarningRuleJobEntity entity : stale) {
            log.info("恢复卡住的任务: {}", entity.getPkJob());
            entity.setJobStatus(WarningRuleJobStatus.PENDING.name());
            entity.setProgress(0);
            entity.setModifyTime(LocalDateTime.now());
            jobRepository.save(entity);
            String jobId = entity.getPkJob();
            warningJobExecutor.execute(() -> jobService.runJob(jobId));
        }
    }
}
