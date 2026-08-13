package com.example.atelier.document.llm;

import com.example.atelier.document.model.CompareOptions;
import com.example.atelier.document.model.CompareResult;
import com.example.atelier.document.model.LlmInterpretation;
import com.example.atelier.document.model.ParagraphOp;
import com.example.atelier.document.model.StructureOp;
import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.warning.evaluator.LlmChatClient;
import com.example.atelier.warning.service.SemanticLlmConfigLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class LlmInterpretService {

    private static final Logger log = LoggerFactory.getLogger(LlmInterpretService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SYSTEM = "你是文档变更审阅助手。根据给定的结构化差异，输出 JSON："
            + "{\"summary\":\"...\",\"impactPoints\":[\"...\"],\"reviewChecklist\":[\"...\"]}。"
            + "不要编造未出现在差异中的内容；说明这是 AI 解读而非逐字校对。";

    private final SemanticLlmConfigLoader configLoader;
    private final LlmChatClient chatClient = new LlmChatClient();

    public LlmInterpretService(SemanticLlmConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    public LlmInterpretation interpret(CompareResult result, CompareOptions options) {
        if (options != null && !options.isEnableLlm()) {
            return LlmInterpretation.builder().available(false).error("已关闭 LLM 解读").build();
        }
        SemanticLlmConfig config;
        try {
            config = configLoader.loadProfile(options == null ? null : options.getLlmProfileId());
        } catch (Exception e) {
            return LlmInterpretation.builder().available(false).error("读取 LLM 配置失败").build();
        }
        String reject = LlmConfigSupport.rejectReason(config);
        if (reject != null) {
            return LlmInterpretation.builder().available(false).error(reject + "，已跳过解读").build();
        }
        // 文档对比页已显式开启 AI 时，即使档案 enabled=false 也允许调用
        if (!config.isEnabled()) {
            config.setEnabled(true);
        }
        try {
            String userPrompt = buildPrompt(result);
            String content = chatClient.chat(config, SYSTEM, userPrompt, 1024);
            JsonNode root = MAPPER.readTree(LlmChatClient.extractJsonObject(content));
            List<String> impacts = readStringArray(root.get("impactPoints"));
            List<String> checklist = readStringArray(root.get("reviewChecklist"));
            String summary = root.hasNonNull("summary") ? root.get("summary").asText() : content;
            return LlmInterpretation.builder()
                    .available(true)
                    .summary(summary)
                    .impactPoints(impacts)
                    .reviewChecklist(checklist)
                    .build();
        } catch (Exception e) {
            log.warn("LLM interpret failed: {}", e.getMessage());
            return LlmInterpretation.builder()
                    .available(false)
                    .error("LLM 解读失败: " + e.getMessage())
                    .build();
        }
    }

    private String buildPrompt(CompareResult result) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("fileA", result.getFileNameA());
        root.put("fileB", result.getFileNameB());
        if (result.getStats() != null) {
            root.putPOJO("stats", result.getStats());
        }
        ArrayNode paras = root.putArray("paragraphOps");
        int p = 0;
        if (result.getParagraphOps() != null) {
            for (ParagraphOp op : result.getParagraphOps()) {
                if (p++ >= 40) {
                    break;
                }
                ObjectNode n = paras.addObject();
                n.put("type", op.getType() == null ? "" : op.getType().name());
                n.put("oldText", truncate(op.getOldText(), 240));
                n.put("newText", truncate(op.getNewText(), 240));
            }
        }
        ArrayNode structs = root.putArray("structureOps");
        int s = 0;
        if (result.getStructureOps() != null) {
            for (StructureOp op : result.getStructureOps()) {
                if (s++ >= 40) {
                    break;
                }
                ObjectNode n = structs.addObject();
                n.put("type", op.getType() == null ? "" : op.getType().name());
                n.put("path", op.getPath());
                n.put("oldText", truncate(op.getOldText(), 160));
                n.put("newText", truncate(op.getNewText(), 160));
            }
        }
        if (result.getQuality() != null && result.getQuality().getWarnings() != null) {
            root.putPOJO("warnings", result.getQuality().getWarnings());
        }
        return "请解读以下文档差异：\n" + MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private static List<String> readStringArray(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return list;
        }
        Iterator<JsonNode> it = node.elements();
        while (it.hasNext()) {
            JsonNode n = it.next();
            if (n != null && n.isTextual()) {
                list.add(n.asText());
            }
        }
        return list;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
