package com.example.atelier.api.dashboard;

import com.example.atelier.domain.dashboard.DashboardScreen;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将 LLM 产出的大屏 JSON 清洗为领域模型可识别的结构。
 */
public final class DashboardDefinitionParser {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Set<String> WIDGET_FIELDS = immutableSet(
            "id", "type", "title", "x", "y", "w", "h", "content", "style", "dataSource");

    private static final Set<String> DATASOURCE_FIELDS = immutableSet(
            "bindType", "metricCodes", "metricCode", "valueField", "categoryField", "chartType",
            "ruleId", "ruleCode", "ruleName", "datasourceId", "queryMode", "sql", "schema", "tableName",
            "pageSize", "columnLabels", "valueMappings", "valueFormat", "valuePrefix", "valueSuffix",
            "decimalPlaces", "useGrouping", "filterGroups");

    private static final Set<String> VALID_WIDGET_TYPES = immutableSet(
            "TITLE", "METRIC_VALUE", "METRIC_CHART", "METRIC_TABLE",
            "WARNING_STAT", "WARNING_TABLE", "SQL_VALUE", "SQL_CHART", "SQL_TABLE");

    private static final Map<String, String> TYPE_ALIASES = buildTypeAliases();

    private static Map<String, String> buildTypeAliases() {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("DATA_CARD", "METRIC_VALUE");
        aliases.put("KPI", "METRIC_VALUE");
        aliases.put("KPI_CARD", "METRIC_VALUE");
        aliases.put("METRIC_CARD", "METRIC_VALUE");
        aliases.put("STAT_CARD", "METRIC_VALUE");
        aliases.put("NUMBER_CARD", "METRIC_VALUE");
        aliases.put("VALUE_CARD", "METRIC_VALUE");
        aliases.put("CARD", "METRIC_VALUE");
        aliases.put("CHART", "METRIC_CHART");
        aliases.put("BAR_CHART", "METRIC_CHART");
        aliases.put("LINE_CHART", "METRIC_CHART");
        aliases.put("PIE_CHART", "METRIC_CHART");
        aliases.put("MAP", "METRIC_CHART");
        aliases.put("TABLE", "METRIC_TABLE");
        aliases.put("DATA_TABLE", "METRIC_TABLE");
        aliases.put("GRID", "METRIC_TABLE");
        aliases.put("HEADER", "TITLE");
        aliases.put("HEADING", "TITLE");
        aliases.put("TEXT", "TITLE");
        aliases.put("WARNING", "WARNING_STAT");
        aliases.put("ALERT", "WARNING_STAT");
        aliases.put("ALERT_STAT", "WARNING_STAT");
        aliases.put("WARNING_CARD", "WARNING_STAT");
        aliases.put("ALERT_TABLE", "WARNING_TABLE");
        aliases.put("WARNING_LIST", "WARNING_TABLE");
        return Collections.unmodifiableMap(aliases);
    }

    private static Set<String> immutableSet(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    private DashboardDefinitionParser() {
    }

    public static DashboardScreen parse(JsonNode root) {
        JsonNode dashboardNode = resolveDashboardNode(root);
        ObjectNode sanitized = sanitizeDashboard(dashboardNode.deepCopy());
        return MAPPER.convertValue(sanitized, DashboardScreen.class);
    }

    private static JsonNode resolveDashboardNode(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            throw new IllegalArgumentException("缺少大屏定义");
        }
        if (root.has("dashboard") && root.get("dashboard").isObject()) {
            return root.get("dashboard");
        }
        return root;
    }

    private static ObjectNode sanitizeDashboard(JsonNode dashboardNode) {
        ObjectNode dashboard = dashboardNode.isObject()
                ? (ObjectNode) dashboardNode
                : MAPPER.createObjectNode();
        normalizeLayoutAndWidgets(dashboard);
        JsonNode widgetsNode = dashboard.get("widgets");
        if (widgetsNode != null && widgetsNode.isArray()) {
            ArrayNode widgets = (ArrayNode) widgetsNode;
            for (int i = 0; i < widgets.size(); i++) {
                JsonNode widget = widgets.get(i);
                if (widget != null && widget.isObject()) {
                    widgets.set(i, sanitizeWidget((ObjectNode) widget.deepCopy()));
                }
            }
        }
        return dashboard;
    }

