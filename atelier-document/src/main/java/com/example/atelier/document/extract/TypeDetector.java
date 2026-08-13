package com.example.atelier.document.extract;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Locale;

@Component
public class TypeDetector {

    private final Tika tika = new Tika();

    public String detect(InputStream input, String fileName, String declaredContentType) {
        String byName = guessByExtension(fileName);
        if (byName != null) {
            return byName;
        }
        if (declaredContentType != null && !declaredContentType.isEmpty()
                && !"application/octet-stream".equalsIgnoreCase(declaredContentType)) {
            return declaredContentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        }
        try {
            InputStream in = input.markSupported() ? input : new BufferedInputStream(input);
            in.mark(128 * 1024);
            String detected = tika.detect(in, fileName);
            in.reset();
            return detected == null ? "application/octet-stream" : detected.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    public String guessByExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".csv")) {
            return "text/plain";
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return "text/markdown";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lower.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".java") || lower.endsWith(".ts") || lower.endsWith(".tsx")
                || lower.endsWith(".js") || lower.endsWith(".py") || lower.endsWith(".xml")
                || lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".sql")) {
            return "text/plain";
        }
        return null;
    }
}
