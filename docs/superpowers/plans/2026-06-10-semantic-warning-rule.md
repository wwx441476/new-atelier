# 预警规则语义合规与组合规则 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 扩展预警规则支持 SEMANTIC（元数据表文本语义合规）、COMPOSITE（指标表达式 + 语义 AND/OR），LLM 在界面配置，混合判定（词库 + LLM）。

**Architecture:** `RULE_TYPE` + `RULE_CONFIG` 扩展 `ATELIER_WARNING_RULE`；`ATELIER_APP_SETTING` 存 LLM 配置；`SemanticMatcher` SPI 实现词库/LLM/混合三层；预览以元数据表行为主数据源，COMPOSITE 同行粒度合并指标表达式与语义结果。

**Tech Stack:** Java 11, Spring Boot, JPA, QLExpress, React/Ant Design, Java HttpClient（OpenAI 兼容 API）

---

## File Map

| 文件 | 职责 |
|------|------|
| `atelier-domain/.../WarningRuleType.java` | 规则类型枚举 |
| `atelier-domain/.../SemanticRuleConfig.java` | 语义配置 POJO |
| `atelier-domain/.../CompositeRuleConfig.java` | 组合配置 POJO |
| `atelier-domain/.../SemanticMatchResult.java` | 语义判定结果 |
| `atelier-domain/.../SemanticLlmConfig.java` | LLM 界面配置 POJO |
| `atelier-domain/.../WarningRule.java` | 增加 ruleType、ruleConfig |
| `atelier-infra/.../AppSettingEntity.java` | 应用设置实体 |
| `atelier-infra/.../AppSettingJpaRepository.java` | 设置仓储 |
| `atelier-infra/.../WarningRuleEntity.java` | 增加 ruleType、ruleConfig |
| `atelier-warning/.../SemanticMatcher.java` | 语义匹配 SPI |
| `atelier-warning/.../KeywordSemanticMatcher.java` | 词库匹配 |
| `atelier-warning/.../LlmSemanticMatcher.java` | LLM 匹配 |
| `atelier-warning/.../HybridSemanticMatcher.java` | 混合匹配 |
| `atelier-warning/.../SemanticRuleEvaluator.java` | 语义规则求值 |
| `atelier-warning/.../WarningRulePreviewService.java` | 按类型预览 |
| `atelier-warning/.../KeywordExpansionService.java` | LLM 词库扩展 |
| `atelier-warning/.../WarningRuleServiceImpl.java` | 保存校验分支 |
| `atelier-api/.../AppSettingService.java` | 设置读写 |
| `atelier-api/.../SettingsController.java` | LLM 设置 API |
| `atelier-api/.../WarningRuleController.java` | 扩展语义 API |
| `atelier-web/.../SemanticLlmSettingsModal.tsx` | LLM 设置弹窗 |
| `atelier-web/.../SemanticRuleConfigForm.tsx` | 语义配置表单 |
| `atelier-web/.../WarningRulePage.tsx` | 规则类型 UI |
| `atelier-app/.../schema.sql` | DDL |
| `atelier-app/.../data.sql` | 演示 remark 列 + 组合规则种子 |

---

### Task 1: 数据库与领域模型

**Files:**
- Create: `atelier-domain/src/main/java/com/example/atelier/domain/warning/WarningRuleType.java`
- Create: `atelier-domain/src/main/java/com/example/atelier/domain/warning/SemanticRuleConfig.java`
- Create: `atelier-domain/src/main/java/com/example/atelier/domain/warning/CompositeRuleConfig.java`
- Create: `atelier-domain/src/main/java/com/example/atelier/domain/warning/SemanticMatchResult.java`
- Create: `atelier-domain/src/main/java/com/example/atelier/domain/settings/SemanticLlmConfig.java`
- Modify: `atelier-domain/src/main/java/com/example/atelier/domain/warning/WarningRule.java`
- Modify: `atelier-app/src/main/resources/schema.sql`

- [ ] **Step 1: 添加 WarningRuleType 枚举**

```java
public enum WarningRuleType {
    METRIC, SEMANTIC, COMPOSITE
}
```

- [ ] **Step 2: 添加 SemanticRuleConfig、CompositeRuleConfig、SemanticMatchResult、SemanticLlmConfig**

`SemanticMatchResult` 字段：`triggered`, `reason`, `layer`（keyword/llm/none）。

- [ ] **Step 3: WarningRule 增加字段**

```java
private WarningRuleType ruleType;      // default METRIC
private CompositeRuleConfig ruleConfig; // SEMANTIC 时 semantic 在 ruleConfig.semantic
```

- [ ] **Step 4: schema.sql 增加列与设置表**

```sql
-- ATELIER_WARNING_RULE
RULE_TYPE VARCHAR(20) DEFAULT 'METRIC',
RULE_CONFIG CLOB

-- ATELIER_APP_SETTING (新表)
```