    private static void normalizeLayoutAndWidgets(ObjectNode dashboard) {
        promoteAlternateWidgetKeys(dashboard);
        JsonNode layoutNode = dashboard.get("layout");
        if (layoutNode != null && layoutNode.isArray()) {
            ArrayNode layoutArray = (ArrayNode) layoutNode;
            dashboard.set("layout", extractLayoutConfig(layoutArray));
            mergeWidgets(dashboard, extractWidgetsFromLayoutArray(layoutArray));
            return;
        }
        if (layoutNode != null && layoutNode.isObject()) {
            dashboard.set("layout", normalizeLayoutObject((ObjectNode) layoutNode.deepCopy()));
        } else if (dashboard.has("layouts") && dashboard.get("layouts").isArray()) {
            ArrayNode layoutsArray = (ArrayNode) dashboard.get("layouts");
            dashboard.set("layout", extractLayoutConfig(layoutsArray));
            mergeWidgets(dashboard, extractWidgetsFromLayoutArray(layoutsArray));
            dashboard.remove("layouts");
        } else if (layoutNode == null || layoutNode.isNull()) {
            dashboard.set("layout", defaultLayout());
        }
    }

    private static void promoteAlternateWidgetKeys(ObjectNode dashboard) {
        if (dashboard.has("widgets") && dashboard.get("widgets").isArray()) {
            return;
        }
        String[] alternateKeys = {"components", "items", "panels", "elements", "cards"};
        for (String key : alternateKeys) {
            JsonNode node = dashboard.get(key);
            if (node != null && node.isArray()) {
                dashboard.set("widgets", node);
                dashboard.remove(key);
                return;
            }
        }
    }

    private static ObjectNode extractLayoutConfig(ArrayNode layoutArray) {
        ObjectNode layout = defaultLayout();
        if (layoutArray == null || layoutArray.size() == 0) {
            return layout;
        }
        if (layoutArray.size() >= 2 && layoutArray.get(0).isNumber() && layoutArray.get(1).isNumber()) {
            layout.put("width", layoutArray.get(0).asInt(1920));
            layout.put("height", layoutArray.get(1).asInt(1080));
        }
        for (JsonNode item : layoutArray) {
            if (item != null && item.isObject() && looksLikeLayoutConfig((ObjectNode) item)) {
                mergeLayoutFields(layout, (ObjectNode) item);
            }
        }
        return layout;
    }

    private static ArrayNode extractWidgetsFromLayoutArray(ArrayNode layoutArray) {
        ArrayNode widgets = MAPPER.createArrayNode();
        if (layoutArray == null) {
            return widgets;
        }
        for (JsonNode item : layoutArray) {
            if (item != null && item.isObject() && looksLikeWidget(item)) {
                widgets.add(item.deepCopy());
            }
        }
        return widgets;
    }

    private static void mergeWidgets(ObjectNode dashboard, ArrayNode extractedWidgets) {
        if (extractedWidgets == null || extractedWidgets.size() == 0) {
            return;
        }
        JsonNode existing = dashboard.get("widgets");
        ArrayNode merged = MAPPER.createArrayNode();
        if (existing != null && existing.isArray()) {
            merged.addAll((ArrayNode) existing);
        }
        merged.addAll(extractedWidgets);
        dashboard.set("widgets", merged);
    }

    private static ObjectNode normalizeLayoutObject(ObjectNode layout) {
        ObjectNode normalized = defaultLayout();
        mergeLayoutFields(normalized, layout);
        return normalized;
    }

