package com.example.atelier.document.extract;

import com.example.atelier.document.model.BlockType;
import com.example.atelier.document.model.DocumentBlock;
import com.example.atelier.document.model.DocumentModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Order(10)
public class TextDocumentExtractor implements DocumentExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean supports(String mimeType, String fileName) {
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (mime.startsWith("text/") || "application/json".equals(mime)
                || "application/xml".equals(mime) || "application/javascript".equals(mime)) {
            return true;
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".json")
                || name.endsWith(".csv") || name.endsWith(".xml") || name.endsWith(".yml")
                || name.endsWith(".yaml") || name.endsWith(".sql") || name.endsWith(".java")
                || name.endsWith(".ts") || name.endsWith(".tsx") || name.endsWith(".js")
                || name.endsWith(".py");
    }

    @Override
    public DocumentModel extract(InputStream input, ExtractContext context) throws Exception {
        byte[] bytes = readAll(input);
        String text = new String(bytes, StandardCharsets.UTF_8);
        String fileName = context.getFileName();
        boolean json = (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".json"))
                || "application/json".equalsIgnoreCase(context.getDeclaredContentType());
        if (json) {
            try {
                Object parsed = MAPPER.readValue(text, Object.class);
                text = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
            } catch (Exception ignored) {
                // keep raw text
            }
        }

        BlockIds ids = new BlockIds("txt");
        List<DocumentBlock> blocks = new ArrayList<>();
        String[] paragraphs = text.split("\\R{2,}");
        for (String para : paragraphs) {
            String trimmed = para.replace("\r\n", "\n").replace('\r', '\n').trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            BlockType type = BlockType.PARAGRAPH;
            int level = 0;
            if (trimmed.startsWith("#")) {
                type = BlockType.HEADING;
                level = Math.min(6, leadingHashes(trimmed));
            } else if (trimmed.startsWith("```") || looksLikeCode(trimmed)) {
                type = BlockType.CODE;
            } else if (trimmed.matches("(?s)^\\s*([-*+]|\\d+\\.)\\s+.*")) {
                type = BlockType.LIST_ITEM;
            }
            blocks.add(DocumentBlock.builder()
                    .id(ids.next())
                    .type(type)
                    .level(level)
                    .text(trimmed)
                    .build());
        }
        if (blocks.isEmpty() && !text.trim().isEmpty()) {
            blocks.add(DocumentBlock.builder()
                    .id(ids.next())
                    .type(BlockType.PARAGRAPH)
                    .text(text.trim())
                    .build());
        }
        return DocumentModel.builder()
                .fileName(fileName)
                .mimeType(json ? "application/json" : "text/plain")
                .blocks(blocks)
                .build();
    }

    private static int leadingHashes(String s) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == '#') {
            n++;
        }
        return n;
    }

    private static boolean looksLikeCode(String s) {
        return s.contains("{") && s.contains("}") && s.contains(";");
    }

    static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = input.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
