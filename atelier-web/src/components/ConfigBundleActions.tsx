import { useState } from 'react';
import {
  Button,
  Checkbox,
  Input,
  Modal,
  Space,
  Typography,
  Upload,
  message,
} from 'antd';
import { DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { configApi } from '../api/config';
import type { AtelierConfigBundle, ConfigImportOptions } from '../api/types';

const DEFAULT_SCOPE_OPTIONS: ConfigImportOptions = {
  importDatasources: true,
  importMetadata: true,
  importDimensions: true,
  importMetrics: true,
  importWarningRules: true,
  importSemanticLlm: true,
};

const SCOPE_ITEMS: Array<{ key: keyof ConfigImportOptions; label: string }> = [
  { key: 'importDatasources', label: '数据源' },
  { key: 'importMetadata', label: '元数据表与字段' },
  { key: 'importDimensions', label: '维度与维度值' },
  { key: 'importMetrics', label: '指标定义' },
  { key: 'importWarningRules', label: '预警规则' },
  { key: 'importSemanticLlm', label: '语义检测设置' },
];

function downloadJson(filename: string, data: unknown) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function summarizeBundle(bundle: AtelierConfigBundle): string {
  const parts = [
    `数据源 ${bundle.datasources?.length ?? 0} 个`,
    `元数据表 ${bundle.metadataTables?.length ?? 0} 个`,
    `维度 ${bundle.dimensions?.length ?? 0} 个`,
    `指标 ${bundle.metrics?.length ?? 0} 个`,
    `预警规则 ${bundle.warningRules?.length ?? 0} 条`,
    `LLM 配置 ${bundle.semanticLlmProfiles?.profiles?.length ?? 0} 套`,
  ];
  return parts.join(' · ');
}

function ScopeCheckboxes({
  value,
  onChange,
}: {
  value: ConfigImportOptions;
  onChange: (next: ConfigImportOptions) => void;
}) {
  return (
    <Space direction="vertical" style={{ marginTop: 8 }}>
      {SCOPE_ITEMS.map(({ key, label }) => (
        <Checkbox
          key={key}
          checked={value[key] !== false}
          onChange={(e) => onChange({ ...value, [key]: e.target.checked })}
        >
          {label}
        </Checkbox>
      ))}
    </Space>
  );
}

export default function ConfigBundleActions() {
  const [exportOpen, setExportOpen] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [includeSecrets, setIncludeSecrets] = useState(true);
  const [exportOptions, setExportOptions] = useState<ConfigImportOptions>(DEFAULT_SCOPE_OPTIONS);
  const [importOpen, setImportOpen] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importOptions, setImportOptions] = useState<ConfigImportOptions>(DEFAULT_SCOPE_OPTIONS);
  const [importText, setImportText] = useState('');
  const [parsedBundle, setParsedBundle] = useState<AtelierConfigBundle | null>(null);

  const handleExport = async () => {
    setExporting(true);
    try {
      const bundle = await configApi.exportBundle({
        includeSecrets,
        options: exportOptions,
      });
      const stamp = new Date().toISOString().slice(0, 10);
      downloadJson(`atelier-config-${stamp}.json`, bundle);
      message.success('配置已导出');
      setExportOpen(false);
    } finally {
      setExporting(false);
    }
  };

  const openExport = () => {
    setExportOptions({ ...DEFAULT_SCOPE_OPTIONS });
    setIncludeSecrets(true);
    setExportOpen(true);
  };

  const openImport = () => {
    setImportText('');
    setParsedBundle(null);
    setImportOptions({ ...DEFAULT_SCOPE_OPTIONS });
    setImportOpen(true);
  };

  const parseImportText = (text: string) => {
    setImportText(text);
    if (!text.trim()) {
      setParsedBundle(null);
      return;
    }
    try {
      setParsedBundle(JSON.parse(text) as AtelierConfigBundle);
    } catch {
      setParsedBundle(null);
    }
  };

  const uploadProps: UploadProps = {
    accept: '.json,application/json',
    showUploadList: false,
    beforeUpload: (file) => {
      const reader = new FileReader();
      reader.onload = () => parseImportText(String(reader.result || ''));
      reader.readAsText(file);
      return false;
    },
  };

  const handleImport = async () => {
    if (!parsedBundle) {
      message.warning('请先选择或粘贴有效的 JSON 配置');
      return;
    }
    setImporting(true);
    try {
      const result = await configApi.importBundle(parsedBundle, importOptions);
      const parts = Object.entries(result.imported || {}).map(([k, v]) => `${k}: ${v}`);
      message.success(result.message || `导入完成（${parts.join(', ')}）`);
      setImportOpen(false);
      window.location.reload();
    } finally {
      setImporting(false);
    }
  };

  return (
    <>
      <Space size="small">
        <Button size="small" icon={<DownloadOutlined />} onClick={openExport}>
          导出配置
        </Button>
        <Button size="small" icon={<UploadOutlined />} onClick={openImport}>
          导入配置
        </Button>
      </Space>

      <Modal
        title="导出配置"
        open={exportOpen}
        onCancel={() => setExportOpen(false)}
        onOk={handleExport}
        okText="下载 JSON"
        confirmLoading={exporting}
        width={560}
      >
        <Typography.Paragraph type="secondary">
          按所选范围导出配置 JSON，可用于迁移到其他环境。
        </Typography.Paragraph>
        <Typography.Text strong style={{ display: 'block' }}>
          导出范围
        </Typography.Text>
        <ScopeCheckboxes value={exportOptions} onChange={setExportOptions} />
        <div style={{ marginTop: 16 }}>
          <Checkbox checked={includeSecrets} onChange={(e) => setIncludeSecrets(e.target.checked)}>
            包含数据源密码与 LLM API Key（迁移到其他环境时建议勾选；分享配置文件时请取消）
          </Checkbox>
        </div>
      </Modal>

      <Modal
        title="导入配置"
        open={importOpen}
        onCancel={() => setImportOpen(false)}
        onOk={handleImport}
        okText="开始导入"
        confirmLoading={importing}
        width={640}
      >
        <Typography.Paragraph type="secondary">
          上传或粘贴 JSON 配置包。相同 id / code 的记录将被覆盖更新；数据源密码或 LLM API Key 为空时保留现有值。
        </Typography.Paragraph>
        <Upload {...uploadProps}>
          <Button icon={<UploadOutlined />} style={{ marginBottom: 12 }}>
            选择 JSON 文件
          </Button>
        </Upload>
        <Input.TextArea
          rows={8}
          placeholder='{"version":"1.0","datasources":[...]}'
          value={importText}
          onChange={(e) => parseImportText(e.target.value)}
          style={{ fontFamily: 'monospace', fontSize: 12 }}
        />
        {parsedBundle && (
          <Typography.Paragraph style={{ marginTop: 8 }} type="success">
            已识别：{summarizeBundle(parsedBundle)}
          </Typography.Paragraph>
        )}
        <Typography.Text strong style={{ display: 'block', marginTop: 16 }}>
          导入范围
        </Typography.Text>
        <ScopeCheckboxes value={importOptions} onChange={setImportOptions} />
      </Modal>
    </>
  );
}
