package com.example.atelier.api.copilot;

import com.example.atelier.domain.copilot.CopilotActionResult;
import com.example.atelier.domain.copilot.CopilotChatMessage;
import com.example.atelier.domain.copilot.CopilotChatRequest;
import com.example.atelier.domain.copilot.CopilotChatResponse;
import com.example.atelier.domain.copilot.CopilotSqlQueryResult;
import com.example.atelier.domain.query.SqlExecuteResult;
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
                    + "{\"reply\":\"给用户的中文回复\",\"actions\":[{\"tool\":\"工具名\",\"params\":{...}}]}\n"
                    + "若无需要执行的操作，actions 为空数组。\n"
                    + "可用工具及 params：\n"
                    + "1. create_datasource: id,name,jdbcUrl,username,password,dbType(H2|MYSQL|POSTGRESQL|ORACLE),enabled\n"
                    + "2. create_meta_table: tableCode,tableName,datasourceId,schemaCode?,catalogCode?,comments?\n"
                    + "3. import_meta_tables: datasourceId,schemaCode?,catalogCode?,tableNames[](从物理库同步)\n"
                    + "4. create_meta_field: tableId,fieldCode,fieldName,fieldType?,sort?\n"
                    + "5. create_dimension: code,name,type(LIST|TREE|TIME_DIM),datasourceId,valueSource(MANUAL|TABLE),fields?[]\n"
                    + "6. create_dimension_value: dimensionId,code,name,parentCode?,sort?\n"
                    + "7. create_metric: code,name,type(TABLE|SQL|COMPOSITE),datasourceId,tableCode,fieldCode,aggregation(SUM|COUNT|AVG|...),dimensions?[{dimensionCode,fieldCode}]\n"
                    + "8. create_warning_rule: code,name,ruleType(METRIC|SEMANTIC|COMPOSITE),metricCodes[],expression,warningLevel?,enabled?\n"
                    + "9. run_warning_rule: ruleId?|ruleCode?,pageIndex?(默认1),pageSize?(默认20),keywordOnly?(默认true) — 异步执行预警预览，立即返回任务\n"
                    + "10. get_warning_job_result: jobId — 获取预警任务当前页的命中行（从 recentWarningJobs 或上轮 run_warning_rule 返回的 jobId 取值）\n"
                    + "11. execute_sql: datasourceId,sql,pageIndex?(默认1),pageSize?(默认20) — 只读 SELECT 查询\n"
                    + "12. execute_write_sql: datasourceId,sql — INSERT/UPDATE/DELETE/CREATE TABLE/ALTER TABLE/DROP TABLE\n"
                    + "13. create_physical_table: datasourceId,tableName,schema?,ifNotExists?,columns[{name,type,nullable?,primaryKey?}]\n"
                    + "当用户要执行/预览/跑一下某条预警规则时，使用 run_warning_rule（从 warningRules 取 id 或 code），不要同步等待结果。\n"
                    + "当用户要查看命中数据、展示命中的行、上面预警结果的具体数据时，使用 get_warning_job_result（jobId 从 recentWarningJobs 或对话中最近任务获取），不要编造数据。\n"
                    + "规则：引用已有对象时使用工作区中的 id/code；ID 用小写英文与数字；先解释计划再给出 actions；"
                    + "涉及数据写入或建表前提醒用户确认；仅规划模式下仍须写出完整 sql 或 columns；"
                    + "用户只说需求时推断合理默认值；不要编造不存在的 datasourceId。";

    private final SemanticLlmConfigLoader llmConfigLoader;
    private final CopilotWorkspaceContextBuilder contextBuilder;
    private final CopilotActionExecutor actionExecutor;
    private final LlmChatClient chatClient = new LlmChatClient();

    public CopilotService(SemanticLlmConfigLoader llmConfigLoader,
                          CopilotWorkspaceContextBuilder contextBuilder,
                          CopilotActionExecutor actionExecutor) {
        this.llmConfigLoader = llmConfigLoader;
        this.contextBuilder = contextBuilder;
        this.actionExecutor = actionExecutor;
    }

    public CopilotChatResponse chat(CopilotChatRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new AtelierException("请输入对话内容");
        }
        SemanticLlmConfig llmConfig = llmConfigLoader.load();
        if (!llmConfig.isEnabled() || llmConfig.getApiKey() == null || llmConfig.getApiKey().trim().isEmpty()) {
            throw new AtelierException("请先在「语义检测设置」中启用 LLM 并配置 API Key");
        }

        String workspaceSummary = contextBuilder.buildSummary();
        String userPrompt = buildUserPrompt(request, workspaceSummary);
        log.info("Copilot 请求: page={}, messages={}", request.getCurrentPage(), request.getMessages().size());

        String content = chatClient.chat(llmConfig, SYSTEM_PROMPT, userPrompt, LlmChatClient.AGENT_MAX_TOKENS);
        ParsedPlan plan = parsePlan(content);

        List<CopilotActionResult> actionResults = new ArrayList<>();
        if (plan.actions != null) {
            for (JsonNode action : plan.actions) {
                String tool = action.path("tool").asText("");
                JsonNode params = action.path("params");
                if (request.isDryRun()) {
                    actionResults.add(buildPlannedAction(tool, params));
                } else {
                    actionResults.add(actionExecutor.execute(tool, params));
                }
            }
        }

        String reply = plan.reply;
        if (!request.isDryRun() && actionResults.stream().anyMatch(CopilotActionResult::isSuccess)) {
            workspaceSummary = contextBuilder.buildSummary();
            String supplement = buildPostActionSupplement(actionResults, workspaceSummary);
            if (supplement != null && !supplement.isEmpty()) {
                reply = reply + "\n\n" + supplement;
            }
        }

        return CopilotChatResponse.builder()
                .reply(reply)
                .actions(actionResults)
                .workspaceSummary(workspaceSummary)
                .build();
    }

    private String buildUserPrompt(CopilotChatRequest request, String workspaceSummary) {
        StringBuilder builder = new StringBuilder();
        builder.append("【当前页面】").append(request.getCurrentPage() != null ? request.getCurrentPage() : "未知")
                .append('\n');
        builder.append("【工作区配置】\n").append(workspaceSummary).append('\n');
        if (request.isDryRun()) {
            builder.append("【模式】仅规划，不要真正执行（actions 仍须完整列出计划；execute_sql/execute_write_sql 须写出完整 sql；"
                    + "create_physical_table 须写出完整 columns）\n");
        }
        builder.append("【对话历史】\n");
        for (CopilotChatMessage message : request.getMessages()) {
            builder.append(message.getRole()).append(": ").append(message.getContent()).append('\n');
        }
        builder.append("\n请根据最后一条 user 消息回复。");
        return builder.toString();
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
                String ruleLabel = textParam(params, "ruleCode", textParam(params, "ruleId", ""));
                return "计划异步执行预警规则 " + ruleLabel + "（仅规划，未执行）";
            case "get_warning_job_result":
                return "计划获取预警任务 " + textParam(params, "jobId", "") + " 的命中数据（仅规划，未执行）";
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
                .anyMatch(result -> result.isSuccess() && "import_meta_tables".equals(result.getTool()));
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

    private ParsedPlan parsePlan(String content) {
        try {
            JsonNode root = MAPPER.readTree(LlmChatClient.extractJsonObject(content));
            String reply = root.path("reply").asText(content);
            JsonNode actionsNode = root.path("actions");
            List<JsonNode> actions = new ArrayList<>();
            if (actionsNode.isArray()) {
                for (JsonNode node : actionsNode) {
                    actions.add(node);
                }
            }
            return new ParsedPlan(reply, actions);
        } catch (Exception e) {
            log.warn("Copilot 响应解析失败，返回原文: {}", e.getMessage());
            return new ParsedPlan(content, new ArrayList<JsonNode>());
        }
    }

    private static final class ParsedPlan {
        private final String reply;
        private final List<JsonNode> actions;

        private ParsedPlan(String reply, List<JsonNode> actions) {
            this.reply = reply;
            this.actions = actions;
        }
    }
}
