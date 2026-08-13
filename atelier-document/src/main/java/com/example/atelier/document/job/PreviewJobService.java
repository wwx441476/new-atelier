package com.example.atelier.document.job;

import com.example.atelier.document.extract.DocumentExtractService;
import com.example.atelier.document.llm.LlmPreviewRefineService;
import com.example.atelier.document.llm.LlmStyleEnrichService;
import com.example.atelier.document.model.CompareOptions;
import com.example.atelier.document.model.DocumentModel;
import com.example.atelier.document.preview.AnchorAssigner;
import com.example.atelier.document.preview.PreviewDocument;
import com.example.atelier.document.preview.PreviewJob;
import com.example.atelier.document.preview.PreviewJobStatus;
import com.example.atelier.document.preview.PreviewMapper;
import com.example.atelier.document.preview.PreviewOptions;
import com.example.atelier.document.preview.PreviewStructureNormalizer;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

@Service
public class PreviewJobService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PreviewJobService.class);

    private final Map<String, PreviewJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, Path> filePaths = new ConcurrentHashMap<>();
    private final Map<String, String> contentTypes = new ConcurrentHashMap<>();
    private final Map<String, PreviewOptions> optionsMap = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> futures = new ConcurrentHashMap<>();
    private final Set<String> cancelledIds = ConcurrentHashMap.newKeySet();

    private final TempBlobStore blobStore;
    private final DocumentExtractService extractService;
    private final PreviewMapper previewMapper;
    private final LlmStyleEnrichService styleEnrichService;
    private final LlmPreviewRefineService refineService;
    private final AnchorAssigner anchorAssigner = new AnchorAssigner();
    private final PreviewStructureNormalizer structureNormalizer = new PreviewStructureNormalizer();
    private final DocumentPreviewProperties properties;
    private final ExecutorService executor;
    private final Semaphore semaphore;

    public PreviewJobService(TempBlobStore blobStore,
                             DocumentExtractService extractService,
                             PreviewMapper previewMapper,
                             LlmStyleEnrichService styleEnrichService,
                             LlmPreviewRefineService refineService,
                             DocumentPreviewProperties properties) {
        this.blobStore = blobStore;
        this.extractService = extractService;
        this.previewMapper = previewMapper;
        this.styleEnrichService = styleEnrichService;
        this.refineService = refineService;
        this.properties = properties;
        this.executor = Executors.newFixedThreadPool(Math.max(1, properties.getMaxConcurrentJobs()));
        this.semaphore = new Semaphore(Math.max(1, properties.getMaxConcurrentJobs()));
    }

    public PreviewJob submit(String fileName, String contentType, InputStream input, PreviewOptions options) {
        cleanupExpired();
        String id = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        PreviewOptions opts = options == null ? PreviewOptions.builder().build() : options;
        PreviewJob job = PreviewJob.builder()
                .id(id)
                .status(PreviewJobStatus.PENDING)
                .progress("queued")
                .progressPercent(0)
                .fileName(fileName)
                .createdAt(now)
                .updatedAt(now)
                .build();
        jobs.put(id, job);

        Path path = blobStore.save(id, "preview", fileName, input);
        filePaths.put(id, path);
        contentTypes.put(id, contentType);
        optionsMap.put(id, opts);

        Future<?> future = executor.submit(() -> runJob(id));
        futures.put(id, future);
        return snapshot(job);
    }

    public Optional<PreviewJob> get(String id) {
        cleanupExpired();
        PreviewJob job = jobs.get(id);
        return job == null ? Optional.empty() : Optional.of(snapshot(job));
    }

    /** 停止进行中的预览任务（可中断 LLM 长调用之间的后续步骤）。 */
    public Optional<PreviewJob> cancel(String id) {
        PreviewJob job = jobs.get(id);
        if (job == null) {
            return Optional.empty();
        }
        PreviewJobStatus status = job.getStatus();
        if (status == PreviewJobStatus.SUCCEEDED
                || status == PreviewJobStatus.FAILED
                || status == PreviewJobStatus.CANCELLED) {
            return Optional.of(snapshot(job));
        }
        cancelledIds.add(id);
        Future<?> future = futures.get(id);
        if (future != null) {
            future.cancel(true);
        }
        markCancelled(job);
        log.info("Preview job {} cancelled by user", id);
        return Optional.of(snapshot(job));
    }

    public boolean delete(String id) {
        cancelledIds.add(id);
        Future<?> future = futures.remove(id);
        if (future != null) {
            future.cancel(true);
        }
        PreviewJob removed = jobs.remove(id);
        filePaths.remove(id);
        contentTypes.remove(id);
        optionsMap.remove(id);
        cancelledIds.remove(id);
        blobStore.deleteJob(id);
        return removed != null;
    }

    private void runJob(String id) {
        PreviewJob job = jobs.get(id);
        if (job == null || isCancelled(id)) {
            return;
        }
        boolean acquired = false;
        BooleanSupplier cancelled = () -> isCancelled(id);
        try {
            acquired = semaphore.tryAcquire(properties.getJobTtlMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                if (!isCancelled(id)) {
                    fail(job, "等待执行超时（并发任务过多）");
                }
                return;
            }
            ensureNotCancelled(id);
            PreviewOptions previewOptions = optionsMap.getOrDefault(id, PreviewOptions.builder().build());
            update(job, PreviewJobStatus.RUNNING, "extracting", 15);
            CompareOptions extractOptions = CompareOptions.builder()
                    .enableLlm(true)
                    .llmProfileId(previewOptions.getLlmProfileId())
                    .build();
            DocumentModel model = extractService.extract(
                    filePaths.get(id), job.getFileName(), contentTypes.get(id), extractOptions);

            ensureNotCancelled(id);
            update(job, PreviewJobStatus.RUNNING, "mapping", 45);
            PreviewDocument preview = previewMapper.toPreview(model);
            preview = structureNormalizer.normalize(preview);

            ensureNotCancelled(id);
            update(job, PreviewJobStatus.RUNNING, "styling", 65);
            preview = styleEnrichService.enrich(preview, previewOptions, cancelled);
            preview = structureNormalizer.normalize(preview);

            ensureNotCancelled(id);
            update(job, PreviewJobStatus.RUNNING, "refining", 82);
            preview = refineService.refine(preview, model, filePaths.get(id), previewOptions, cancelled);

            ensureNotCancelled(id);
            anchorAssigner.assign(preview);
            job.setResult(preview);
            update(job, PreviewJobStatus.SUCCEEDED, "done", 100);
        } catch (CancellationException e) {
            markCancelled(job);
            log.info("Preview job {} stopped", id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markCancelled(job);
            log.info("Preview job {} interrupted", id);
        } catch (Exception e) {
            if (isCancelled(id) || job.getStatus() == PreviewJobStatus.CANCELLED) {
                markCancelled(job);
                return;
            }
            log.warn("Preview job {} failed: {}", id, e.getMessage());
            fail(job, e instanceof AtelierException ? e.getMessage() : "预览失败: " + e.getMessage());
        } finally {
            futures.remove(id);
            if (acquired) {
                semaphore.release();
            }
            // 取消后仍保留 job 记录供前端展示；仅清理临时文件与路径
            blobStore.deleteJob(id);
            filePaths.remove(id);
            contentTypes.remove(id);
            optionsMap.remove(id);
        }
    }

    private boolean isCancelled(String id) {
        return cancelledIds.contains(id) || Thread.currentThread().isInterrupted();
    }

    private void ensureNotCancelled(String id) {
        if (isCancelled(id)) {
            throw new CancellationException("用户已停止预览");
        }
    }

    private void markCancelled(PreviewJob job) {
        if (job == null) {
            return;
        }
        if (job.getStatus() == PreviewJobStatus.SUCCEEDED) {
            return;
        }
        job.setStatus(PreviewJobStatus.CANCELLED);
        job.setProgress("cancelled");
        job.setError("用户已停止预览");
        job.setUpdatedAt(System.currentTimeMillis());
    }

    private void fail(PreviewJob job, String message) {
        if (job.getStatus() == PreviewJobStatus.CANCELLED) {
            return;
        }
        job.setStatus(PreviewJobStatus.FAILED);
        job.setError(message);
        job.setProgress("failed");
        job.setProgressPercent(100);
        job.setUpdatedAt(System.currentTimeMillis());
    }

    private void update(PreviewJob job, PreviewJobStatus status, String progress, int percent) {
        if (job.getStatus() == PreviewJobStatus.CANCELLED) {
            throw new CancellationException("用户已停止预览");
        }
        job.setStatus(status);
        job.setProgress(progress);
        job.setProgressPercent(percent);
        job.setUpdatedAt(System.currentTimeMillis());
    }

    private void cleanupExpired() {
        long ttl = properties.getJobTtlMillis();
        long cutoff = System.currentTimeMillis() - ttl;
        Iterator<Map.Entry<String, PreviewJob>> it = jobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PreviewJob> e = it.next();
            if (e.getValue().getCreatedAt() < cutoff) {
                String id = e.getKey();
                it.remove();
                cancelledIds.add(id);
                Future<?> f = futures.remove(id);
                if (f != null) {
                    f.cancel(true);
                }
                blobStore.deleteJob(id);
                filePaths.remove(id);
                contentTypes.remove(id);
                optionsMap.remove(id);
                cancelledIds.remove(id);
            }
        }
        blobStore.cleanupExpired(ttl);
    }

    private PreviewJob snapshot(PreviewJob job) {
        return PreviewJob.builder()
                .id(job.getId())
                .status(job.getStatus())
                .progress(job.getProgress())
                .progressPercent(job.getProgressPercent())
                .fileName(job.getFileName())
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
        futures.clear();
        cancelledIds.clear();
    }
}
