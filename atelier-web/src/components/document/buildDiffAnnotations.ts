import type { PreviewBlock } from '../../api/documentPreview';
import type {
  CompareResult,
  DiffOpType,
  ParagraphOp,
  StructureOp,
  TextHunk,
} from '../../api/documentCompare';
import { normalizeForLocate, resolvePreviewBlockIds } from './resolvePreviewBlockIds';

/** A：相对 B 看到的变化；B：相对 A 看到的变化 */
export type AnnotationPerspective = 'A' | 'B';

export interface DiffAnnotation {
  id: string;
  type: DiffOpType;
  perspective: AnnotationPerspective;
  /** 如：相对B·删除 / 相对A·新增 / 相对B·改为 / 相对A·改自 */
  label: string;
  oldText?: string;
  newText?: string;
  source: 'paragraph' | 'structure' | 'text';
}

export type AnnotationsByBlockId = Record<string, DiffAnnotation[]>;

export interface DiffAnnotationMaps {
  a: AnnotationsByBlockId;
  b: AnnotationsByBlockId;
  /** A 侧无落点批注：相对 B 的新增（B 有而 A 无） */
  aSideNotes: DiffAnnotation[];
  /** B 侧无落点批注：相对 A 的删除（A 有而 B 无） */
  bSideNotes: DiffAnnotation[];
  placed: number;
  orphan: number;
}

function clip(text: string | undefined, max = 160): string | undefined {
  if (!text) {
    return undefined;
  }
  const t = text.replace(/\s+/g, ' ').trim();
  if (!t) {
    return undefined;
  }
  return t.length > max ? `${t.slice(0, max)}…` : t;
}

function contentLen(a: DiffAnnotation): number {
  return (a.oldText || '').length + (a.newText || '').length;
}

function sourceRank(source: DiffAnnotation['source']): number {
  if (source === 'paragraph') {
    return 0;
  }
  if (source === 'structure') {
    return 1;
  }
  return 2;
}

function fingerprint(a: DiffAnnotation): string {
  return [a.perspective, a.type, normalizeForLocate(a.oldText), normalizeForLocate(a.newText)].join(
    '|',
  );
}

function resolveIds(
  ids: string[] | undefined,
  blocks: PreviewBlock[] | undefined,
  snippet?: string,
): string[] {
  const fromApi = (ids || []).filter(Boolean);
  if (fromApi.length) {
    return fromApi;
  }
  return resolvePreviewBlockIds(blocks, snippet);
}

function pickPrimaryId(ids: string[]): string | undefined {
  return ids.find(Boolean);
}

function pushAnno(map: AnnotationsByBlockId, blockId: string, anno: DiffAnnotation): boolean {
  const key = fingerprint(anno);
  const list = map[blockId] || (map[blockId] = []);
  if (list.some((x) => x.id === anno.id || fingerprint(x) === key)) {
    return false;
  }
  // 全局同侧同指纹
  for (const other of Object.values(map)) {
    if (other.some((x) => fingerprint(x) === key)) {
      return false;
    }
  }
  list.push(anno);
  return true;
}

function labelFor(type: DiffOpType, perspective: AnnotationPerspective): string {
  if (perspective === 'A') {
    switch (type) {
      case 'REMOVED':
        return '相对B·删除';
      case 'MODIFIED':
      case 'MOVED':
        return '相对B·修改';
      case 'ADDED':
        return '相对B·新增';
      default:
        return type;
    }
  }
  switch (type) {
    case 'ADDED':
      return '相对A·新增';
    case 'MODIFIED':
    case 'MOVED':
      return '相对A·修改';
    case 'REMOVED':
      return '相对A·删除';
    default:
      return type;
  }
}

/**
 * 按视角挂载：
 * - A 侧：删除 / 改为（相对 B）
 * - B 侧：新增 / 改自（相对 A）
 * 纯删除不挂 B，纯新增不挂 A。
 */
function pushSideNote(list: DiffAnnotation[], anno: DiffAnnotation): boolean {
  const key = fingerprint(anno);
  if (list.some((x) => fingerprint(x) === key || x.id === anno.id)) {
    return false;
  }
  list.push(anno);
  return true;
}

