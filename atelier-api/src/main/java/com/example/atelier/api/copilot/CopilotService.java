package com.example.atelier.api.copilot;

import com.example.atelier.domain.copilot.CopilotActionResult;
import com.example.atelier.domain.copilot.CopilotActivePlan;
import com.example.atelier.domain.copilot.CopilotChatMessage;
import com.example.atelier.domain.copilot.CopilotChatRequest;
import com.example.atelier.domain.copilot.CopilotChatResponse;
import com.example.atelier.domain.copilot.CopilotPlanStep;
import com.example.atelier.domain.copilot.CopilotPlaybook;
import com.example.atelier.domain.copilot.CopilotSqlQueryResult;
import com.example.atelier.domain.query.SqlExecuteResult;
import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.persistence.service.CopilotPlaybookService;
import com.example.atelier.warning.evaluator.LlmChatClient;
import com.example.atelier.warning.service.SemanticLlmConfigLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class CopilotService {

    private static final Logger log = LoggerFactory.getLogger(CopilotService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT =
            "你是 Atelier 数据工场配置助手，帮助用户通过对话创建数据源、元数据表、维度、指标、预警规则，"
                    + "并回答用户关于当前工作区已有配置的问题。\n"
                    + "你会收到当前工作区已有配置的 JSON 摘要。metaTables 中每项含 fields 数组（fieldCode/fieldName/fieldType）。\n"
                    + "当用户询问已有配置（如某表有哪些字段、有哪些数据源/维度/指标）时，直接从工作区 JSON 读取并回答，"
                    + "actions 必须为空数组；不要对已含 fields 的表重复 import_meta_tables。\n"
                    + "当用户要查看/筛选业务数据、执行 SELECT、或选择你给出的 SQL 查询方案（如回复「2」「执行第二条」）时，"
                    + "使用 execute_sql；当用户要 INSERT/UPDATE/DELETE 或执行 DDL（CREATE/ALTER/DROP TABLE）时，"
                    + "使用 execute_write_sql；当用户要在物理库新建表时，使用 create_physical_table（区别于 create_meta_table 元数据登记）。"
                    + "从工作区 datasources/metaTables 推断 datasourceId；H2 演示库 orders 通常在 PUBLIC schema，SQL 可写 PUBLIC.orders。\n"
                    + "仅返回 JSON，格式严格为：\n"
                    + "{\"reply\":\"给用户的中文回复\",\"plan\":[{\"id\":\"s1\",\"title\":\"步骤名\",\"tool\":\"工具名可选\",\"status\":\"pending\"}],\"actions\":[{\"tool\":\"工具名\",\"params\":{...}}]}\n"
                    + "复杂任务（3步以上，如建表+元数据+指标+大屏）必须：\n"
                    + "1. plan 列出全部步骤，每步 title 面向用户；status 初始为 pending\n"
                    + "2. actions 仅包含当前第1步的操作（每轮最多2个工具）；用户说「继续/下一步」时执行 plan 中下一个 pending 步骤\n"
                    + "3. reply 用编号说明整体计划与当前进度，不要输出原始 JSON 结构\n"
                    + "若提供了【已沉淀技能】或【进行中计划】，优先按其中步骤顺序执行。\n"
                    + "若无需要执行的操作，actions 为空数组；纯问答时 plan 可省略。\n"
                    + "可用工具及 params：\n"
                    + "1. create_datasource: id,name,jdbcUrl,username,password,dbType(H2|MYSQL|POSTGRESQL|ORACLE),enabled\n"
                    + "2. create_meta_table: tableCode,tableName,datasourceId,schemaCode?,catalogCode?,comments?,fields?[{fieldCode,fieldName,fieldType?,sort?}] — 登记后会自动从物理库同步字段\n"
                    + "3. import_meta_tables: datasourceId,schemaCode?,catalogCode?,tableNames[](从物理库同步，推荐在 create_physical_table 之后使用)\n"
                    + "4. create_meta_field: tableId,fieldCode,fieldName,fieldType?,sort?\n"
                    + "5. create_dimension: code,name,type(LIST|TREE|TIME_DIM),datasourceId,valueSource(MANUAL|TABLE),fields?[]\n"
                    + "6. create_dimension_value: dimensionId,code,name,parentCode?,sort?\n"
                    + "7. create_metric: code,name,type(TABLE|SQL|COMPOSITE),datasourceId,tableCode,fieldCode,aggregation(SUM|COUNT|AVG|...),dimensions?[{dimensionCode,fieldCode}] — "
                    + "单表聚合用 TABLE（须 tableCode+fieldCode+aggregation）；仅多指标运算用 COMPOSITE（须 formula）；"
                    + "dimensions 的 fieldCode 必须是目标表实际存在的列，禁止绑定表中不存在的维度字段（如无 fiscal_year 列则不要绑 year 维度）\n"
                    + "8. create_warning_rule: code,name,ruleType(METRIC|SEMANTIC|COMPOSITE),metricCodes[],expression,warningLevel?,enabled?\n"
                    + "9. run_warning_rule: ruleId?|ruleCode?|ruleName?,pageIndex?(默认1),pageSize?(默认20),keywordOnly?(默认true) — 异步执行预警预览，立即返回任务\n"
                    + "10. get_warning_job_result: jobId — 获取预警任务当前页的命中行（从 recentWarningJobs 或上轮 run_warning_rule 返回的 jobId 取值）\n"
                    + "11. execute_sql: datasourceId,sql,pageIndex?(默认1),pageSize?(默认20) — 只读 SELECT 查询\n"
                    + "12. execute_write_sql: datasourceId,sql — INSERT/UPDATE/DELETE/CREATE TABLE/ALTER TABLE/DROP TABLE\n"
                    + "13. create_physical_table: datasourceId,tableName,schema?,ifNotExists?,columns[{name,type,nullable?,primaryKey?}]\n"
                    + "14. create_dashboard: params 为 {dashboard:{code,name,layout,widgets[]}}；"
                    + "type 只能是 TITLE/METRIC_VALUE/METRIC_CHART/METRIC_TABLE/WARNING_STAT/WARNING_TABLE/SQL_VALUE/SQL_CHART/SQL_TABLE，"
                    + "widgets 每项仅含 id,type,title,x,y,w,h,content?(TITLE 为字符串),style?,dataSource?；"
                    + "禁止在 widget 上写 metricCode/code/ruleId 等，必须全部放在 dataSource 内；"
                    + "指标绑定用 dataSource.metricCodes 数组与 valueField；预警用 dataSource.ruleId；"
                    + "layout 必须是对象 {width,height,gridCols,rowHeight,theme}，禁止数组；组件放在 widgets[]，不要放进 layout\n"
                    + "create_physical_table 的 params 键名必须完整：datasourceId,tableName,schema?,ifNotExists?,columns[{name,type,...}]，禁止省略 columns\n"
                    + "create_physical_table 完成后登记元数据时，优先 import_meta_tables 同步字段；若用 create_meta_table 须确保 tableCode 与物理表一致且 schema 正确\n"
                    + "当用户要执行/预览/跑一下某条预警规则时，使用 run_warning_rule；params 必须包含 ruleId、ruleCode 或 ruleName 之一，"
                    + "禁止留空。用户通过截图/名称指代规则时，从 warningRules 匹配 name 填入 ruleName（如「学杂费项目备注烟酒」），"
                    + "或填入对应 code（如 tuition_remark_tobacco），不要同步等待结果。\n"
                    + "当用户在可视化大屏页面要求生成/创建大屏、或上传大屏截图要求复刻布局时，使用 create_dashboard。\n"
                    + "当用户要查看命中数据、展示命中的行、上面预警结果的具体数据时，使用 get_warning_job_result（jobId 从 recentWarningJobs 或对话中最近任务获取），不要编造数据。\n"
                    + "规则：引用已有对象时使用工作区中的 id/code；ID 用小写英文与数字；先解释计划再给出 actions；"
                    + "涉及数据写入或建表前提醒用户确认；仅规划模式下仍须写出完整 sql 或 columns；"
                    + "用户只说需求时推断合理默认值；不要编造不存在的 datasourceId。\n"
                    + "用户可能附带界面截图，请结合截图中的页面、表格、按钮、字段和标注理解其意图后再回复。\n"
                    + "从截图识别出预警规则后，reply 中须写出完整规则名称；执行 run_warning_rule 时 params 必须带 ruleName 或 ruleCode。";

    private final SemanticLlmConfigLoader llmConfigLoader;
    private final CopilotWorkspaceContextBuilder contextBuilder;
    private final CopilotActionExecutor actionExecutor;
    private final CopilotWarningRuleResolver warningRuleResolver;
    private final CopilotPlaybookMatcher playbookMatcher;
    private final CopilotPlanOrchestrator planOrchestrator;
    private final CopilotPlaybookService playbookService;
    private final LlmChatClient chatClient = new LlmChatClient();

    public CopilotService(SemanticLlmConfigLoader llmConfigLoader,
                          CopilotWorkspaceContextBuilder contextBuilder,
                          CopilotActionExecutor actionExecutor,
                          CopilotWarningRuleResolver warningRuleResolver,
                          CopilotPlaybookMatcher playbookMatcher,
                          CopilotPlanOrchestrator planOrchestrator,
                          CopilotPlaybookService playbookService) {
        this.llmConfigLoader = llmConfigLoader;
        this.contextBuilder = contextBuilder;
        this.actionExecutor = actionExecutor;
        this.warningRuleResolver = warningRuleResolver;
        this.playbookMatcher = playbookMatcher;
        this.planOrchestrator = planOrchestrator;
        this.playbookService = playbookService;
    }

    public CopilotChatResponse chat(CopilotChatRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new AtelierException("请输入对话内容");
        }
        SemanticLlmConfig llmConfig = request.getLlmProfileId() != null && !request.getLlmProfileId().trim().isEmpty()
                ? llmConfigLoader.loadProfile(request.getLlmProfileId().trim())
                : llmConfigLoader.load();
        if (!llmConfig.isEnabled() || llmConfig.getApiKey() == null || llmConfig.getApiKey().trim().isEmpty()) {
            throw new AtelierException("请先在「语义检测设置」中启用 LLM 并配置 API Key");
        }

        String workspaceSummary = contextBuilder.buildSummary();
        List<String> latestImages = extractLatestUserImages(request.getMessages());
        validateUserImages(request.getMessages(), latestImages);
        List<String> images = extractConversationImages(request.getMessages());

        CopilotActivePlan incomingPlan = resolveIncomingPlan(request);
        String lastUserText = extractLastUserText(request.getMessages());
        List<CopilotPlaybook> matchedPlaybooks = playbookMatcher.match(lastUserText, 3);

        String userPrompt = buildUserPrompt(request, workspaceSummary, images, incomingPlan, matchedPlaybooks);
        log.info("Copilot 请求: page={}, messages={}, images={}, planStep={}",
                request.getCurrentPage(), request.getMessages().size(), images.size(),
                incomingPlan != null ? incomingPlan.getCurrentStepIndex() : null);

        String content = chatClient.chat(llmConfig, SYSTEM_PROMPT, userPrompt, images, LlmChatClient.AGENT_MAX_TOKENS);
        CopilotResponseParser.CopilotParsedResponse parsed = CopilotResponseParser.parse(content);

        List<CopilotActionResult> actionResults = new ArrayList<>();
        if (parsed.getActions() != null) {
            for (JsonNode action : parsed.getActions()) {
                String tool = action.path("tool").asText("");
                JsonNode params = enrichActionParams(tool, action.path("params"), request);
                if (request.isDryRun()) {
                    actionResults.add(buildPlannedAction(tool, params));
                } else {
                    actionResults.add(actionExecutor.execute(tool, params));
                }
            }
        }

        List<CopilotPlanStep> parsedSteps = planOrchestrator.parsePlanSteps(parsed.getPlan());
        CopilotActivePlan activePlan = planOrchestrator.mergePlan(parsedSteps, incomingPlan, actionResults);

        String reply = parsed.getReply();
        if (!request.isDryRun() && actionResults.stream().anyMatch(CopilotActionResult::isSuccess)) {
            workspaceSummary = contextBuilder.buildSummary();
            String supplement = buildPostActionSupplement(actionResults, workspaceSummary);
            if (supplement != null && !supplement.isEmpty()) {
                reply = reply + "\n\n" + supplement;
            }
        }

        boolean planCompleted = activePlan != null && Boolean.TRUE.equals(activePlan.getCompleted());
        boolean suggestSave = planCompleted && activePlan.getPlaybookId() == null;

        return CopilotChatResponse.builder()
                .reply(reply)
                .actions(actionResults)
                .workspaceSummary(workspaceSummary)
                .plan(activePlan)
                .planCompleted(planCompleted)
                .matchedPlaybooks(matchedPlaybooks.isEmpty() ? null : matchedPlaybooks)
                .suggestSavePlaybook(suggestSave)
                .build();
    }

    private CopilotActivePlan resolveIncomingPlan(CopilotChatRequest request) {
        if (request.getActivePlan() != null) {
            return request.getActivePlan();
        }
        if (request.getPlaybookId() != null && !request.getPlaybookId().trim().isEmpty()) {
            return playbookService.getById(request.getPlaybookId().trim())
                    .map(playbook -> {
                        playbookService.incrementUsage(playbook.getId());
                        return playbookMatcher.toActivePlan(playbook);
                    })
                    .orElse(null);
        }
        return null;
    }

    private String extractLastUserText(List<CopilotChatMessage> messages) {
        if (messages == null) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            CopilotChatMessage message = messages.get(i);
            if ("user".equalsIgnoreCase(message.getRole()) && message.getContent() != null) {
                return message.getContent();
            }
        }
        return "";
    }

    private String buildUserPrompt(CopilotChatRequest request,
                                   String workspaceSummary,
                                   List<String> images,
                                   CopilotActivePlan activePlan,
                                   List<CopilotPlaybook> matchedPlaybooks) {
        StringBuilder builder = new StringBuilder();
        builder.append("【当前页面】").append(request.getCurrentPage() != null ? request.getCurrentPage() : "未知")
                .append('\n');
        builder.append("【工作区配置】\n").append(workspaceSummary).append('\n');
        if (matchedPlaybooks != null && !matchedPlaybooks.isEmpty()) {
            builder.append("【已沉淀技能（可参考步骤顺序）】\n");
            for (CopilotPlaybook playbook : matchedPlaybooks) {
                builder.append("- ").append(playbook.getName()).append(" (").append(playbook.getCode()).append("): ");
                if (playbook.getSteps() != null) {
                    for (int i = 0; i < playbook.getSteps().size(); i++) {
                        if (i > 0) {
                            builder.append(" → ");
                        }
                        builder.append(playbook.getSteps().get(i).getTitle());
                    }
                }
                builder.append('\n');
            }
        }
        if (activePlan != null && activePlan.getSteps() != null && !activePlan.getSteps().isEmpty()) {
            builder.append("【进行中计划】\n");
            if (activePlan.getPlaybookName() != null) {
                builder.append("技能: ").append(activePlan.getPlaybookName()).append('\n');
            }
            for (int i = 0; i < activePlan.getSteps().size(); i++) {
                CopilotPlanStep step = activePlan.getSteps().get(i);
                builder.append(i + 1).append(". [").append(step.getStatus()).append("] ")
                        .append(step.getTitle());
                if (step.getTool() != null) {
                    builder.append(" (").append(step.getTool()).append(')');
                }
                builder.append('\n');
            }
            int nextIndex = activePlan.getCurrentStepIndex() != null ? activePlan.getCurrentStepIndex() : 0;
            builder.append("当前应执行第 ").append(nextIndex + 1).append(" 步；actions 仅包含该步操作。\n");
        }
        if (request.isDryRun()) {
            builder.append("【模式】仅规划，不要真正执行（actions 仍须完整列出计划；execute_sql/execute_write_sql 须写出完整 sql；"
                    + "create_physical_table 须写出完整 columns）\n");
        }
        builder.append("【对话历史】\n");
        for (CopilotChatMessage message : request.getMessages()) {
            builder.append(message.getRole()).append(": ").append(message.getContent()).append('\n');
            if (message.getImages() != null && !message.getImages().isEmpty()) {
                builder.append("  [附带 ").append(message.getImages().size()).append(" 张截图]\n");
            }
        }
        if (!images.isEmpty()) {
            builder.append("\n【截图】本条消息已附带 ").append(images.size())
                    .append(" 张界面截图（与文字同条 multimodal 输入），请直接识读截图内容后回复。\n");
        }
        builder.append("\n请根据最后一条 user 消息回复。");
        return builder.toString();
    }

    private JsonNode enrichActionParams(String tool, JsonNode params, CopilotChatRequest request) {
        if (tool == null || !"run_warning_rule".equalsIgnoreCase(tool.trim())) {
            return params;
        }
        if (warningRuleResolver.hasIdentifier(params)) {
            return params;
        }
        ObjectNode enriched = params != null && params.isObject()
                ? ((ObjectNode) params).deepCopy()
                : MAPPER.createObjectNode();
        enriched.put("_conversationHint", warningRuleResolver.buildConversationHint(request.getMessages()));
        return enriched;
    }

    private void validateUserImages(List<CopilotChatMessage> messages, List<String> extracted) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            CopilotChatMessage message = messages.get(i);
            if (!"user".equalsIgnoreCase(message.getRole())) {
                continue;
            }
            if (message.getImages() != null && !message.getImages().isEmpty() && extracted.isEmpty()) {
                throw new AtelierException("截图未能识别，请确认图片为 PNG/JPEG/GIF/WebP 后重试");
            }
            return;
        }
    }

    private List<String> extractLatestUserImages(List<CopilotChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            CopilotChatMessage message = messages.get(i);
            if (!"user".equalsIgnoreCase(message.getRole())) {
                continue;
            }
            if (message.getImages() == null || message.getImages().isEmpty()) {
                return Collections.emptyList();
            }
            List<String> images = new ArrayList<>();
            for (String image : message.getImages()) {
                if (image != null && image.startsWith("data:image/")) {
                    images.add(image);
                }
            }
            if (images.size() > 4) {
                throw new AtelierException("单条消息最多附带 4 张截图");
            }
            return images;
        }
        return Collections.emptyList();
    }

    /** 携带对话中最近的用户截图，便于「执行上述」类追问仍能看到先前界面。 */
    private List<String> extractConversationImages(List<CopilotChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> images = new ArrayList<>();
        for (CopilotChatMessage message : messages) {
            if (!"user".equalsIgnoreCase(message.getRole()) || message.getImages() == null) {
                continue;
            }
            for (String image : message.getImages()) {
                if (image != null && image.startsWith("data:image/")) {
                    images.add(image);
                }
            }
        }
        if (images.size() > 4) {
            return images.subList(images.size() - 4, images.size());
        }
        return images;
    }

    private CopilotActionResult buildPlannedAction(String tool, JsonNode params) {
        String normalized = tool != null ? tool.trim() : "";
        return CopilotActionResult.builder()
                .tool(normalized)
                .success(true)
                .planned(true)
                .message(buildPlannedMessage(normalized, params))
                .result(buildPlannedResult(normalized, params))
                .build();
    }

    private String buildPlannedMessage(String tool, JsonNode params) {
        if (tool == null || tool.isEmpty()) {
            return "计划执行操作（仅规划，未执行）";
        }
        switch (tool.trim().toLowerCase()) {
            case "execute_sql":
                return "计划执行 SQL 查询（仅规划，未执行）";
            case "execute_write_sql":
                return "计划执行写入 SQL（仅规划，未执行）";
            case "create_physical_table":
                return "计划创建物理表 " + textParam(params, "tableName", "") + "（仅规划，未执行）";
            case "import_meta_tables":
                int tableCount = params != null && params.has("tableNames") && params.get("tableNames").isArray()
                        ? params.get("tableNames").size() : 0;
                return "计划同步 " + tableCount + " 张表（仅规划，未执行）";
            case "create_datasource":
                return "计划创建数据源 " + textParam(params, "id", "") + "（仅规划，未执行）";
            case "create_meta_table":
                return "计划创建元数据表 " + textParam(params, "tableCode", "") + "（仅规划，未执行）";
            case "create_metric":
                return "计划创建指标 " + textParam(params, "code", "") + "（仅规划，未执行）";
            case "create_warning_rule":
                return "计划创建预警规则 " + textParam(params, "code", "") + "（仅规划，未执行）";
            case "run_warning_rule":
                String ruleLabel = textParam(params, "ruleName",
                        textParam(params, "ruleCode", textParam(params, "ruleId", "")));
                return "计划异步执行预警规则 " + ruleLabel + "（仅规划，未执行）";
            case "get_warning_job_result":
                return "计划获取预警任务 " + textParam(params, "jobId", "") + " 的命中数据（仅规划，未执行）";
            case "create_dashboard":
                return "计划创建大屏 " + textParam(params, "name",
                        textParam(params.path("dashboard"), "name", textParam(params, "code", "")))
                        + "（仅规划，未执行）";
            default:
                return "计划执行 " + tool + "（仅规划，未执行）";
        }
    }

    private Object buildPlannedResult(String tool, JsonNode params) {
        if (tool == null || params == null || params.isMissingNode()) {
            return params;
        }
        String normalized = tool.trim();
        if ("execute_sql".equalsIgnoreCase(normalized)) {
            int pageIndex = params.path("pageIndex").asInt(1);
            int pageSize = params.path("pageSize").asInt(20);
            return CopilotSqlQueryResult.builder()
                    .datasourceId(textParam(params, "datasourceId", ""))
                    .sql(textParam(params, "sql", ""))
                    .pageIndex(pageIndex <= 0 ? 1 : pageIndex)
                    .pageSize(pageSize <= 0 ? 20 : pageSize)
                    .total(0)
                    .rows(Collections.emptyList())
                    .headers(Collections.emptyMap())
                    .build();
        }
        if ("execute_write_sql".equalsIgnoreCase(normalized)) {
            return SqlExecuteResult.builder()
                    .sql(textParam(params, "sql", ""))
                    .statementType("PLANNED")
                    .affectedRows(0)
                    .message("仅规划，未执行")
                    .build();
        }
        if ("create_physical_table".equalsIgnoreCase(normalized)) {
            String datasourceId = textParam(params, "datasourceId", "");
            String ddl = datasourceId.isEmpty()
                    ? ""
                    : actionExecutor.previewCreateTableDdl(datasourceId, params);
            return SqlExecuteResult.builder()
                    .sql(ddl)
                    .statementType("CREATE TABLE")
                    .affectedRows(0)
                    .message("仅规划，未执行")
                    .build();
        }
        return MAPPER.convertValue(params, Object.class);
    }

    private String textParam(JsonNode node, String field, String defaultValue) {
        if (node == null || node.isMissingNode()) {
            return defaultValue;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        String text = value.asText("").trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private String buildPostActionSupplement(List<CopilotActionResult> actionResults, String workspaceSummary) {
        boolean imported = actionResults.stream()
                .anyMatch(result -> result.isSuccess()
                        && ("import_meta_tables".equals(result.getTool())
                        || "create_meta_table".equals(result.getTool())));
        if (!imported) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(workspaceSummary);
            JsonNode tables = root.path("metaTables");
            if (!tables.isArray() || tables.isEmpty()) {
                return null;
            }
            StringBuilder builder = new StringBuilder();
            boolean wroteAny = false;
            for (JsonNode table : tables) {
                JsonNode fields = table.path("fields");
                if (!fields.isArray() || fields.isEmpty()) {
                    continue;
                }
                if (!wroteAny) {
                    builder.append("**同步后的字段列表：**\n");
                    wroteAny = true;
                }
                String label = table.path("tableName").asText(table.path("tableCode").asText("表"));
                builder.append("\n**").append(label).append("** (`")
                        .append(table.path("tableCode").asText(""))
                        .append("`)\n\n");
                builder.append("| 字段编码 | 字段名称 | 类型 |\n");
                builder.append("| --- | --- | --- |\n");
                for (JsonNode field : fields) {
                    builder.append("| ")
                            .append(field.path("fieldCode").asText(""))
                            .append(" | ")
                            .append(field.path("fieldName").asText(""))
                            .append(" | ")
                            .append(field.path("fieldType").asText(""))
                            .append(" |\n");
                }
            }
            return wroteAny ? builder.toString() : null;
        } catch (Exception e) {
            log.warn("Copilot 同步后补充字段列表失败: {}", e.getMessage());
            return null;
        }
    }
}