    private static void mergeLayoutFields(ObjectNode target, ObjectNode source) {
        copyIntField(target, source, "width", 1920);
        copyIntField(target, source, "height", 1080);
        copyIntField(target, source, "gridCols", 24);
        copyIntField(target, source, "rowHeight", 30);
        if (source.has("backgroundColor") && source.get("backgroundColor").isTextual()) {
            target.put("backgroundColor", source.get("backgroundColor").asText());
        }
        if (source.has("backgroundImage") && source.get("backgroundImage").isTextual()) {
            target.put("backgroundImage", source.get("backgroundImage").asText());
        }
        if (source.has("theme") && source.get("theme").isTextual()) {
            target.put("theme", source.get("theme").asText());
        }
    }

    private static void copyIntField(ObjectNode target, ObjectNode source, String field, int defaultValue) {
        if (source.has(field) && source.get(field).canConvertToInt()) {
            target.put(field, source.get(field).asInt(defaultValue));
        }
    }

    private static ObjectNode defaultLayout() {
        ObjectNode layout = MAPPER.createObjectNode();
        layout.put("width", 1920);
        layout.put("height", 1080);
        layout.put("gridCols", 24);
        layout.put("rowHeight", 30);
        layout.put("theme", "tech-blue");
        return layout;
    }

    private static boolean looksLikeLayoutConfig(ObjectNode node) {
        return node.has("width") || node.has("height") || node.has("gridCols")
                || node.has("rowHeight") || node.has("theme");
    }

    private static boolean looksLikeWidget(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        if (looksLikeLayoutConfig((ObjectNode) node) && !node.has("type") && !node.has("title")) {
            return false;
        }
        return node.has("type") || node.has("title")
                || (node.has("x") && node.has("w"))
                || node.has("dataSource");
    }