function placeOp(
  maps: DiffAnnotationMaps,
  blocksA: PreviewBlock[] | undefined,
  blocksB: PreviewBlock[] | undefined,
  op: {
    type: DiffOpType;
    oldText?: string;
    newText?: string;
    blockIdsA?: string[];
    blockIdsB?: string[];
  },
  id: string,
  source: DiffAnnotation['source'],
) {
  if (op.type === 'EQUAL') {
    return;
  }
  const oldClip = clip(op.oldText);
  const newClip = clip(op.newText);
  const idA = pickPrimaryId(resolveIds(op.blockIdsA, blocksA, op.oldText));
  const idB = pickPrimaryId(resolveIds(op.blockIdsB, blocksB, op.newText || op.oldText));
  let placed = false;

  if (op.type === 'REMOVED') {
    // A：落在被删块上
    if (idA) {
      placed =
        pushAnno(maps.a, idA, {
          id: `${id}-a`,
          type: 'REMOVED',
          perspective: 'A',
          label: labelFor('REMOVED', 'A'),
          oldText: oldClip,
          source,
        }) || placed;
    }
    // B：正文无此块，用侧栏摘要「相对A·删除」
    placed =
      pushSideNote(maps.bSideNotes, {
        id: `${id}-b-side`,
        type: 'REMOVED',
        perspective: 'B',
        label: labelFor('REMOVED', 'B'),
        oldText: oldClip,
        source,
      }) || placed;
  } else if (op.type === 'ADDED') {
    if (idB) {
      placed =
        pushAnno(maps.b, idB, {
          id: `${id}-b`,
          type: 'ADDED',
          perspective: 'B',
          label: labelFor('ADDED', 'B'),
          newText: newClip,
          source,
        }) || placed;
    }
    // A：正文无此块，侧栏摘要「相对B·对方新增」
    placed =
      pushSideNote(maps.aSideNotes, {
        id: `${id}-a-side`,
        type: 'ADDED',
        perspective: 'A',
        label: labelFor('ADDED', 'A'),
        newText: newClip,
        source,
      }) || placed;
  } else {
    if (idA) {
      placed =
        pushAnno(maps.a, idA, {
          id: `${id}-a`,
          type: op.type,
          perspective: 'A',
          label: labelFor(op.type, 'A'),
          oldText: oldClip,
          newText: newClip,
          source,
        }) || placed;
    }
    if (idB) {
      placed =
        pushAnno(maps.b, idB, {
          id: `${id}-b`,
          type: op.type,
          perspective: 'B',
          label: labelFor(op.type, 'B'),
          oldText: oldClip,
          newText: newClip,
          source,
        }) || placed;
    }
  }

  if (placed) {
    maps.placed += 1;
  } else if (
    (op.type === 'REMOVED' && !idA && !oldClip) ||
    (op.type === 'ADDED' && !idB && !newClip) ||
    ((op.type === 'MODIFIED' || op.type === 'MOVED') && !idA && !idB)
  ) {
    maps.orphan += 1;
  }
}

/** 同类型嵌套：旧∈旧 且 新∈新 */
function isSameTypeNested(inner: DiffAnnotation, outer: DiffAnnotation): boolean {
  if (inner.type !== outer.type || inner.id === outer.id) {
    return false;
  }
  if (contentLen(inner) > contentLen(outer)) {
    return false;
  }
  if (
    contentLen(inner) === contentLen(outer) &&
    sourceRank(inner.source) <= sourceRank(outer.source)
  ) {
    return false;
  }
  const iOld = normalizeForLocate(inner.oldText);
  const iNew = normalizeForLocate(inner.newText);
  const oOld = normalizeForLocate(outer.oldText);
  const oNew = normalizeForLocate(outer.newText);
  if (inner.type === 'REMOVED') {
    return iOld.length >= 4 && oOld.includes(iOld);
  }
  if (inner.type === 'ADDED') {
    return iNew.length >= 4 && oNew.includes(iNew);
  }
  const oldHit = iOld.length >= 4 && oOld.includes(iOld);
  const newHit = iNew.length >= 4 && oNew.includes(iNew);
  return oldHit && newHit;
}

/**
 * 跨类型冲突（同块）：
 * - A：删除 与 修改 指向同一段旧文 → 只留「改为」（说明不是单纯删掉）
 * - B：新增 与 修改 指向同一段新文 → 只留「改自」
 */
function conflictsWithModify(alone: DiffAnnotation, mod: DiffAnnotation, side: 'A' | 'B'): boolean {
  if (mod.type !== 'MODIFIED' && mod.type !== 'MOVED') {
    return false;
  }
  if (side === 'A' && alone.type === 'REMOVED') {
    const t = normalizeForLocate(alone.oldText);
    return t.length >= 4 && normalizeForLocate(mod.oldText).includes(t);
  }
  if (side === 'B' && alone.type === 'ADDED') {
    const t = normalizeForLocate(alone.newText);
    return t.length >= 4 && normalizeForLocate(mod.newText).includes(t);
  }
  return false;
}

function collapseList(list: DiffAnnotation[], side: 'A' | 'B'): DiffAnnotation[] {
  // 1) 同类型嵌套去重，留短
  const byType: DiffAnnotation[] = [];
  const sorted = [...list].sort((a, b) => contentLen(a) - contentLen(b));
  for (const anno of sorted) {
    let skip = false;
    for (let i = byType.length - 1; i >= 0; i--) {
      const other = byType[i];
      if (isSameTypeNested(anno, other)) {
        byType.splice(i, 1);
        continue;
      }
      if (isSameTypeNested(other, anno)) {
        skip = true;
        break;
      }
    }
    if (!skip) {
      byType.push(anno);
    }
  }

  // 2) 删除/新增 与 修改 冲突时丢掉删除/新增
  const mods = byType.filter((a) => a.type === 'MODIFIED' || a.type === 'MOVED');
  return byType.filter((a) => {
    if (a.type === 'MODIFIED' || a.type === 'MOVED') {
      return true;
    }
    return !mods.some((m) => conflictsWithModify(a, m, side));
  });
}

