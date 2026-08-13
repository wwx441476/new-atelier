/**
 * 按稳定 blockId 滚动到预览块（配合 data-block-id / id=block-...）。
 * 可选 root 限定左右双预览之一，避免 A/B 同 id 冲突时找错侧。
 */
export function scrollToPreviewBlock(
  blockId: string,
  behavior: ScrollBehavior = 'smooth',
  root?: ParentNode | null,
): boolean {
  if (!blockId) {
    return false;
  }
  const scope: ParentNode = root || document;
  const byData = scope.querySelector(`[data-block-id="${CSS.escape(blockId)}"]`);
  const el =
    (byData as HTMLElement | null) ||
    (root
      ? (root.querySelector(`#block-${CSS.escape(blockId)}`) as HTMLElement | null)
      : (document.getElementById(`block-${blockId}`) as HTMLElement | null));
  if (!el) {
    return false;
  }
  el.scrollIntoView({ behavior, block: 'center' });
  el.classList.add('flow-block-flash');
  window.setTimeout(() => el.classList.remove('flow-block-flash'), 1600);
  return true;
}

export function scrollToPreviewBlocks(
  blockIdsA: string[] | undefined,
  blockIdsB: string[] | undefined,
  rootA?: ParentNode | null,
  rootB?: ParentNode | null,
): void {
  const a = (blockIdsA || []).filter(Boolean);
  const b = (blockIdsB || []).filter(Boolean);
  if (a[0]) {
    scrollToPreviewBlock(a[0], 'smooth', rootA);
  }
  if (b[0]) {
    scrollToPreviewBlock(b[0], 'smooth', rootB);
  }
}
