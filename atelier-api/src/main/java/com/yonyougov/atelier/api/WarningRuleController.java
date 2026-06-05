package com.yonyougov.atelier.api;

import com.yonyougov.atelier.api.dto.ApiResponse;
import com.yonyougov.atelier.domain.warning.WarningRule;
import com.yonyougov.atelier.warning.spi.WarningRuleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @PostMapping
    public ApiResponse<WarningRule> save(@RequestBody WarningRule rule) {
        return ApiResponse.ok(warningRuleService.saveRule(rule));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        warningRuleService.deleteRule(id);
        return ApiResponse.ok(null);
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
}
