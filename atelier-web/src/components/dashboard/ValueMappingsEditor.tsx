import { useState } from 'react';
import { Button, Input, Select, Space, Typography } from 'antd';
import { PlusOutlined, DeleteOutlined, ImportOutlined } from '@ant-design/icons';
import { dimensionApi } from '../../api/dimension';
import type { Dimension } from '../../api/types';
import type { FieldValueMappings } from './valueMappingUtils';
import { mergeFieldValueMappings } from './valueMappingUtils';

interface ValueMappingsEditorProps {
  valueMappings?: FieldValueMappings;
  onChange: (valueMappings: FieldValueMappings) => void;
  /** 默认选中的字段，如图表的 categoryField */
  activeField?: string;
  suggestedFields?: string[];
  dimensions?: Dimension[];
}

export default function ValueMappingsEditor({
  valueMappings,
  onChange,
  activeField,
  suggestedFields = [],
  dimensions = [],
}: ValueMappingsEditorProps) {
  const fieldOptions = Array.from(
    new Set([
      ...(activeField ? [activeField] : []),
      ...Object.keys(valueMappings ?? {}),
      ...suggestedFields,
    ]),
  ).filter(Boolean);

  const [field, setField] = useState(activeField ?? fieldOptions[0] ?? 'dept_code');
  const effectiveField = fieldOptions.includes(field) ? field : fieldOptions[0] ?? field;

  const entries = valueMappings?.[effectiveField] ?? {};
  const entryKeys = Object.keys(entries);

  const updateEntry = (code: string, label: string) => {
    const nextFieldMap = { ...entries };
    if (label.trim()) {
      nextFieldMap[code] = label.trim();
    } else {
      delete nextFieldMap[code];
    }
    onChange(mergeFieldValueMappings(valueMappings, effectiveField, nextFieldMap));
  };

  const addEntry = () => {
    const code = `code_${Date.now().toString(36).slice(-4)}`;
    onChange(mergeFieldValueMappings(valueMappings, effectiveField, { ...entries, [code]: '' }));
  };

  const removeEntry = (code: string) => {
    const nextFieldMap = { ...entries };
    delete nextFieldMap[code];
    onChange({ ...(valueMappings ?? {}), [effectiveField]: nextFieldMap });
  };

  const renameCode = (oldCode: string, newCode: string) => {
    const trimmed = newCode.trim();
    if (!trimmed || trimmed === oldCode) {
      return;
    }
    const nextFieldMap = { ...entries };
    nextFieldMap[trimmed] = nextFieldMap[oldCode] ?? '';
    delete nextFieldMap[oldCode];
    onChange({ ...(valueMappings ?? {}), [effectiveField]: nextFieldMap });
  };

  const importFromDimension = async (dimensionId: string) => {
    const values = await dimensionApi.listValues(dimensionId);
    const imported = Object.fromEntries(
      values.filter((v) => v.code).map((v) => [v.code!, v.name ?? v.code!]),
    );
    onChange(mergeFieldValueMappings(valueMappings, effectiveField, imported));
  };

  return (
    <div>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        编码值映射为展示名，如图表 X 轴、表格单元格
      </Typography.Text>
      <div style={{ marginTop: 8, marginBottom: 8 }}>
        <Select
          size="small"
          value={effectiveField}
          onChange={setField}
          options={fieldOptions.map((f) => ({ label: f, value: f }))}
          placeholder="选择字段"
          style={{ width: '100%' }}
        />
      </div>
      {dimensions.length > 0 && (
        <Space style={{ marginBottom: 8 }} wrap>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            从维度导入：
          </Typography.Text>
          {dimensions.map((dim) => (
            <Button
              key={dim.id}
              size="small"
              icon={<ImportOutlined />}
              onClick={() => dim.id && void importFromDimension(dim.id)}
            >
              {dim.name}
            </Button>
          ))}
        </Space>
      )}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {entryKeys.map((code) => (
          <Space key={code} align="start" style={{ display: 'flex' }}>
            <Input
              size="small"
              placeholder="编码"
              value={code}
              onChange={(e) => renameCode(code, e.target.value)}
              style={{ width: 72 }}
            />
            <Input
              size="small"
              placeholder="显示名，如销售部"
              value={entries[code] ?? ''}
              onChange={(e) => updateEntry(code, e.target.value)}
              style={{ flex: 1 }}
            />
            <Button
              size="small"
              type="text"
              danger
              icon={<DeleteOutlined />}
              onClick={() => removeEntry(code)}
            />
          </Space>
        ))}
        <Button size="small" icon={<PlusOutlined />} onClick={addEntry}>
          添加映射
        </Button>
      </div>
    </div>
  );
}
