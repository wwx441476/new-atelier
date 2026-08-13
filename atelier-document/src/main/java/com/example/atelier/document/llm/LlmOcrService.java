package com.example.atelier.document.llm;

import com.example.atelier.document.model.CompareOptions;
import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.warning.evaluator.LlmChatClient;
import com.example.atelier.warning.service.SemanticLlmConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class LlmOcrService {

    private static final Logger log = LoggerFactory.getLogger(LlmOcrService.class);
    private static final String SYSTEM = "你是 OCR 助手。只输出图片中的可见文字，保持原有段落结构，不要解释。";

    private final SemanticLlmConfigLoader configLoader;
    private final LlmChatClient chatClient = new LlmChatClient();

    public LlmOcrService(SemanticLlmConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    public boolean isAvailable(CompareOptions options) {
        try {
            return LlmConfigSupport.hasApiKey(loadConfig(options));
        } catch (Exception e) {
            return false;
        }
    }

    public String ocrImage(String dataUrl, CompareOptions options) {
        SemanticLlmConfig config = loadConfig(options);
        String reject = LlmConfigSupport.rejectReason(config);
        if (reject != null) {
            throw new IllegalStateException(reject + "，无法 OCR");
        }
        // 文档对比页已显式开启 AI 时，即使档案 enabled=false 也允许调用
        if (!config.isEnabled()) {
            config.setEnabled(true);
        }
        // OCR 比短文本语义判定更慢；Read timed out 多为读超时过短，非必须改流式
        Integer timeout = config.getTimeoutSeconds();
        if (timeout == null || timeout < 180) {
            config.setTimeoutSeconds(180);
        }
        try {
            String text = chatClient.chat(config, SYSTEM, "请提取图片中的全部文字。",
                    Collections.singletonList(dataUrl), 2048);
            return text == null ? "" : text;
        } catch (Exception e) {
            log.warn("OCR failed: {}", e.getMessage());
            // 交由上层按页降级，避免空响应直接打断整次对比
            if (e.getMessage() != null && e.getMessage().contains("响应为空")) {
                return "";
            }
            throw e;
        }
    }

    private SemanticLlmConfig loadConfig(CompareOptions options) {
        String profileId = options == null ? null : options.getLlmProfileId();
        return configLoader.loadProfile(profileId);
    }
}
