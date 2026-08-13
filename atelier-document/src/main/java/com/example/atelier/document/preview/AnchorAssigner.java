package com.example.atelier.document.preview;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 为预览块分配稳定 id 与锚点，供后续按内容定位 / 批注。
 * id = p{page}-{textHash8}-{occurrence}
 */
public class AnchorAssigner {

    public void assign(PreviewDocument document) {
        if (document == null || document.getBlocks() == null) {
            return;
        }
        Map<Integer, StringBuilder> pageNorm = new LinkedHashMap<>();
        Map<String, Integer> occurrenceCounter = new HashMap<>();

        for (PreviewBlock block : document.getBlocks()) {
            if (block == null || block.getType() == PreviewBlockType.SECTION) {
                continue;
            }
            ensureRuns(block);
            String plain = PreviewTextNormalize.blockPlainText(block);
            block.setText(plain);

            int page = resolvePage(block);
            String norm = PreviewTextNormalize.normalize(plain);
            String hash = shortHash(norm);
            String occKey = page + ":" + hash;
            int occurrence = occurrenceCounter.getOrDefault(occKey, 0) + 1;
            occurrenceCounter.put(occKey, occurrence);

            StringBuilder pageBuf = pageNorm.computeIfAbsent(page, p -> new StringBuilder());
            int start = pageBuf.length();
            pageBuf.append(norm);
            int end = pageBuf.length();

            block.setId("p" + page + "-" + hash + "-" + occurrence);
            block.setAnchor(PreviewBlockAnchor.builder()
                    .page(page)
                    .textHash(hash)
                    .occurrence(occurrence)
                    .sourceStart(start)
                    .sourceEnd(end)
                    .build());
        }
    }

    private static int resolvePage(PreviewBlock block) {
        if (block.getMeta() != null && block.getMeta().getPage() != null) {
            return block.getMeta().getPage();
        }
        if (block.getAnchor() != null && block.getAnchor().getPage() != null) {
            return block.getAnchor().getPage();
        }
        return 1;
    }

    private static void ensureRuns(PreviewBlock block) {
        if (block.getRuns() != null && !block.getRuns().isEmpty()) {
            return;
        }
        String text = block.getText() == null ? "" : block.getText();
        block.setRuns(java.util.Collections.singletonList(
                PreviewRun.builder().text(text).marks(new ArrayList<>()).build()));
    }

    static String shortHash(String normalizedText) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(normalizedText.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(normalizedText.hashCode());
        }
    }
}
