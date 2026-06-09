package com.example.atelier.domain.warning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpressionValidateResult {

    private boolean valid;

    private String normalizedExpression;

    /** 表达式中引用的指标变量 */
    private List<String> usedVariables;

    /** 未在关联指标中声明的变量 */
    private List<String> unknownVariables;

    /** 已关联但未在表达式中使用的指标 */
    private List<String> unusedMetrics;

    private String message;

    /** 试算样例值是否可成功求值 */
    private Boolean sampleEvaluated;

    private Boolean sampleTriggered;
}
