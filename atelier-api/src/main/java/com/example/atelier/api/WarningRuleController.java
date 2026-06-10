package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.api.dto.MetricQueryApiRequest;
import com.example.atelier.api.dto.WarningRulePreviewRequest;
import com.example.atelier.domain.metric.FilterCondition;
import com.example.atelier.domain.metric.FilterGroup;
import com.example.atelier.domain.metric.FilterOperator;
import com.example.atelier.domain.warning.ExpressionValidateResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import com.example.atelier.domain.warning.SemanticValidateResult;
import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.domain.warning.WarningRulePreviewResult;
import com.example.atelier.warning.spi.WarningRuleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 预警规则管理 API — /api/v2/warning/rules。
 */
@RestController
@RequestMapping("/api/v2/warning/rules")
public class WarningRuleController {

    private final WarningRuleService warningRuleService;

    public WarningRuleController(WarningRuleService warningRuleService) {
        this.warningRuleService = warningRuleService;
    }

    @GetMapping
    public ApiResponse<List<WarningRule>> list() {
        return ApiResponse.ok(warningRuleService.listRules());
    }

    @GetMapping("/{id}")
    public ApiResponse<WarningRule> get(@PathVariable String id) {
        return warningRuleService.getRule(id)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("预警规则不存在: " + id));
    }

    @GetMapping("/{id}/preview")
    public ApiResponse<WarningRulePreviewResult> preview(
            @PathVariable String id,
            @RequestParam(value = "pageIndex", defaultValue = "1") int pageIndex,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(warningRuleService.previewRule(id, pageIndex, pageSize, null, null));
    }

    @PostMapping("/{id}/preview")
    public ApiResponse<WarningRulePreviewResult> previewWithFilters(
            @PathVariable String id,
            @RequestBody(required = false) WarningRulePreviewRequest request) {
        WarningRulePreviewRequest body = request != null ? request : new WarningRulePreviewRequest();
        return ApiResponse.ok(warningRuleService.previewRule(
                id,
                body.getPageIndex(),
                body.getPageSize(),
                toFilterConditions(body.getFilters()),
                toFilterGroups(body.getFilterGroups())));
    }

    @PostMapping
    public ApiResponse<WarningRule> save(@RequestBody WarningRule rule) {
        return ApiResponse.ok(warningRuleService.saveRule(rule));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        warningRuleService.deleteRule(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/validate-expression")
    public ApiResponse<ExpressionValidateResult> validateExpression(@RequestBody Map<String, Object> request) {
        String expression = (String) request.get("expression");
        @SuppressWarnings("unchecked")
        List<String> metricCodes = (List<String>) request.get("metricCodes");
        return ApiResponse.ok(warningRuleService.validateExpression(expression, metricCodes));
    }

    @PostMapping("/evaluate")
    public ApiResponse<Map<String, Object>> evaluate(@RequestBody Map<String, Object> request) {
        String expression = (String) request.get("expression");
        @SuppressWarnings("unchecked")
        Map<String, Object> metricValues = (Map<String, Object>) request.get("metricValues");
        boolean triggered = warningRuleService.evaluateExpression(expression, metricValues);
        Map<String, Object> result = new HashMap<>();
        result.put("triggered", triggered);
        return ApiResponse.ok(result);
    }

    @PostMapping("/validate-semantic")
    public ApiResponse<SemanticValidateResult> validateSemantic(@RequestBody Map<String, Object> request) {
        SemanticRuleConfig config = parseSemanticConfig(request.get("semanticConfig"));
        String sampleText = (String) request.get("sampleText");
        return ApiResponse.ok(warningRuleService.validateSemantic(config, sampleText));
    }

    @PostMapping("/expand-keywords")
    public ApiResponse<Map<String, Object>> expandKeywords(@RequestBody Map<String, Object> request) {
        SemanticRuleConfig config = parseSemanticConfig(request.get("semanticConfig"));
        List<String> keywords = warningRuleService.expandKeywords(config);
        Map<String, Object> result = new HashMap<>();
        result.put("keywords", keywords);
        return ApiResponse.ok(result);
    }

    @SuppressWarnings("unchecked")
    private SemanticRuleConfig parseSemanticConfig(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof SemanticRuleConfig) {
            return (SemanticRuleConfig) raw;
        }
        if (raw instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) raw;
            SemanticRuleConfig config = new SemanticRuleConfig();
            config.setMetaTableId((String) map.get("metaTableId"));
            config.setFieldCode((String) map.get("fieldCode"));
            config.setPolicy((String) map.get("policy"));
            config.setMatchMode((String) map.get("matchMode"));
            Object hints = map.get("hintKeywords");
            if (hints instanceof List) {
                config.setHintKeywords((List<String>) hints);
            }
            Object expanded = map.get("expandedKeywords");
            if (expanded instanceof List) {
                config.setExpandedKeywords((List<String>) expanded);
            }
            return config;
        }
        return null;
    }

    private List<FilterCondition> toFilterConditions(List<MetricQueryApiRequest.FilterDto> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        return filters.stream().map(this::toFilterCondition).collect(Collectors.toList());
    }

    private List<FilterGroup> toFilterGroups(List<MetricQueryApiRequest.FilterGroupDto> filterGroups) {
        if (filterGroups == null || filterGroups.isEmpty()) {
            return null;
        }
        return filterGroups.stream()
                .map(group -> FilterGroup.builder()
                        .conditions(group.getConditions() == null ? null :
                                group.getConditions().stream()
                                        .map(this::toFilterCondition)
                                        .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    private FilterCondition toFilterCondition(MetricQueryApiRequest.FilterDto filter) {
        return FilterCondition.builder()
                .field(filter.getField())
                .operator(parseOperator(filter.getOperator()))
                .values(filter.getValues())
                .build();
    }

    private FilterOperator parseOperator(String operator) {
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("过滤运算符不能为空");
        }
        String normalized = operator.toUpperCase().replace(" ", "_");
        if ("GTE".equals(normalized)) {
            normalized = "GE";
        } else if ("LTE".equals(normalized)) {
            normalized = "LE";
        }
        return FilterOperator.valueOf(normalized);
    }
}
