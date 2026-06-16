package com.example.atelier.api.dashboard;

import com.example.atelier.domain.dashboard.DashboardGenerateRequest;
import com.example.atelier.domain.dashboard.DashboardGenerateResponse;
import com.example.atelier.domain.dashboard.DashboardScreen;
import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.warning.evaluator.LlmChatClient;
import com.example.atelier.warning.service.SemanticLlmConfigLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DashboardGenerateService {

    private static final Logger log = LoggerFactory.getLogger(DashboardGenerateService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TOKENS = 4096;

    private static final String SYSTEM_PROMPT =
            "你是 Atelier 可视化大屏设计助手。根据用户描述或参考截图，生成可拖拽大屏的完整 JSON 定义。\n"
                    + "你会收到工作区已有指标、预警规则、数据源、维度值等 JSON，只能引用其中真实存在的 code/id。\n"
                    + "仅返回 JSON，格式严格为：\n"
                    + "{\"reply\":\"给用户的中文说明\",\"dashboard\":{...DashboardScreen...}}\n"
                    + "DashboardScreen 字段：code(英文小写连字符),name,description?,enabled(true),layout,widgets[]\n"
                    + "layout: width(1920),height(1080),gridCols(24),rowHeight(30),theme(tech-blue|aurora|light|emerald)，必须是对象禁止数组\n"
                    + "widgets[] 每项: id,type,title,x,y,w,h,content?,style?,dataSource?；"
                    + "type 只能是 TITLE/METRIC_VALUE/METRIC_CHART/METRIC_TABLE/WARNING_STAT/WARNING_TABLE/SQL_VALUE/SQL_CHART/SQL_TABLE；"
                    + "禁止在 widget 顶层写 metricCode/code/ruleId\n"
                    + "组件类型 type：\n"
                    + "- TITLE: content,style{fontSize,color,textAlign:left|center|right}\n"
                    + "- METRIC_VALUE: dataSource{bindType:METRIC,metricCodes[],valueField,valueFormat?如{value}美元,pageSize:20}\n"
                    + "- METRIC_CHART: +categoryField,chartType(bar|line|pie),valueMappings?{field:{code:name}}\n"
                    + "- METRIC_TABLE: dataSource{bindType:METRIC,metricCodes[],pageSize,valueMappings?,columnLabels?}\n"
                    + "- WARNING_STAT: dataSource{bindType:WARNING,ruleId(用warningRules的id),pageSize:20}\n"
                    + "- WARNING_TABLE: dataSource{bindType:WARNING,ruleId,pageSize:10}\n"
                    + "- SQL_TABLE: dataSource{bindType:SQL,queryMode:TABLE,datasourceId,tableName,pageSize,columnLabels?,valueMappings?}\n"
                    + "布局规则：24 列网格，x+w<=24，组件不重叠，自上而下排列；顶部放 TITLE(w:24,h:2)，"
                    + "KPI 通常 h:4，图表 h:8，表格 h:6~10。\n"
                    + "若用户给截图，尽量复刻布局分区、组件类型与标题；指标/规则绑定到工作区最接近的已有对象。\n"
                    + "dept_code 等编码字段优先从 dimensions.values 生成 valueMappings。\n"
                    + "code 不要与 existingDashboardCodes 重复。";

    private final SemanticLlmConfigLoader llmConfigLoader;
    private final DashboardGenerateContextBuilder contextBuilder;
    private final DashboardScreenNormalizer normalizer;
    private final LlmChatClient chatClient = new LlmChatClient();

    public DashboardGenerateService(SemanticLlmConfigLoader llmConfigLoader,
                                    DashboardGenerateContextBuilder contextBuilder,
                                    DashboardScreenNormalizer normalizer) {
        this.llmConfigLoader = llmConfigLoader;
        this.contextBuilder = contextBuilder;
        this.normalizer = normalizer;
    }

    public DashboardGenerateResponse generate(DashboardGenerateRequest request) {
        if (request == null || request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
            throw new AtelierException("请描述你想生成的大屏");
        }
        SemanticLlmConfig llmConfig = request.getLlmProfileId() != null && !request.getLlmProfileId().trim().isEmpty()
                ? llmConfigLoader.loadProfile(request.getLlmProfileId().trim())
                : llmConfigLoader.load();
        if (!llmConfig.isEnabled() || llmConfig.getApiKey() == null || llmConfig.getApiKey().trim().isEmpty()) {
            throw new AtelierException("请先在「语义检测设置」中启用 LLM 并配置 API Key");
        }

        List<String> images = normalizeImages(request.getImages());
        String workspaceSummary = contextBuilder.buildSummary();
        String userPrompt = buildUserPrompt(request.getPrompt().trim(), workspaceSummary, images);
        log.info("大屏生成请求: promptLen={}, images={}", request.getPrompt().length(), images.size());

        String content = chatClient.chat(llmConfig, SYSTEM_PROMPT, userPrompt, images, MAX_TOKENS);
        ParsedResult parsed = parseResponse(content);

        DashboardScreen screen = normalizer.normalize(parsed.dashboard);
        boolean saved = false;
        if (request.isAutoSave()) {
            screen = normalizer.save(screen);
            saved = true;
        }

        return DashboardGenerateResponse.builder()
                .reply(parsed.reply)
                .dashboard(screen)
                .saved(saved)
                .build();
    }

    public DashboardScreen parseAndNormalize(JsonNode params) {
        DashboardScreen screen = DashboardDefinitionParser.parse(params);
        return normalizer.normalize(screen);
    }

    private String buildUserPrompt(String prompt, String workspaceSummary, List<String> images) {
        StringBuilder builder = new StringBuilder();
        builder.append("【工作区资源】\n").append(workspaceSummary).append('\n');
        builder.append("【用户需求】\n").append(prompt).append('\n');
        if (!images.isEmpty()) {
            builder.append("\n【参考截图】已附带 ").append(images.size())
                    .append(" 张截图，请分析布局与组件后生成 dashboard JSON。\n");
        }
        return builder.toString();
    }

    private List<String> normalizeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalized = new ArrayList<>();
        for (String image : images) {
            if (image != null && image.startsWith("data:image/")) {
                normalized.add(image);
            }
        }
        if (normalized.size() > 4) {
            throw new AtelierException("最多附带 4 张截图");
        }
        return normalized;
    }

    private ParsedResult parseResponse(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new AtelierException("LLM 未返回有效内容");
        }
        String trimmed = unwrapMarkdownCodeFence(content.trim());
        try {
            return buildParsed(MAPPER.readTree(trimmed));
        } catch (Exception ignored) {
            // try extract JSON
        }
        try {
            return buildParsed(MAPPER.readTree(LlmChatClient.extractJsonObject(trimmed)));
        } catch (Exception e) {
            log.warn("大屏生成响应解析失败: {}", e.getMessage());
            throw new AtelierException("无法解析 AI 返回的大屏定义，请重试或简化描述");
        }
    }

    private ParsedResult buildParsed(JsonNode root) throws Exception {
        String reply = root.path("reply").asText("").trim();
        if (reply.isEmpty()) {
            reply = "已为您生成大屏布局。";
        }
        JsonNode dashboardNode = root.path("dashboard");
        if (dashboardNode.isMissingNode() || dashboardNode.isNull()) {
            throw new IllegalArgumentException("缺少 dashboard 节点");
        }
        DashboardScreen screen = DashboardDefinitionParser.parse(root);
        return new ParsedResult(reply, screen);
    }

    private String unwrapMarkdownCodeFence(String content) {
        if (!content.startsWith("```")) {
            return content;
        }
        int firstLineEnd = content.indexOf('\n');
        if (firstLineEnd < 0) {
            return content;
        }
        String body = content.substring(firstLineEnd + 1);
        if (body.endsWith("```")) {
            body = body.substring(0, body.length() - 3);
        }
        return body.trim();
    }

    private static final class ParsedResult {
        private final String reply;
        private final DashboardScreen dashboard;

        private ParsedResult(String reply, DashboardScreen dashboard) {
            this.reply = reply;
            this.dashboard = dashboard;
        }
    }
}
