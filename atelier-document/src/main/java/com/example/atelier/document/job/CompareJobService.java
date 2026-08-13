package com.example.atelier.document.job;

import com.example.atelier.document.diff.DiffEngine;
import com.example.atelier.document.extract.DocumentExtractService;
import com.example.atelier.document.llm.LlmInterpretService;
import com.example.atelier.document.model.CompareJob;
import com.example.atelier.document.model.CompareJobStatus;
import com.example.atelier.document.model.CompareOptions;
import com.example.atelier.document.model.CompareResult;
import com.example.atelier.document.model.DocumentModel;
import com.example.atelier.document.model.LlmInterpretation;
import com.example.atelier.document.store.TempBlobStore;
import com.example.atelier.infra.exception.AtelierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class CompareJobService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(CompareJobService.class);

    private final Map<String, CompareJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, Path> fileAPaths = new ConcurrentHashMap<>();
    private final Map<String, Path> fileBPaths = new ConcurrentHashMap<>();
    private final Map<String, String> contentTypeA = new ConcurrentHashMap<>();
    private final Map<String, String> contentTypeB = new ConcurrentHashMap<>();

    private final TempBlobStore blobStore;
    private final DocumentExtractService extractService;
    private final DiffEngine diffEngine;
    private final LlmInterpretService interpretService;
    private final DocumentCompareProperties properties;
    private final ExecutorService executor;
    private final Semaphore semaphore;

    public CompareJobService(TempBlobStore blobStore,
                             DocumentExtractService extractService,
                             DiffEngine diffEngine,
                             LlmInterpretService interpretService,
                             DocumentCompareProperties properties) {
        this.blobStore = blobStore;
        this.extractService = extractService;
        this.diffEngine = diffEngine;
        this.interpretService = interpretService;
        this.properties = properties;
        this.executor = Executors.newFixedThreadPool(Math.max(1, properties.getMaxConcurrentJobs()));
        this.semaphore = new Semaphore(Math.max(1, properties.getMaxConcurrentJobs()));
    }

    public CompareJob submit(String fileNameA, String contentTypeAValue, InputStream inA,
                             String fileNameB, String contentTypeBValue, InputStream inB,
                             CompareOptions options) {
        cleanupExpired();
        String id = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        CompareOptions opts = options == null ? CompareOptions.builder().build() : options;
        CompareJob job = CompareJob.builder()
                .id(id)
                .status(CompareJobStatus.PENDING)
                .progress("queued")
                .progressPercent(0)
                .fileNameA(fileNameA)
                .fileNameB(fileNameB)
                .options(opts)
                .createdAt(now)
                .updatedAt(now)
                .build();
        jobs.put(id, job);

        Path pathA = blobStore.save(id, "a", fileNameA, inA);
        Path pathB = blobStore.save(id, "b", fileNameB, inB);
        fileAPaths.put(id, pathA);
        fileBPaths.put(id, pathB);
        contentTypeA.put(id, contentTypeAValue);
        contentTypeB.put(id, contentTypeBValue);

        executor.submit(() -> runJob(id));
        return snapshot(job);
    }

    public Optional<CompareJob> get(String id) {
        cleanupExpired();
        CompareJob job = jobs.get(id);
        return job == null ? Optional.empty() : Optional.of(snapshot(job));
    }

    public boolean delete(String id) {
        CompareJob removed = jobs.remove(id);
        fileAPaths.remove(id);
        fileBPaths.remove(id);
        contentTypeA.remove(id);
        contentTypeB.remove(id);
        blobStore.deleteJob(id);
        return removed != null;
    }

    private void runJob(String id) {
        CompareJob job = jobs.get(id);
        if (job == null) {
            return;
        }
        boolean acquired = false;
        try {
            acquired = semaphore.tryAcquire(properties.getJobTtlMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                fail(job, "等待执行超时（并发任务过多）");
                return;
            }
            update(job, CompareJobStatus.RUNNING, "extracting", 10);
            DocumentModel modelA = extractService.extract(
                    fileAPaths.get(id), job.getFileNameA(), contentTypeA.get(id), job.getOptions());
            update(job, CompareJobStatus.RUNNING, "extracting-b", 35);
            DocumentModel modelB = extractService.extract(
                    fileBPaths.get(id), job.getFileNameB(), contentTypeB.get(id), job.getOptions());
            update(job, CompareJobStatus.RUNNING, "diffing", 60);
            CompareResult result = diffEngine.compare(modelA, modelB, job.getOptions());
            update(job, CompareJobStatus.RUNNING, "interpreting", 80);
            LlmInterpretation interpretation = interpretService.interpret(result, job.getOptions());
            result.setInterpretation(interpretation);
            job.setResult(result);
            update(job, CompareJobStatus.SUCCEEDED, "done", 100);
        } catch (Exception e) {
            log.warn("Compare job {} failed: {}", id, e.getMessage());
            fail(job, e instanceof AtelierException ? e.getMessage() : "对比失败: " + e.getMessage());
        } finally {
            if (acquired) {
                semaphore.release();
            }
            // keep result in memory; delete blobs to reduce disk use
            blobStore.deleteJob(id);
            fileAPaths.remove(id);
            fileBPaths.remove(id);
        }
    }

    private void fail(CompareJob job, String message) {
        job.setStatus(CompareJobStatus.FAILED);
        job.setError(message);
        job.setProgress("failed");
        job.setProgressPercent(100);
        job.setUpdatedAt(System.currentTimeMillis());
    }

    private void update(CompareJob job, CompareJobStatus status, String progress, int percent) {
        job.setStatus(status);
        job.setProgress(progress);
        job.setProgressPercent(percent);
        job.setUpdatedAt(System.currentTimeMillis());
    }

    private void cleanupExpired() {
        long ttl = properties.getJobTtlMillis();
        long cutoff = System.currentTimeMillis() - ttl;
        Iterator<Map.Entry<String, CompareJob>> it = jobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CompareJob> e = it.next();
            if (e.getValue().getCreatedAt() < cutoff) {
                it.remove();
                blobStore.deleteJob(e.getKey());
                fileAPaths.remove(e.getKey());
                fileBPaths.remove(e.getKey());
                contentTypeA.remove(e.getKey());
                contentTypeB.remove(e.getKey());
            }
        }
        blobStore.cleanupExpired(ttl);
    }

    private CompareJob snapshot(CompareJob job) {
        return CompareJob.builder()
                .id(job.getId())
                .status(job.getStatus())
                .progress(job.getProgress())
                .progressPercent(job.getProgressPercent())
                .fileNameA(job.getFileNameA())
                .fileNameB(job.getFileNameB())
                .options(job.getOptions())
                .result(job.getResult())
                .error(job.getError())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
        for (String id : new ArrayList<>(jobs.keySet())) {
            blobStore.deleteJob(id);
        }
        jobs.clear();
    }
}