- [ ] **Step 5: 编译验证**

```bash
mvn compile -pl atelier-domain,atelier-app -am -q
```

---

### Task 2: 基础设施层 — 实体与映射

**Files:**
- Create: `atelier-infra/src/main/java/com/example/atelier/infra/persistence/entity/AppSettingEntity.java`
- Create: `atelier-infra/src/main/java/com/example/atelier/infra/persistence/jpa/AppSettingJpaRepository.java`
- Modify: `atelier-infra/src/main/java/com/example/atelier/infra/persistence/entity/WarningRuleEntity.java`
- Modify: `WarningRuleServiceImpl.toDomain/save` 映射 ruleType/ruleConfig JSON

- [ ] **Step 1: AppSettingEntity + Repository**

- [ ] **Step 2: WarningRuleEntity 增加 ruleType、ruleConfig（@Lob JSON 字符串）**

- [ ] **Step 3: JSON 映射工具（复用 DataSourcePropsMapper 模式）**

创建 `RuleConfigMapper.java`：`CompositeRuleConfig` ↔ JSON。

- [ ] **Step 4: 编译**

```bash
mvn compile -pl atelier-infra,atelier-warning -am -q
```

---

### Task 3: 词库语义匹配器（TDD）

**Files:**
- Create: `atelier-warning/src/main/java/com/example/atelier/warning/evaluator/SemanticMatcher.java`
- Create: `atelier-warning/src/main/java/com/example/atelier/warning/evaluator/KeywordSemanticMatcher.java`
- Create: `atelier-warning/src/test/java/com/example/atelier/warning/evaluator/KeywordSemanticMatcherTest.java`

- [ ] **Step 1: 写失败测试**

```java
@Test
public void shouldMatchKeywordInText() {
    SemanticRuleConfig config = SemanticRuleConfig.builder()
        .hintKeywords(Arrays.asList("茅台", "五粮液"))
        .expandedKeywords(Arrays.asList("飞天"))
        .build();
    KeywordSemanticMatcher matcher = new KeywordSemanticMatcher();
    SemanticMatchResult result = matcher.match("本次采购茅台两瓶", config);
    assertTrue(result.isTriggered());
    assertEquals("keyword", result.getLayer());
    assertTrue(result.getReason().contains("茅台"));
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -pl atelier-warning -Dtest=KeywordSemanticMatcherTest -q
```

- [ ] **Step 3: 实现 KeywordSemanticMatcher**

匹配逻辑：hintKeywords + expandedKeywords，大小写不敏感 contains。

- [ ] **Step 4: 测试通过**

---

### Task 4: LLM 语义匹配器与设置服务

**Files:**
- Create: `atelier-warning/src/main/java/com/example/atelier/warning/evaluator/LlmSemanticMatcher.java`
- Create: `atelier-warning/src/main/java/com/example/atelier/warning/evaluator/HybridSemanticMatcher.java`
- Create: `atelier-api/src/main/java/com/example/atelier/api/service/AppSettingService.java`
- Create: `atelier-api/src/main/java/com/example/atelier/api/SettingsController.java`
- Create: `atelier-warning/src/main/java/com/example/atelier/warning/service/KeywordExpansionService.java`

- [ ] **Step 1: AppSettingService**

- `getSemanticLlmConfig()` — 读 `semantic.llm`，响应脱敏
- `saveSemanticLlmConfig(config, apiKey)` — apiKey 空则保留
- `testConnection(config)` — 发送最小 prompt

- [ ] **Step 2: SettingsController**

`GET/PUT/POST /api/v2/settings/semantic-llm` + `/test`

- [ ] **Step 3: LlmSemanticMatcher**

Java HttpClient 调用 `{baseUrl}/chat/completions`，解析 JSON 响应。

- [ ] **Step 4: HybridSemanticMatcher**

先 Keyword，未命中且 LLM 已启用则调 Llm。

- [ ] **Step 5: KeywordExpansionService**

保存规则或 `expand-keywords` 时调用 LLM 生成 expandedKeywords。

---

### Task 5: 预警规则服务扩展

**Files:**
- Create: `atelier-warning/src/main/java/com/example/atelier/warning/service/SemanticRuleEvaluator.java`
- Create: `atelier-warning/src/main/java/com/example/atelier/warning/service/WarningRulePreviewService.java`
- Modify: `atelier-warning/src/main/java/com/example/atelier/warning/service/WarningRuleServiceImpl.java`
- Modify: `atelier-warning/src/main/java/com/example/atelier/warning/spi/WarningRuleService.java`

- [ ] **Step 1: saveRule 分支校验**

| ruleType | 校验 |
|----------|------|
| METRIC | 现有 expression 校验 |
| SEMANTIC | semanticConfig 完整，无 expression |
| COMPOSITE | expression + semanticConfig + triggerLogic |

