package com.example.atelier.document.store;

import com.example.atelier.document.job.DocumentCompareProperties;
import com.example.atelier.infra.exception.AtelierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Stream;

@Component
public class TempBlobStore {

    private static final Logger log = LoggerFactory.getLogger(TempBlobStore.class);

    private final DocumentCompareProperties properties;
    private Path root;

    public TempBlobStore(DocumentCompareProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws IOException {
        String configured = properties.getTempDir();
        if (configured == null || configured.trim().isEmpty()) {
            root = Paths.get(System.getProperty("java.io.tmpdir"), "atelier-doc-compare");
        } else {
            root = Paths.get(configured);
        }
        Files.createDirectories(root);
    }

    public Path jobDir(String jobId) {
        return root.resolve(jobId);
    }

    public Path save(String jobId, String side, String originalName, InputStream input) {
        try {
            Path dir = jobDir(jobId);
            Files.createDirectories(dir);
            String safe = sanitize(originalName);
            Path target = dir.resolve(side + "-" + safe);
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            long size = Files.size(target);
            if (size > properties.getMaxFileBytes()) {
                Files.deleteIfExists(target);
                throw new AtelierException("文件超过大小限制 "
                        + DocumentCompareProperties.formatBytes(size)
                        + "（上限 " + properties.formatMaxFileSize() + "）");
            }
            return target;
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("保存临时文件失败: " + e.getMessage(), e);
        }
    }

    public void deleteJob(String jobId) {
        Path dir = jobDir(jobId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Failed to delete {}", p);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to clean job dir {}: {}", jobId, e.getMessage());
        }
    }

    public void cleanupExpired(long ttlMillis) {
        long cutoff = System.currentTimeMillis() - ttlMillis;
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(dir, BasicFileAttributes.class);
                    if (attrs.creationTime().toMillis() < cutoff) {
                        deleteRecursive(dir);
                    }
                } catch (IOException e) {
                    log.warn("cleanup skip {}: {}", dir, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("cleanup failed: {}", e.getMessage());
        }
    }

    private void deleteRecursive(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.deleteIfExists(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String sanitize(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "file.bin";
        }
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fff]", "_");
        if (base.length() > 120) {
            base = base.substring(base.length() - 120);
        }
        return base.isEmpty() ? "file.bin" : base;
    }
}
