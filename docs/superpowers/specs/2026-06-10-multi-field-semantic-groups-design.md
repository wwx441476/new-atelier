# 多字段语义条件组设计

**日期：** 2026-06-10  
**状态：** 待确认  
**依赖：** [2026-06-10-semantic-warning-rule-design.md](./2026-06-10-semantic-warning-rule-design.md)（P1 已实现）

## 背景

单字段语义规则无法满足「多列文本联合判定」场景。典型需求：

> **备注**不得包含烟酒相关内容，**并且** **项目名称**属于学杂费、教材费、学费一类（自由文本，无法穷举枚举）。

现有 `SemanticRuleConfig.fieldCode` 仅支持单列；`COMPOSITE` 仅能将「一个语义字段」与指标表达式组合，无法在语义层做多字段 AND/OR。

## 目标

1. 支持对同一元数据表**多个文本字段**分别配置语义策略。
2. 支持**条件组**组合：组内 AND、组间 OR（与指标 `filterGroups` 心智一致）。
3. 支持两种判定极性：**违规检测**（VIOLATION）与**正向符合**（REQUIREMENT）。
4. 自由文本字段走词库 + LLM 混合判定，不依赖维度枚举。
5. 向后兼容现有单字段 `semantic` 配置。

## 非目标（本期）

- 语义表达式字符串（如 `(c1 或 c2) 且 c3`）—— 表达能力更强但 UI/校验成本高，后续按需引入。
- 跨表多字段（不同 `metaTableId`）—— 本期限定同一 `metaTableId`。
- 将 REQUIREMENT 类条件转为维度维护。

---

## 用户示例

**业务规则：** 学杂费类项目的备注中若出现烟酒表述则预警。

| 字段 | 策略类型 | 策略描述 | 子条件为 true 当… |
|------|----------|----------|-------------------|
| `remark` | VIOLATION | 不得包含烟酒相关内容 | 文本**违反**策略 |
| `project_name` | REQUIREMENT | 属于学杂费、教材费、学费、杂费等教育收费类项目 | 文本**符合**策略 |

**组合：** 组内 AND → 两行子条件均 true 时，语义分组触发。

```
语义触发 = remark违规 AND project_name符合学杂费类
```

可与指标组合（COMPOSITE）：

```
最终触发 = (profit < 500) AND 语义触发
```

---

## 数据模型

### SemanticFieldCheck（单字段语义子条件）