- [ ] **Step 2: SemanticRuleEvaluator**

封装 HybridSemanticMatcher，注入 SemanticLlmConfig。

- [ ] **Step 3: WarningRulePreviewService**

- `METRIC` → 现有 metricQuery 逻辑
- `SEMANTIC` → metadata preview + 语义判定
- `COMPOSITE` → metadata preview + 行内指标上下文 + AND/OR

- [ ] **Step 4: 集成测试**

扩展 `WarningRuleApiIntegrationTest`：
- SEMANTIC 词库命中
- COMPOSITE AND 部分触发

---

### Task 6: API 层扩展

**Files:**
- Modify: `atelier-api/src/main/java/com/example/atelier/api/WarningRuleController.java`
- Modify: `atelier-domain/.../WarningRulePreviewResult.java`（可选扩展 headers）

- [ ] **Step 1: 新增端点**

```java
@PostMapping("/validate-semantic")
@PostMapping("/expand-keywords")
```

- [ ] **Step 2: save/list/get 透传 ruleType/ruleConfig**

- [ ] **Step 3: 集成测试**

```bash
mvn test -pl atelier-app -Dtest=WarningRuleApiIntegrationTest -q
```

---

### Task 7: 前端类型与 API

**Files:**
- Modify: `atelier-web/src/api/types.ts`
- Modify: `atelier-web/src/api/warning.ts`
- Create: `atelier-web/src/api/settings.ts`

- [ ] **Step 1: 类型扩展**

```typescript
export type WarningRuleType = 'METRIC' | 'SEMANTIC' | 'COMPOSITE';
export interface SemanticRuleConfig { ... }
export interface CompositeRuleConfig { ... }
```

- [ ] **Step 2: warningApi 增加 validateSemantic、expandKeywords**

- [ ] **Step 3: settingsApi 增加 get/save/testSemanticLlm**

---

### Task 8: 前端 — LLM 设置弹窗

**Files:**
- Create: `atelier-web/src/components/SemanticLlmSettingsModal.tsx`
- Modify: `atelier-web/src/layouts/AdminLayout.tsx`

- [ ] **Step 1: SemanticLlmSettingsModal**

表单：启用、服务商、API Key（留空保持不变）、模型、Base URL、超时；测试连接 + 保存。

- [ ] **Step 2: AdminLayout 顶部按钮「语义检测设置」**

---

### Task 9: 前端 — 预警规则表单

**Files:**
- Create: `atelier-web/src/components/SemanticRuleConfigForm.tsx`
- Modify: `atelier-web/src/pages/WarningRulePage.tsx`

- [ ] **Step 1: 规则类型 Radio（METRIC / SEMANTIC / COMPOSITE）**

- [ ] **Step 2: SemanticRuleConfigForm**

元数据表 Select（metadataApi.listTables）→ 文本字段 Select → policy TextArea → hintKeywords Tags → matchMode Radio。

- [ ] **Step 3: COMPOSITE 展示**

指标表达式（现有 WarningExpressionField）+ triggerLogic AND/OR + SemanticRuleConfigForm。

- [ ] **Step 4: 列表列展示规则摘要**

- [ ] **Step 5: 预览弹窗增加 _matchReason、子条件列**

- [ ] **Step 6: 构建验证**

```bash
cd atelier-web && npm run build
```

---

### Task 10: 演示数据与配置导出

**Files:**
- Modify: `atelier-app/src/main/resources/data.sql`
- Modify: `atelier-api/src/main/java/com/example/atelier/api/service/ConfigBundleService.java`

- [ ] **Step 1: orders 表增加 remark 列与样例数据**

```sql
ALTER TABLE orders ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
UPDATE orders SET remark = '采购茅台两瓶' WHERE id = 1;  -- 示例
```

- [ ] **Step 2: 种子 COMPOSITE 规则**

`low_profit_bad_remark`: profit < 500 AND remark 烟酒语义。

- [ ] **Step 3: ConfigBundleService 导出/导入 ruleType + ruleConfig**

确认不含 semantic.llm 设置。

---

## Spec Coverage Checklist

| Spec 章节 | Task |
|-----------|------|
| 规则类型 METRIC/SEMANTIC/COMPOSITE | Task 1, 5, 9 |
| RULE_TYPE + RULE_CONFIG DDL | Task 1, 2 |
| ATELIER_APP_SETTING | Task 1, 2, 4 |
| 混合判定流程 | Task 3, 4 |
| LLM 界面配置 | Task 4, 8 |
| 预览数据流 | Task 5 |
| API 变更 | Task 6 |
| 前端表单 | Task 9 |
| 配置导出（不含 LLM Key） | Task 10 |
| 演示数据 | Task 10 |

## P2/P3 延后

- 跨粒度指标关联（spec P3）不在本计划 Task 内。
- 批量预览 LLM 限流优化后续迭代。
