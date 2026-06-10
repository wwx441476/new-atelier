package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.warning.SemanticMatchResult;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * LLM 语义判定结果内存缓存（LRU + TTL）。
 */
public final class SemanticLlmResultCache {

    private static final SemanticLlmResultCache INSTANCE = new SemanticLlmResultCache();
    private static final int MAX_ENTRIES = 2000;
    private static final long TTL_MS = 60L * 60L * 1000L;

    private final Map<String, CacheEntry> cache = new LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public static SemanticLlmResultCache getInstance() {
        return INSTANCE;
    }

    public Optional<SemanticMatchResult> get(String checkMode, String policy, String text) {
        String key = buildKey(checkMode, policy, text);
        synchronized (cache) {
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                return Optional.empty();
            }
            if (entry.expiresAt < System.currentTimeMillis()) {
                cache.remove(key);
                return Optional.empty();
            }
            return Optional.of(entry.result);
        }
    }

    public void put(String checkMode, String policy, String text, SemanticMatchResult result) {
        if (result == null) {
            return;
        }
        String key = buildKey(checkMode, policy, text);
        synchronized (cache) {
            evictExpiredLocked();
            cache.put(key, new CacheEntry(result, System.currentTimeMillis() + TTL_MS));
        }
    }

    void clear() {
        synchronized (cache) {
            cache.clear();
        }
    }

    private void evictExpiredLocked() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, CacheEntry>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt < now) {
                iterator.remove();
            }
        }
    }

    private static String buildKey(String checkMode, String policy, String text) {
        return (checkMode != null ? checkMode : "") + "\u0001"
                + (policy != null ? policy : "") + "\u0001"
                + (text != null ? text : "");
    }

    private static final class CacheEntry {
        private final SemanticMatchResult result;
        private final long expiresAt;

        private CacheEntry(SemanticMatchResult result, long expiresAt) {
            this.result = result;
            this.expiresAt = expiresAt;
        }
    }
}