function pruneSide(map: AnnotationsByBlockId, side: 'A' | 'B'): void {
  for (const blockId of Object.keys(map)) {
    map[blockId] = collapseList(map[blockId], side);
    if (!map[blockId].length) {
      delete map[blockId];
    }
  }

  // 跨块同类型嵌套
  const entries = Object.entries(map).flatMap(([blockId, list]) =>
    list.map((anno) => ({ blockId, anno })),
  );
  const dropIds = new Set<string>();
  for (let i = 0; i < entries.length; i++) {
    for (let j = i + 1; j < entries.length; j++) {
      const left = entries[i].anno;
      const right = entries[j].anno;
      if (dropIds.has(left.id) || dropIds.has(right.id)) {
        continue;
      }
      if (isSameTypeNested(left, right)) {
        dropIds.add(right.id);
      } else if (isSameTypeNested(right, left)) {
        dropIds.add(left.id);
      }
    }
  }
  if (dropIds.size) {
    for (const blockId of Object.keys(map)) {
      map[blockId] = map[blockId].filter((a) => !dropIds.has(a.id));
      if (!map[blockId].length) {
        delete map[blockId];
      }
    }
  }
}

function pruneSideNotes(
  notes: DiffAnnotation[],
  blockMap: AnnotationsByBlockId,
  side: 'A' | 'B',
): DiffAnnotation[] {
  const mods = Object.values(blockMap)
    .flat()
    .filter((a) => a.type === 'MODIFIED' || a.type === 'MOVED');
  // 同类型嵌套
  let list = collapseList(notes, side);
  // 已被「修改」覆盖的删除/新增侧注去掉
  list = list.filter((a) => !mods.some((m) => conflictsWithModify(a, m, side)));
  // 限制条数，避免两份完全不同文档时刷屏
  const MAX = 40;
  if (list.length > MAX) {
    return list
      .slice()
      .sort((a, b) => contentLen(a) - contentLen(b))
      .slice(0, MAX);
  }
  return list;
}

function recountPlaced(maps: DiffAnnotationMaps): void {
  const ids = new Set<string>();
  for (const side of [maps.a, maps.b]) {
    for (const list of Object.values(side)) {
      for (const anno of list) {
        ids.add(anno.id.replace(/-[ab](-side)?$/, ''));
      }
    }
  }
  for (const anno of [...maps.aSideNotes, ...maps.bSideNotes]) {
    ids.add(anno.id.replace(/-[ab](-side)?$/, ''));
  }
  maps.placed = ids.size;
}

/**
 * 构建相对视角批注：
 * - 预览 A：块内 = 相对 B 的删除/改为；文首摘要 = 相对 B 的新增
 * - 预览 B：块内 = 相对 A 的新增/改自；文首摘要 = 相对 A 的删除
 */
export function buildDiffAnnotations(result: CompareResult | null | undefined): DiffAnnotationMaps {
  const maps: DiffAnnotationMaps = {
    a: {},
    b: {},
    aSideNotes: [],
    bSideNotes: [],
    placed: 0,
    orphan: 0,
  };
  if (!result) {
    return maps;
  }
  const blocksA = result.previewA?.blocks;
  const blocksB = result.previewB?.blocks;

  (result.paragraphOps || []).forEach((op: ParagraphOp, i) => {
    placeOp(maps, blocksA, blocksB, op, `p-${i}`, 'paragraph');
  });
  (result.structureOps || []).forEach((op: StructureOp, i) => {
    placeOp(maps, blocksA, blocksB, op, `s-${i}`, 'structure');
  });
  (result.textHunks || []).forEach((hunk: TextHunk, i) => {
    if (hunk.type === 'EQUAL') {
      return;
    }
    placeOp(
      maps,
      blocksA,
      blocksB,
      {
        type: hunk.type,
        oldText: (hunk.oldLines || []).join('\n'),
        newText: (hunk.newLines || []).join('\n'),
        blockIdsA: hunk.blockIdsA,
        blockIdsB: hunk.blockIdsB,
      },
      `t-${i}`,
      'text',
    );
  });

  pruneSide(maps.a, 'A');
  pruneSide(maps.b, 'B');
  maps.aSideNotes = pruneSideNotes(maps.aSideNotes, maps.a, 'A');
  maps.bSideNotes = pruneSideNotes(maps.bSideNotes, maps.b, 'B');
  recountPlaced(maps);

  return maps;
}
