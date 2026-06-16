/** 从 Copilot 原始响应中提取用户可读的 reply 文本 */
export function extractCopilotReply(content: string): string | null {
  const trimmed = content.trim();
  if (!trimmed.startsWith('{') || !trimmed.includes('"reply"')) {
    return null;
  }
  try {
    const parsed = JSON.parse(trimmed) as { reply?: unknown };
    if (typeof parsed.reply === 'string' && parsed.reply.trim()) {
      return parsed.reply.trim();
    }
  } catch {
    // fall through to regex
  }
  const match = trimmed.match(/"reply"\s*:\s*"((?:\\.|[^"\\])*)"/s);
  if (match?.[1]) {
    return unescapeJsonString(match[1]);
  }
  return null;
}

export function formatCopilotReply(content: string): string {
  const extracted = extractCopilotReply(content);
  return extracted ?? content;
}

function unescapeJsonString(value: string): string {
  return value
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, '\t')
    .replace(/\\"/g, '"')
    .replace(/\\\\/g, '\\');
}

export const COPILOT_TOOL_LABELS: Record<string, string> = {
  create_dashboard: '创建大屏',
  create_physical_table: '创建数据表',
  create_datasource: '创建数据源',
  create_meta_table: '登记元数据表',
  import_meta_tables: '同步元数据表',
  create_metric: '创建指标',
  create_warning_rule: '创建预警规则',
  run_warning_rule: '执行预警规则',
  get_warning_job_result: '获取预警结果',
  execute_sql: 'SQL 查询',
  execute_write_sql: 'SQL 写入',
};

export function copilotToolLabel(tool: string): string {
  return COPILOT_TOOL_LABELS[tool] ?? tool;
}
