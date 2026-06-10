package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.warning.SemanticRuleConfig;
import com.example.atelier.domain.warning.SemanticValidateResult;

import java.util.ArrayList;
import java.util.List;

public class SemanticRuleValidator {

    public SemanticValidateResult validate(SemanticRuleConfig config) {
        List<String> errors = new ArrayList<>();
        if (config == null) {
            errors.add("语义配置不能为空");
        } else {
            if (isBlank(config.getMetaTableId())) {
                errors.add("请选择元数据表");
            }
            if (isBlank(config.getFieldCode())) {
                errors.add("请选择检测字段");
            }
            if (isBlank(config.getPolicy())) {
                errors.add("请填写合规策略");
            }
        }
        boolean valid = errors.isEmpty();
        return SemanticValidateResult.builder()
                .valid(valid)
                .message(valid ? "语义配置有效" : String.join("；", errors))
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
