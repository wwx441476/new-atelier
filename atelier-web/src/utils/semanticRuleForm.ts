import type { SemanticCheckGroup, SemanticFieldCheck, SemanticRuleConfig } from '../api/types';

export function createDefaultSemanticCheck(): SemanticFieldCheck {
  return {
    checkMode: 'VIOLATION',
    matchMode: 'HYBRID',
    hintKeywords: [],
    expandedKeywords: [],
  };
}

export function createDefaultSemanticGroup(): SemanticCheckGroup {
  return {
    checks: [createDefaultSemanticCheck()],
  };
}

/** 将旧版单字段配置迁移为 semanticGroups */
export function normalizeSemanticConfig(semantic?: SemanticRuleConfig): SemanticRuleConfig {
  if (!semantic) {
    return { semanticGroups: [createDefaultSemanticGroup()] };
  }
  if (semantic.semanticGroups && semantic.semanticGroups.length > 0) {
    return semantic;
  }
  if (semantic.fieldCode) {
    return {
      metaTableId: semantic.metaTableId,
      semanticGroups: [
        {
          checks: [
            {
              fieldCode: semantic.fieldCode,
              checkMode: 'VIOLATION',
              policy: semantic.policy,
              hintKeywords: semantic.hintKeywords,
              matchMode: semantic.matchMode || 'HYBRID',
              expandedKeywords: semantic.expandedKeywords,
            },
          ],
        },
      ],
    };
  }
  return {
    ...semantic,
    semanticGroups: [createDefaultSemanticGroup()],
  };
}

/** 收集配置中不重复的检测字段 */
export function collectSemanticFieldCodes(semantic?: SemanticRuleConfig): string[] {
  const normalized = normalizeSemanticConfig(semantic);
  const codes = new Set<string>();
  normalized.semanticGroups?.forEach((group) => {
    group.checks?.forEach((check) => {
      if (check.fieldCode?.trim()) {
        codes.add(check.fieldCode.trim());
      }
    });
  });
  return Array.from(codes);
}

export const SEMANTIC_SAMPLE_PRESETS: Record<string, Record<string, string>> = {
  remark_tobacco_tuition: {
    remark: '采购茅台两瓶',
    project_name: '2024春季学杂费',
  },
  remark_clean_tuition: {
    remark: '正常办公采购',
    project_name: '2024春季学杂费',
  },
};
