import { useMemo } from 'react';
import { Button, Typography, message } from 'antd';
import { CopyOutlined } from '@ant-design/icons';
import { formatSql } from '../utils/formatSql';

interface SqlPreviewBlockProps {
  sql: string;
  meta?: string;
  maxHeight?: number;
}

export default function SqlPreviewBlock({ sql, meta, maxHeight = 280 }: SqlPreviewBlockProps) {
  const formattedSql = useMemo(() => formatSql(sql), [sql]);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(formattedSql);
      message.success('SQL 已复制');
    } catch {
      message.error('复制失败，请手动选择复制');
    }
  };

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 12,
          marginTop: meta ? 8 : 0,
        }}
      >
        {meta ? (
          <Typography.Paragraph type="secondary" style={{ margin: 0, fontSize: 13, flex: 1 }}>
            {meta}
          </Typography.Paragraph>
        ) : (
          <span />
        )}
        <Button size="small" icon={<CopyOutlined />} onClick={handleCopy}>
          复制 SQL
        </Button>
      </div>
      <pre
        style={{
          background: '#f6f8fa',
          padding: 16,
          borderRadius: 6,
          overflow: 'auto',
          fontSize: 13,
          lineHeight: 1.6,
          marginTop: 8,
          marginBottom: 0,
          maxHeight,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }}
      >
        {formattedSql}
      </pre>
    </div>
  );
}
