import type { PreviewBlock } from '../../api/documentPreview';

/** 与后端 PreviewTextNormalize.normalizeForLocate 对齐 */
export function normalizeForLocate(text: string | undefined | null): string {
  if (!text) {
    return '';
  }
  return text.replace(/[|｜]/g, ' ').replace(/\s+/g, '');
}

function blockPlainText(block: PreviewBlock): string {
  if (block.type === 'TABLE' || block.type === 'SHEET') {
    const rows = block.table?.rows;
    if (rows?.length) {
      return rows.map((row) => (row || []).join('\t')).join('\n');
    }
  }
  if (block.runs?.length) {
    return block.runs.map((r) => r.text || '').join('');
  }
  return block.text || '';
}

/**
 * 用差异片段在预览块中反查 blockId（后端未挂锚点或表格「|」分隔时的前端兜底）。
 */
export function resolvePreviewBlockIds(
  blocks: PreviewBlock[] | undefined,
  snippet?: string | null,
): string[] {
  if (!snippet?.trim() || !blocks?.length) {
    return [];
  }
  const norm = normalizeForLocate(snippet);
  if (!norm) {
    return [];
  }
  const exact: string[] = [];
  const contains: string[] = [];
  for (const block of blocks) {
    if (!block.id || block.type === 'SECTION') {
      continue;
    }
    const sn = normalizeForLocate(blockPlainText(block));
    if (!sn) {
      continue;
    }
    if (sn === norm) {
      exact.push(block.id);
    } else if (sn.includes(norm) || (norm.includes(sn) && sn.length >= 8)) {
      contains.push(block.id);
    }
  }
  if (exact.length) {
    return exact;
  }
  if (contains.length) {
    return contains.slice(0, 3);
  }
  const lines = snippet.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n');
  if (lines.length > 1) {
    for (const line of lines) {
      const pn = normalizeForLocate(line);
      if (pn.length < 4) {
        continue;
      }
      for (const block of blocks) {
        if (!block.id || block.type === 'SECTION') {
          continue;
        }
        if (normalizeForLocate(blockPlainText(block)).includes(pn)) {
          return [block.id];
        }
      }
    }
  }
  if (norm.length > 24) {
    const head = norm.slice(0, 24);
    for (const block of blocks) {
      if (!block.id || block.type === 'SECTION') {
        continue;
      }
      if (normalizeForLocate(blockPlainText(block)).includes(head)) {
        return [block.id];
      }
    }
  }
  if (norm.length >= 12) {
    const mid = norm.slice(0, Math.min(norm.length, 32));
    for (const block of blocks) {
      if (!block.id || block.type === 'SECTION') {
        continue;
      }
      if (normalizeForLocate(blockPlainText(block)).includes(mid)) {
        return [block.id];
      }
    }
  }
  return [];
}
