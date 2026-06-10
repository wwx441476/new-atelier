package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.warning.SemanticCheckGroup;
import com.example.atelier.domain.warning.SemanticFieldCheck;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import com.example.atelier.domain.warning.SemanticValidateResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SemanticRuleValidator {

    public SemanticValidateResult validate(SemanticRuleConfig config) {
        List<String> errors = new ArrayList<>();
        if (config == null) {
            errors.add("语义配置不能为空");
        } else {
            if (isBlank(config.getMetaTableId())) {
                errors.add("请选择元数据表");
            }
            List<SemanticCheckGroup> groups = SemanticRuleConfigSupport.normalizeGroups(config);
            if (groups.isEmpty()) {
                errors.add("请至少配置一条语义条件");
            }
            int groupIndex = 0;
            for (SemanticCheckGroup group : groups) {
                groupIndex++;
                if (group.getChecks() == null || group.getChecks().isEmpty()) {
                    errors.add("条件组 " + groupIndex + " 不能为空");
                    continue;
                }
                Set<String> fieldsInGroup = new HashSet<>();
                for (SemanticFieldCheck check : group.getChecks()) {
                    if (isBlank(check.getFieldCode())) {
                        errors.add("条件组 " + groupIndex + " 存在未选择字段的条件");
                    } else if (!fieldsInGroup.add(check.getFieldCode().trim())) {
                        errors.add("条件组 " + groupIndex + " 中字段 " + check.getFieldCode() + " 重复");
                    }
                    if (isBlank(check.getPolicy())) {
                        errors.add("字段 " + check.getFieldCode() + " 的策略不能为空");
                    }
                }
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
