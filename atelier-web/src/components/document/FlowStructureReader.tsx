import type { ReactNode } from 'react';
import { Empty, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type {
  PreviewBlock,
  PreviewBlockType,
  PreviewInlineMark,
  PreviewRun,
} from '../../api/documentPreview';
import './FlowStructureReader.css';

function headingTag(level?: number): 'h1' | 'h2' | 'h3' {
  if (level != null && level <= 1) return 'h1';
  if (level === 2) return 'h2';
  return 'h3';
}

function tableColumns(header: string[], colCount: number): ColumnsType<Record<string, string>> {
  const cols: ColumnsType<Record<string, string>> = [];
  for (let i = 0; i < Math.max(colCount, 1); i++) {
    const key = `c${i}`;
    const title = (header[i] ?? '').trim()
      || String.fromCharCode(65 + (i % 26)) + (i >= 26 ? String(Math.floor(i / 26)) : '');
    cols.push({
      title,
      dataIndex: key,
      key,
      ellipsis: true,
    });
  }
  return cols;
}

function tableParts(rows: string[][]): {
  columns: ColumnsType<Record<string, string>>;
  data: Record<string, string>[];
} {
  const width = rows.reduce((max, row) => Math.max(max, row.length), 0);
  if (rows.length === 0) {
    return { columns: tableColumns([], width), data: [] };
  }
  // Word 等表格首行通常是表头，避免再显示 A/B 占位列名
  const header = rows[0] || [];
  const body = rows.length > 1 ? rows.slice(1) : rows;
  const useFirstAsHeader = rows.length > 1 && header.some((c) => (c ?? '').trim().length > 0);
  const headerRow = useFirstAsHeader ? header : [];
  const dataRows = useFirstAsHeader ? body : rows;
  const data = dataRows.map((row, idx) => {
    const record: Record<string, string> = { key: String(idx) };
    for (let i = 0; i < Math.max(width, 1); i++) {
      record[`c${i}`] = row[i] ?? '';
    }
    return record;
  });
  return { columns: tableColumns(headerRow, width), data };
}

/** 将文本中的换行转为 <br/>，避免 HTML 把 \\n 折叠成空格 */
function renderTextWithBreaks(text: string, keyPrefix: string): ReactNode {
  const parts = (text || '').split('\n');
  return parts.map((line, i) => (
    <span key={`${keyPrefix}-${i}`}>
      {line}
      {i < parts.length - 1 ? <br /> : null}
    </span>
  ));
}

function renderRuns(runs?: PreviewRun[], fallbackText?: string): ReactNode {
  if (runs && runs.length > 0) {
    return runs.map((run, i) => {
      let node: ReactNode = renderTextWithBreaks(run.text ?? '', `r${i}`);
      const marks: PreviewInlineMark[] = run.marks || [];
      if (marks.includes('ITALIC')) {
        node = <em key={`em-${i}`}>{node}</em>;
      }
      if (marks.includes('BOLD')) {
        node = <strong key={`b-${i}`}>{node}</strong>;
      }
      return <span key={`run-${i}`}>{node}</span>;
    });
  }
  return renderTextWithBreaks(fallbackText || '', 'fb');
}

function blockWrapperProps(block: PreviewBlock, highlighted?: boolean) {
  const id = block.id;
  const classes = ['flow-block'];
  if (highlighted) {
    classes.push('flow-block-highlight');
  }
  return {
    id: id ? `block-${id}` : undefined,
    'data-block-id': id,
    className: classes.join(' '),
  } as const;
}

function BlockView({ block, highlighted }: { block: PreviewBlock; highlighted?: boolean }) {
  const type: PreviewBlockType = block.type;
  const wrap = blockWrapperProps(block, highlighted);

  if (type === 'SECTION') {
    return (
      <div {...wrap} className={`${wrap.className} flow-section`}>
        <Typography.Title level={4} className="flow-section-title">
          {block.text || '分区'}
        </Typography.Title>
      </div>
    );
  }

  if (type === 'HEADING') {
    const Tag = headingTag(block.level);
    return (
      <Tag {...wrap} className={`${wrap.className} flow-heading flow-heading-${Tag}`}>
        {renderRuns(block.runs, block.text)}
      </Tag>
    );
  }

  if (type === 'LIST_ITEM') {
    return (
      <li {...wrap} className={`${wrap.className} flow-list-item`}>
        {renderRuns(block.runs, block.text)}
      </li>
    );
  }

  if (type === 'CODE') {
    return (
      <pre {...wrap} className={`${wrap.className} flow-code`}>
        {block.text}
      </pre>
    );
  }

  if (type === 'IMAGE') {
    const src = block.imageDataUrl;
    if (!src) {
      return (
        <Typography.Paragraph type="secondary" {...wrap} className={`${wrap.className} flow-para`}>
          {block.text || '（图片缺失）'}
        </Typography.Paragraph>
      );
    }
    return (
      <figure {...wrap} className={`${wrap.className} flow-image-wrap`}>
        <img className="flow-image" src={src} alt={block.text || '文档图片'} loading="lazy" />
        {block.text && block.text !== '[图片]' ? (
          <Typography.Paragraph className="flow-image-caption">{block.text}</Typography.Paragraph>
        ) : null}
      </figure>
    );
  }

  if (type === 'IMAGE_CAPTION') {
    return (
      <Typography.Paragraph type="secondary" {...wrap} className={`${wrap.className} flow-caption`}>
        {renderRuns(block.runs, block.text)}
      </Typography.Paragraph>
    );
  }

  if (type === 'TABLE' || type === 'SHEET') {
    const rows = block.table?.rows || [];
    if (!rows.length) {
      return (
        <Typography.Paragraph type="secondary" {...wrap} className={`${wrap.className} flow-para`}>
          {block.text || '（空表）'}
        </Typography.Paragraph>
      );
    }
    const { columns, data } = tableParts(rows);
    return (
      <div {...wrap} className={`${wrap.className} flow-table-wrap`}>
        <Table
          size="small"
          bordered
          pagination={false}
          scroll={{ x: true }}
          columns={columns}
          dataSource={data}
        />
      </div>
    );
  }

  return (
    <Typography.Paragraph {...wrap} className={`${wrap.className} flow-para`}>
      {renderRuns(block.runs, block.text)}
    </Typography.Paragraph>
  );
}

export default function FlowStructureReader({
  blocks,
  highlightBlockIds,
}: {
  blocks?: PreviewBlock[];
  highlightBlockIds?: string[];
}) {
  if (!blocks || blocks.length === 0) {
    return <Empty description="无可展示的结构化内容" />;
  }

  const highlight = new Set((highlightBlockIds || []).filter(Boolean));
  const nodes: ReactNode[] = [];
  let listBuffer: PreviewBlock[] = [];

  const flushList = () => {
    if (!listBuffer.length) return;
    nodes.push(
      <ul key={`list-${nodes.length}`} className="flow-list">
        {listBuffer.map((b, i) => (
          <BlockView
            key={b.id || `li-${i}`}
            block={b}
            highlighted={!!b.id && highlight.has(b.id)}
          />
        ))}
      </ul>,
    );
    listBuffer = [];
  };

  blocks.forEach((block, index) => {
    if (block.type === 'LIST_ITEM') {
      listBuffer.push(block);
      return;
    }
    flushList();
    nodes.push(
      <BlockView
        key={block.id || `b-${index}`}
        block={block}
        highlighted={!!block.id && highlight.has(block.id)}
      />,
    );
  });
  flushList();

  return <article className="flow-reader">{nodes}</article>;
}

export { scrollToPreviewBlock } from './scrollToPreviewBlock';
