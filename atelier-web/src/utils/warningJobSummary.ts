export function formatWarningJobSummary(options: {
  total?: number | null;
  matchedCount?: number | null;
  pageIndex?: number;
  pageSize?: number;
}): string {
  const { total, matchedCount, pageIndex = 1, pageSize = 20 } = options;
  const totalText = total != null ? String(total) : '-';
  const matchedText = matchedCount != null ? String(matchedCount) : '-';
  return `全表共 ${totalText} 条 · 第 ${pageIndex} 页（每页 ${pageSize} 条）本页命中 ${matchedText} 条`;
}