```json
{
  "fieldCode": "project_name",
  "checkMode": "REQUIREMENT",
  "policy": "属于学杂费、教材费、学费、杂费、代办费等教育收费类项目",
  "hintKeywords": ["学杂费", "学费", "教材费"],
  "matchMode": "HYBRID",
  "expandedKeywords": []
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| fieldCode | 是 | 元数据表文本列 code |
| checkMode | 是 | `VIOLATION`（违反策略→true）或 `REQUIREMENT`（符合策略→true） |
| policy | 是 | 自然语言策略 |
| hintKeywords | 否 | 示例词 |
| matchMode | 否 | `KEYWORD` / `LLM` / `HYBRID`，默认 `HYBRID` |
| expandedKeywords | 否 | 扩展词库 |

### SemanticCheckGroup（条件组）

```json
{
  "checks": [
    { "fieldCode": "remark", "checkMode": "VIOLATION", "policy": "…", "matchMode": "HYBRID" },
    { "fieldCode": "project_name", "checkMode": "REQUIREMENT", "policy": "…", "matchMode": "HYBRID" }
  ]
}
```

- **组内：** 所有 `checks` 结果 AND。
- **多组：** 组与组之间 OR。

### SemanticRuleConfig 扩展

```json
{
  "metaTableId": "mt-orders",
  "semanticGroups": [
    {
      "checks": [ /* SemanticFieldCheck[] */ ]
    }
  ],
  "fieldCode": "remark",
  "policy": "…"
}
```

- **新配置**使用 `semanticGroups`。
- **旧配置**保留 `fieldCode` + `policy` 等顶层字段；加载时若 `semanticGroups` 为空，自动包装为单组单条件（`checkMode=VIOLATION`）。

### CompositeRuleConfig

不变：`triggerLogic` + `semantic`（`SemanticRuleConfig` 扩展后承载多字段组）。

---

## 求值逻辑

### 单子条件

1. 从行数据读取 `fieldCode` 文本。
2. 调用现有 `HybridSemanticMatcher`（词库优先 → LLM）。
3. 匹配器返回 `triggered`（语义上：文本是否**违反** policy）。
4. 按 `checkMode` 转换子条件结果：

| checkMode | 子条件 true 当 |
|-----------|----------------|
| VIOLATION | `matcher.triggered == true` |
| REQUIREMENT | `matcher.triggered == false` 且文本非空 |

空文本：VIOLATION → false；REQUIREMENT → false（不符合「必须是学杂费类」）。

### REQUIREMENT 与 LLM

- **词库（KEYWORD/HYBRID）：** 文本包含 hint/expanded 关键词 → 视为符合 REQUIREMENT。
- **LLM（LLM/HYBRID）：** 使用独立 prompt：`triggered=true` 表示文本**符合**策略（与 VIOLATION prompt 相反）。
- **日志：** 标明 `checkMode=REQUIREMENT` 与字段 code。

### 分组

```
groupResult = checks[0] AND checks[1] AND …
semanticResult = groupResults[0] OR groupResults[1] OR …
```

### COMPOSITE 合并

```
finalTriggered = metricTriggered AND/OR semanticResult  // 按 triggerLogic
```

---

## 预览结果扩展

| 字段 | 说明 |
|------|------|
| `_semanticTriggered` | 语义分组整体 |
| `_semanticCheck.{fieldCode}` | 各子条件是否 true |
| `_matchReason.{fieldCode}` | 各字段原因 |
| `_matchLayer.{fieldCode}` | keyword / llm / none |
| `_llmInvoked.{fieldCode}` | 是否调用 LLM |

保留 `_triggered`、`_matchReason`、`_matchLayer`（汇总：首个失败或触发子条件的信息，便于列表扫读）。

---

## API

### 校验 `POST /validate-semantic`

请求体扩展：

```json
{
  "semanticConfig": {
    "metaTableId": "mt-orders",
    "semanticGroups": [ { "checks": [ … ] } ]
  },
  "sampleRow": {
    "remark": "采购茅台两瓶",
    "project_name": "2024春季学杂费"
  }
}
```

- 配置校验：每组至少一个 check；`fieldCode` 不重复（同组内）；policy 非空。
- `sampleRow` 可选：返回各子条件及整体结果。

### 保存规则

`saveRule` 校验分支识别 `semanticGroups`；`maybeExpandKeywords` 对每个 check 分别扩展（或仅 HYBRID/KEYWORD 模式）。

---

## 前端

### SemanticRuleConfigForm 改造

- **条件组列表**（复用 `DimensionFilterGroupsForm` 交互模式）：
  - 每组内可添加多条「字段 + 策略类型 + 策略 + 示例词 + 判定方式」。
  - 组间提示：「满足任一组即触发语义条件」。
- **策略类型** Radio：`违规检测` / `必须符合`。
- 单字段旧表单数据编辑时自动展开为一组一条。

### 文案示例

| checkMode | 策略 placeholder |
|-----------|------------------|
| VIOLATION | 如：不得包含烟酒相关内容… |
| REQUIREMENT | 如：属于学杂费、教材费、学费一类项目… |

---

## 演示数据

### schema / data.sql

`orders` 表新增：

```sql
project_name VARCHAR  -- 项目名称，自由文本
```

示例行：

| remark | project_name | 语义预期 |
|--------|--------------|----------|
| 采购茅台两瓶 | 2024春季学杂费 | 触发 |
| 正常办公采购 | 2024春季学杂费 | 不触发 |
| 采购茅台两瓶 | 设备采购项目 | 不触发 |
| 商务接待用名贵礼盒 | 2024春季学杂费 | 不触发（remark 未违规） |

### 演示规则 `tuition_remark_tobacco`

- `ruleType`: `SEMANTIC`
- `semanticGroups`: 一组，remark VIOLATION + project_name REQUIREMENT

---

## 向后兼容

| 场景 | 行为 |
|------|------|
| 旧规则仅 `fieldCode` | 运行时包装为单组 VIOLATION |
| 导出/导入 | JSON 含 `semanticGroups`；旧包仅 `fieldCode` 仍可读 |
| 前端编辑旧规则 | 打开时迁移为条件组 UI |

---

## 模块变更摘要

| 模块 | 变更 |
|------|------|
| `atelier-domain` | `SemanticFieldCheck`, `SemanticCheckGroup`, `checkMode` 枚举；扩展 `SemanticRuleConfig` |
| `atelier-warning` | `SemanticGroupEvaluator`, REQUIREMENT matcher/prompt, 预览多列 |
| `atelier-api` | 校验 API 支持 `sampleRow` |
| `atelier-web` | 条件组表单、`checkMode` 选择 |
| `atelier-app` | DDL + 演示数据 |

---

## 测试要点

1. 单字段旧规则预览行为不变。
2. VIOLATION + REQUIREMENT 同行 AND：仅「茅台 + 学杂费」行触发。
3. REQUIREMENT 空 `project_name` 不触发。
4. 组间 OR：两组各一条，任一组全满足即触发。
5. COMPOSITE：指标 + 多字段语义 AND。
6. LLM 未配置：HYBRID 降级词库，REQUIREMENT 靠 hint 关键词（无法穷举时界面提示配置 LLM）。

---

## 已确认决策

- **项目名称等为自由文本**，无法穷举 → 使用 `REQUIREMENT` + 词库/LLM，不走维度枚举。
- **组合模型采用条件组**（组内 AND、组间 OR），与 `filterGroups` 一致，本期不实现语义表达式字符串。
