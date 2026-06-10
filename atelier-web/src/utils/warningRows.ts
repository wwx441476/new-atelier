export function filterMatchedRows(rows: Record<string, unknown>[] = []): Record<string, unknown>[] {
  return rows.filter((row) => row._triggered === true);
}
