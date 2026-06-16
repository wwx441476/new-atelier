import { Button, Input, Space, Typography } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';

interface ColumnLabelsEditorProps {
  value?: Record<string, string>;
  suggestedFields?: string[];
  onChange: (labels: Record<string, string>) => void;
}

export default function ColumnLabelsEditor({
  value,
  suggestedFields,
  onChange,
}: ColumnLabelsEditorProps) {
  const labels = value ?? {};
  const fieldKeys = Array.from(
    new Set([...Object.keys(labels), ...(suggestedFields ?? [])]),
  ).filter(Boolean);

  const updateLabel = (field: string, label: string) => {
    const next = { ...labels };
    if (label.trim()) {
      next[field] = label.trim();
    } else {
      delete next[field];
    }
    onChange(next);
  };

  const addField = () => {
    const field = `field_${Date.now().toString(36).slice(-4)}`;
    onChange({ ...labels, [field]: '' });
  };

  const removeField = (field: string) => {
    const next = { ...labels };
    delete next[field];
    onChange(next);
  };

  const renameField = (oldField: string, newField: string) => {
    const trimmed = newField.trim();
    if (!trimmed || trimmed === oldField) {
      return;
    }
    const next = { ...labels };
    const label = next[oldField];
    delete next[oldField];
    next[trimmed] = label ?? '';
    onChange(next);
  };

  return (
    <div>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        为列字段设置显示名，留空则使用原始字段名
      </Typography.Text>
      <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 8 }}>
        {fieldKeys.map((field) => (
          <Space key={field} align="start" style={{ display: 'flex' }}>
            <Input
              size="small"
              placeholder="字段名"
              value={field}
              onChange={(e) => renameField(field, e.target.value)}
              style={{ width: 100 }}
            />
            <Input
              size="small"
              placeholder="显示名，如唯一标识"
              value={labels[field] ?? ''}
              onChange={(e) => updateLabel(field, e.target.value)}
              style={{ flex: 1 }}
            />
            <Button
              size="small"
              type="text"
              danger
              icon={<DeleteOutlined />}
              onClick={() => removeField(field)}
            />
          </Space>
        ))}
        <Button size="small" icon={<PlusOutlined />} onClick={addField}>
          添加字段
        </Button>
      </div>
    </div>
  );
}
