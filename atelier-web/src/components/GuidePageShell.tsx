import type { ReactNode } from 'react';

interface GuidePageShellProps {
  children: ReactNode;
}

/**
 * 为页面主内容区包裹导览锚点，配合 Tour 高亮表格等区域。
 */
export default function GuidePageShell({ children }: GuidePageShellProps) {
  return <div id="guide-main-content">{children}</div>;
}