    private static ObjectNode sanitizeWidget(ObjectNode widget) {
        ObjectNode dataSource = extractDataSource(widget);
        normalizeWidgetType(widget, dataSource);
        normalizeContentAndStyle(widget);
        List<String> foreignKeys = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = widget.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            if (WIDGET_FIELDS.contains(key)) {
                continue;
            }
            if (shouldMoveToDataSource(key, widget)) {
                moveToDataSource(dataSource, key, entry.getValue(), widget);
                foreignKeys.add(key);
            }
        }
        foreignKeys.forEach(widget::remove);
        normalizeDataSource(dataSource, widget);
        if (!dataSource.isEmpty()) {
            widget.set("dataSource", dataSource);
        }
        return widget;
    }

    private static void normalizeContentAndStyle(ObjectNode widget) {
        normalizeStringField(widget, "title");
        JsonNode content = widget.get("content");
        if (content == null || content.isNull()) {
            normalizeStyleField(widget);
            return;
        }
        if (content.isTextual() || content.isNumber() || content.isBoolean()) {
            widget.put("content", content.asText());
            normalizeStyleField(widget);
            return;
        }
        if (content.isArray()) {
            widget.put("content", joinTextArray(content));
            normalizeStyleField(widget);
            return;
        }
        if (content.isObject()) {
            ObjectNode contentObj = (ObjectNode) content;
            mergeStyleFromNode(widget, contentObj);
            String text = extractTextFromObject(contentObj);
            if (text != null) {
                widget.put("content", text);
            } else if (isTitleWidget(widget)) {
                widget.remove("content");
            } else {
                widget.remove("content");
            }
        }
        normalizeStyleField(widget);
    }

    private static void normalizeStringField(ObjectNode widget, String field) {
        JsonNode node = widget.get(field);
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            widget.put(field, node.asText());
            return;
        }
        if (node.isObject()) {
            String text = extractTextFromObject((ObjectNode) node);
            if (text != null) {
                widget.put(field, text);
            } else {
                widget.remove(field);
            }
            return;
        }
        if (node.isArray()) {
            String joined = joinTextArray(node);
            if (joined.isEmpty()) {
                widget.remove(field);
            } else {
                widget.put(field, joined);
            }
        }
    }

    private static String joinTextArray(JsonNode array) {
        StringBuilder builder = new StringBuilder();
        if (array == null || !array.isArray()) {
            return "";
        }
        for (JsonNode item : array) {
            if (item.isTextual() || item.isNumber()) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(item.asText());
            }
        }
        return builder.toString();
    }

    private static String extractTextFromObject(ObjectNode obj) {
        String[] keys = {"text", "value", "label", "title", "content", "name", "html", "caption"};
        for (String key : keys) {
            JsonNode value = obj.get(key);
            if (value != null && (value.isTextual() || value.isNumber())) {
                return value.asText();
            }
        }
        return null;
    }

    private static void mergeStyleFromNode(ObjectNode widget, ObjectNode source) {
        ObjectNode patch = MAPPER.createObjectNode();
        if (source.has("fontSize") && source.get("fontSize").canConvertToInt()) {
            patch.put("fontSize", source.get("fontSize").asInt());
        }
        if (source.has("color") && source.get("color").isTextual()) {
            patch.put("color", source.get("color").asText());
        }
        if (source.has("textAlign") && source.get("textAlign").isTextual()) {
            patch.put("textAlign", source.get("textAlign").asText());
        }
        if (source.has("style") && source.get("style").isObject()) {
            mergeStyleFromNode(widget, (ObjectNode) source.get("style"));
        }
        if (!patch.isEmpty()) {
            mergeStyle(widget, patch);
        }
    }

    private static void mergeStyle(ObjectNode widget, ObjectNode patch) {
        ObjectNode style;
        JsonNode existing = widget.get("style");
        if (existing != null && existing.isObject()) {
            style = (ObjectNode) existing.deepCopy();
        } else {
            style = MAPPER.createObjectNode();
        }
        Iterator<Map.Entry<String, JsonNode>> fields = patch.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            style.set(entry.getKey(), entry.getValue());
        }
        widget.set("style", style);
    }

    private static void normalizeStyleField(ObjectNode widget) {
        JsonNode style = widget.get("style");
        if (style == null || style.isNull()) {
            return;
        }
        if (style.isObject()) {
            widget.set("style", normalizeStyleObject((ObjectNode) style));
            return;
        }
        widget.remove("style");
    }

    private static ObjectNode normalizeStyleObject(ObjectNode style) {
        ObjectNode normalized = MAPPER.createObjectNode();
        if (style.has("fontSize") && style.get("fontSize").canConvertToInt()) {
            normalized.put("fontSize", style.get("fontSize").asInt());
        }
        if (style.has("color") && style.get("color").isTextual()) {
            normalized.put("color", style.get("color").asText());
        }
        if (style.has("textAlign") && style.get("textAlign").isTextual()) {
            normalized.put("textAlign", style.get("textAlign").asText());
        }
        return normalized.isEmpty() ? style : normalized;
    }

    private static boolean isTitleWidget(ObjectNode widget) {
        String type = normalizeTypeKey(widget.path("type").asText(""));
        return "TITLE".equals(type)
                || (!widget.has("dataSource") && widget.has("content") && !widget.has("title"));
    }

    private static ObjectNode extractDataSource(ObjectNode widget) {
        JsonNode existing = widget.get("dataSource");
        if (existing != null && existing.isObject()) {
            return (ObjectNode) existing.deepCopy();
        }
        return MAPPER.createObjectNode();
    }

    private static boolean shouldMoveToDataSource(String key, ObjectNode widget) {
        if (DATASOURCE_FIELDS.contains(key)) {
            return true;
        }
        if ("code".equals(key) && isMetricWidgetType(widget.path("type").asText(""))) {
            return true;
        }
        return false;
    }

    private static void moveToDataSource(ObjectNode dataSource, String key, JsonNode value, ObjectNode widget) {
        switch (key) {
            case "metricCode":
            case "code":
                appendMetricCode(dataSource, value);
                break;
            case "ruleCode":
            case "ruleName":
                if (!dataSource.has("ruleId")) {
                    dataSource.set("ruleId", value);
                }
                break;
            default:
                if (!dataSource.has(key)) {
                    dataSource.set(key, value);
                }
                break;
        }
        inferBindType(dataSource, widget.path("type").asText(""));
    }

    private static void normalizeDataSource(ObjectNode dataSource, ObjectNode widget) {
        if (dataSource.has("metricCode")) {
            appendMetricCode(dataSource, dataSource.remove("metricCode"));
        }
        if (dataSource.has("metricCodes") && dataSource.get("metricCodes").isTextual()) {
            appendMetricCode(dataSource, dataSource.get("metricCodes"));
            dataSource.remove("metricCodes");
        }
        if (dataSource.has("ruleCode") || dataSource.has("ruleName")) {
            JsonNode ruleRef = dataSource.has("ruleCode")
                    ? dataSource.remove("ruleCode")
                    : dataSource.remove("ruleName");
            if (!dataSource.has("ruleId")) {
                dataSource.set("ruleId", ruleRef);
            }
        }
        inferBindType(dataSource, widget.path("type").asText(""));
        inferValueField(dataSource, widget.path("type").asText(""));
    }

    private static void appendMetricCode(ObjectNode dataSource, JsonNode codeNode) {
        if (codeNode == null || codeNode.isNull()) {
            return;
        }
        ArrayNode codes;
        if (dataSource.has("metricCodes") && dataSource.get("metricCodes").isArray()) {
            codes = (ArrayNode) dataSource.get("metricCodes");
        } else {
            codes = MAPPER.createArrayNode();
            dataSource.set("metricCodes", codes);
        }
        if (codeNode.isArray()) {
            for (JsonNode item : codeNode) {
                addCodeIfAbsent(codes, item.asText(""));
            }
            return;
        }
        addCodeIfAbsent(codes, codeNode.asText(""));
    }

    private static void addCodeIfAbsent(ArrayNode codes, String code) {
        if (code == null || code.trim().isEmpty()) {
            return;
        }
        for (JsonNode existing : codes) {
            if (code.equals(existing.asText())) {
                return;
            }
        }
        codes.add(code.trim());
    }

    private static void inferBindType(ObjectNode dataSource, String widgetType) {
        if (dataSource.has("bindType") && !dataSource.get("bindType").asText("").isEmpty()) {
            return;
        }
        String type = normalizeTypeKey(widgetType);
        if (type.startsWith("METRIC_")) {
            dataSource.put("bindType", "METRIC");
        } else if (type.startsWith("WARNING_")) {
            dataSource.put("bindType", "WARNING");
        } else if (type.startsWith("SQL_")) {
            dataSource.put("bindType", "SQL");
        }
    }

    private static void inferValueField(ObjectNode dataSource, String widgetType) {
        String type = normalizeTypeKey(widgetType);
        if (!"METRIC_VALUE".equals(type) && !"SQL_VALUE".equals(type)) {
            return;
        }
        if (dataSource.has("valueField") && !dataSource.get("valueField").asText("").isEmpty()) {
            return;
        }
        JsonNode codes = dataSource.get("metricCodes");
        if (codes != null && codes.isArray() && codes.size() > 0) {
            dataSource.put("valueField", codes.get(0).asText());
        }
    }

    private static void normalizeWidgetType(ObjectNode widget, ObjectNode dataSource) {
        String raw = widget.path("type").asText("").trim();
        if (raw.isEmpty()) {
            widget.put("type", inferTypeFromContext(dataSource));
            return;
        }
        String typeKey = normalizeTypeKey(raw);
        inferChartTypeFromAlias(typeKey, dataSource);
        String resolved = resolveWidgetType(typeKey, dataSource);
        widget.put("type", resolved);
    }

    private static String normalizeTypeKey(String raw) {
        return raw.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String resolveWidgetType(String typeKey, ObjectNode dataSource) {
        if (VALID_WIDGET_TYPES.contains(typeKey)) {
            return resolveSqlVariant(typeKey, dataSource);
        }
        if (TYPE_ALIASES.containsKey(typeKey)) {
            return resolveSqlVariant(TYPE_ALIASES.get(typeKey), dataSource);
        }
        if (typeKey.contains("TITLE") || typeKey.contains("HEADER")) {
            return "TITLE";
        }
        if (typeKey.contains("WARNING") && typeKey.contains("TABLE")) {
            return "WARNING_TABLE";
        }
        if (typeKey.contains("WARNING") || typeKey.contains("ALERT")) {
            return "WARNING_STAT";
        }
        if (typeKey.contains("TABLE") || typeKey.contains("GRID") || typeKey.contains("LIST")) {
            return resolveSqlVariant("METRIC_TABLE", dataSource);
        }
        if (typeKey.contains("PIE") || typeKey.contains("BAR") || typeKey.contains("LINE")
                || typeKey.contains("CHART") || typeKey.contains("MAP")) {
            return resolveSqlVariant("METRIC_CHART", dataSource);
        }
        if (typeKey.contains("KPI") || typeKey.contains("CARD") || typeKey.contains("STAT")
                || typeKey.contains("VALUE") || typeKey.contains("NUMBER")) {
            return resolveSqlVariant("METRIC_VALUE", dataSource);
        }
        return resolveSqlVariant("METRIC_VALUE", dataSource);
    }

    private static String inferTypeFromContext(ObjectNode dataSource) {
        if (dataSource == null || dataSource.isEmpty()) {
            return "METRIC_VALUE";
        }
        String bindType = dataSource.path("bindType").asText("").toUpperCase(Locale.ROOT);
        if ("WARNING".equals(bindType)) {
            return dataSource.has("pageSize") && dataSource.get("pageSize").asInt(20) <= 10
                    ? "WARNING_TABLE"
                    : "WARNING_STAT";
        }
        if ("SQL".equals(bindType) || dataSource.has("tableName") || dataSource.has("sql")) {
            if (dataSource.has("chartType")) {
                return "SQL_CHART";
            }
            return "SQL_TABLE";
        }
        if (dataSource.has("chartType") || dataSource.has("categoryField")) {
            return "METRIC_CHART";
        }
        if (dataSource.has("metricCodes")) {
            JsonNode codes = dataSource.get("metricCodes");
            if (codes.isArray() && codes.size() > 1) {
                return "METRIC_TABLE";
            }
            return "METRIC_VALUE";
        }
        return "METRIC_VALUE";
    }

    private static String resolveSqlVariant(String metricType, ObjectNode dataSource) {
        if (dataSource == null || dataSource.isEmpty()) {
            return metricType;
        }
        String bindType = dataSource.path("bindType").asText("").toUpperCase(Locale.ROOT);
        boolean sqlBound = "SQL".equals(bindType)
                || dataSource.has("tableName")
                || dataSource.has("sql")
                || dataSource.has("datasourceId") && !dataSource.has("metricCodes");
        if ("WARNING".equals(bindType) || dataSource.has("ruleId")) {
            if ("METRIC_TABLE".equals(metricType) || "SQL_TABLE".equals(metricType)) {
                return "WARNING_TABLE";
            }
            return "WARNING_STAT";
        }
        if (!sqlBound) {
            return metricType;
        }
        if ("METRIC_VALUE".equals(metricType)) {
            return "SQL_VALUE";
        }
        if ("METRIC_CHART".equals(metricType)) {
            return "SQL_CHART";
        }
        if ("METRIC_TABLE".equals(metricType)) {
            return "SQL_TABLE";
        }
        return metricType;
    }

    private static void inferChartTypeFromAlias(String typeKey, ObjectNode dataSource) {
        if (dataSource == null) {
            return;
        }
        if (dataSource.has("chartType") && !dataSource.get("chartType").asText("").isEmpty()) {
            return;
        }
        if (typeKey.contains("PIE")) {
            dataSource.put("chartType", "pie");
        } else if (typeKey.contains("LINE")) {
            dataSource.put("chartType", "line");
        } else if (typeKey.contains("BAR")) {
            dataSource.put("chartType", "bar");
        }
    }

    private static boolean isMetricWidgetType(String widgetType) {
        String key = normalizeTypeKey(widgetType);
        if (key.startsWith("METRIC_")) {
            return true;
        }
        String alias = TYPE_ALIASES.get(key);
        return alias != null && alias.startsWith("METRIC_");
    }
}
