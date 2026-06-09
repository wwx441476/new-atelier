const CLAUSE_BREAKS: Array<{ pattern: RegExp; replacement: string }> = [
  { pattern: /\s+UNION ALL\s+/gi, replacement: '\nUNION ALL\n' },
  { pattern: /\s+UNION\s+/gi, replacement: '\nUNION\n' },
  { pattern: /\s+INNER JOIN\s+/gi, replacement: '\nINNER JOIN ' },
  { pattern: /\s+LEFT JOIN\s+/gi, replacement: '\nLEFT JOIN ' },
  { pattern: /\s+RIGHT JOIN\s+/gi, replacement: '\nRIGHT JOIN ' },
  { pattern: /(?<!(?:INNER|LEFT|RIGHT|FULL|CROSS)\s)JOIN\s+/gi, replacement: '\nJOIN ' },
  { pattern: /\s+GROUP BY\s+/gi, replacement: '\nGROUP BY ' },
  { pattern: /\s+ORDER BY\s+/gi, replacement: '\nORDER BY ' },
  { pattern: /\s+HAVING\s+/gi, replacement: '\nHAVING ' },
  { pattern: /\s+WHERE\s+/gi, replacement: '\nWHERE ' },
  { pattern: /\s+FROM\s+/gi, replacement: '\nFROM ' },
];

function splitCommaItems(part: string): string[] {
  const items: string[] = [];
  let current = '';
  let depth = 0;
  let inQuote = false;

  for (let i = 0; i < part.length; i += 1) {
    const ch = part[i];
    if (ch === "'" && part[i - 1] !== '\\') {
      inQuote = !inQuote;
    }
    if (!inQuote) {
      if (ch === '(') depth += 1;
      if (ch === ')') depth -= 1;
      if (ch === ',' && depth === 0) {
        const trimmed = current.trim();
        if (trimmed) items.push(trimmed);
        current = '';
        continue;
      }
    }
    current += ch;
  }
  const trimmed = current.trim();
  if (trimmed) items.push(trimmed);
  return items;
}

function formatCreateTable(sql: string): string | null {
  const match = sql.match(
    /^(CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?)\s+(\S+)\s*\(([\s\S]*)\)\s*;?$/i,
  );
  if (!match) return null;

  const prefix = match[1].replace(/\s+/g, ' ').trim();
  const tableName = match[2];
  const columns = splitCommaItems(match[3]);
  if (columns.length === 0) {
    return `${prefix} ${tableName} ()`;
  }
  return `${prefix} ${tableName} (\n  ${columns.join(',\n  ')}\n)`;
}

function formatSelectSql(sql: string): string {
  let result = sql.replace(/\s+/g, ' ').trim();
  for (const { pattern, replacement } of CLAUSE_BREAKS) {
    result = result.replace(pattern, replacement);
  }

  const selectMatch = result.match(/^SELECT\s+([\s\S]*)$/i);
  if (selectMatch) {
    const rest = selectMatch[1];
    const fromIndex = rest.search(/\nFROM\s+/i);
    if (fromIndex >= 0) {
      const selectPart = rest.slice(0, fromIndex).trim();
      const tail = rest.slice(fromIndex);
      const items = splitCommaItems(selectPart);
      result = `SELECT\n  ${items.map((item, index) => `${index === 0 ? '' : '  '}${item}`).join(',\n')}${tail}`;
    } else {
      const items = splitCommaItems(rest);
      result = `SELECT\n  ${items.map((item, index) => `${index === 0 ? '' : '  '}${item}`).join(',\n')}`;
    }
  }

  result = result.replace(/\s+ON\s+/gi, '\n  ON ');
  result = result.replace(/\s+(AND|OR)\s+/gi, '\n  $1 ');
  result = result.replace(/\n{3,}/g, '\n\n');
  return result.trim();
}

export function formatSql(sql: string): string {
  if (!sql?.trim()) return '';

  const normalized = sql.trim().replace(/;+\s*$/, '');
  const createTable = formatCreateTable(normalized);
  if (createTable) return createTable;

  return formatSelectSql(normalized);
}
