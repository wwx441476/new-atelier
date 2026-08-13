import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Progress,
  Space,
  Switch,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import { EyeOutlined, FileTextOutlined, StopOutlined } from '@ant-design/icons';
import PageHeader from '../components/PageHeader';
import FlowStructureReader from '../components/document/FlowStructureReader';
import {
  DOCUMENT_PREVIEW_MAX_FILE_BYTES,
  documentPreviewApi,
  formatFileSize,
  type PreviewJob,
} from '../api/documentPreview';

const ACCEPT =
  '.txt,.md,.markdown,.json,.csv,.xml,.yml,.yaml,.sql,.java,.ts,.tsx,.js,.py,.docx,.xlsx,.pptx,.pdf,.png,.jpg,.jpeg,.gif,.webp';

export default function DocPreviewPage() {
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [enableLlmStyle, setEnableLlmStyle] = useState(true);
  const [enableLlmRefine, setEnableLlmRefine] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [job, setJob] = useState<PreviewJob | null>(null);
  const pollRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (pollRef.current != null) {
        window.clearInterval(pollRef.current);
      }
    };
  }, []);

  const stopPoll = () => {
    if (pollRef.current != null) {
      window.clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  const startPoll = (id: string) => {
    stopPoll();
    pollRef.current = window.setInterval(async () => {
      try {
        const next = await documentPreviewApi.getJob(id);
        setJob(next);
        if (next.status === 'SUCCEEDED' || next.status === 'FAILED' || next.status === 'CANCELLED') {
          stopPoll();
          setSubmitting(false);
          if (next.status === 'FAILED') {
            message.error(next.error || '预览失败');
          }
        }
      } catch {
        stopPoll();
        setSubmitting(false);
      }
    }, 1200);
  };

  const onPreview = async () => {
    const file = fileList[0]?.originFileObj;
    if (!file) {
      message.warning('请先上传文档');
      return;
    }
    const maxLabel = formatFileSize(DOCUMENT_PREVIEW_MAX_FILE_BYTES);
    if (file.size > DOCUMENT_PREVIEW_MAX_FILE_BYTES) {
      message.error(`文件过大（${formatFileSize(file.size)}），单文件上限 ${maxLabel}`);
      return;
    }
    setSubmitting(true);
    setJob(null);
    try {
      const created = await documentPreviewApi.createJob(file, { enableLlmStyle, enableLlmRefine });
      setJob(created);
      if (created.status === 'SUCCEEDED' || created.status === 'FAILED' || created.status === 'CANCELLED') {
        setSubmitting(false);
        if (created.status === 'FAILED') {
          message.error(created.error || '预览失败');
        }
      } else {
        startPoll(created.id);
      }
    } catch {
      setSubmitting(false);
    }
  };

  const onStop = async () => {
    if (!job?.id) {
      setSubmitting(false);
      return;
    }
    stopPoll();
    try {
      const stopped = await documentPreviewApi.cancelJob(job.id);
      setJob(stopped);
      message.info('已停止预览');
    } catch {
      message.warning('停止请求已发送');
      setJob((prev) =>
        prev
          ? {
              ...prev,
              status: 'CANCELLED',
              progress: 'cancelled',
              error: '用户已停止预览',
            }
          : prev,
      );
    } finally {
      setSubmitting(false);
    }
  };

  const result = job?.result;
  const warnings = result?.warnings || [];
  const running = submitting || job?.status === 'PENDING' || job?.status === 'RUNNING';

  return (
    <div>
      <PageHeader
        title="文档预览"
        description="流式规范阅读视图；PDF / Word / Markdown 可走 LLM 样式 + 保真闭环（对比原文自动修补结构，非 WYSIWYG）。"
      />

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={`支持 txt/md/json、Word/Excel/PPT、PDF、图片；单文件 ≤ ${formatFileSize(DOCUMENT_PREVIEW_MAX_FILE_BYTES)}；开启 LLM 样式/保真闭环时更慢；结果约 1 小时后过期。`}
      />

      <Card size="small" style={{ marginBottom: 16 }}>
        <Upload.Dragger
          accept={ACCEPT}
          maxCount={1}
          fileList={fileList}
          beforeUpload={() => false}
          onChange={({ fileList: next }) => setFileList(next.slice(-1))}
        >
          <p className="ant-upload-drag-icon">
            <FileTextOutlined />
          </p>
          <p className="ant-upload-text">点击或拖拽上传文档</p>
        </Upload.Dragger>
        <Space wrap style={{ marginTop: 16 }}>
          <Space>
            <Typography.Text>LLM 样式增强</Typography.Text>
            <Switch checked={enableLlmStyle} onChange={setEnableLlmStyle} />
          </Space>
          <Space>
            <Typography.Text>保真闭环</Typography.Text>
            <Switch checked={enableLlmRefine} onChange={setEnableLlmRefine} />
            <Typography.Text type="secondary">（对比原文自动修补，支持 PDF/Word/MD）</Typography.Text>
          </Space>
          <Button
            type="primary"
            icon={<EyeOutlined />}
            loading={submitting}
            disabled={running && !!job}
            onClick={() => void onPreview()}
          >
            开始预览
          </Button>
          {running && (
            <Button danger icon={<StopOutlined />} onClick={() => void onStop()}>
              停止预览
            </Button>
          )}
        </Space>
      </Card>

      {job && job.status !== 'SUCCEEDED' && job.status !== 'FAILED' && job.status !== 'CANCELLED' && (
        <Card size="small" style={{ marginBottom: 16 }}>
          <Space style={{ width: '100%', justifyContent: 'space-between' }} wrap>
            <Typography.Text type="secondary">
              任务 {job.id} · {job.progress || job.status}
            </Typography.Text>
            <Button size="small" danger icon={<StopOutlined />} onClick={() => void onStop()}>
              停止
            </Button>
          </Space>
          <Progress percent={job.progressPercent || 0} status="active" />
        </Card>
      )}

      {job?.status === 'CANCELLED' && (
        <Alert type="info" showIcon style={{ marginBottom: 16 }} message={job.error || '已停止预览'} />
      )}

      {job?.status === 'FAILED' && (
        <Alert type="error" showIcon style={{ marginBottom: 16 }} message={job.error || '预览失败'} />
      )}

      {job?.status === 'SUCCEEDED' && result && (
        <Card
          size="small"
          title={
            <Space wrap>
              <Typography.Text strong>{result.fileName || job.fileName}</Typography.Text>
              {result.sourceType && <Tag>{result.sourceType}</Tag>}
              <Tag color="blue">flow</Tag>
              <Tag>canonical</Tag>
              {result.ocrUsed && <Tag color="orange">OCR</Tag>}
              {result.llmStyleUsed && <Tag color="purple">LLM 样式</Tag>}
            </Space>
          }
        >
          {warnings.length > 0 && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
              message="解析提示"
              description={
                <ul style={{ margin: 0, paddingLeft: 18 }}>
                  {warnings.map((w, i) => (
                    <li key={i}>{w}</li>
                  ))}
                </ul>
              }
            />
          )}
          <FlowStructureReader blocks={result.blocks} />
        </Card>
      )}
    </div>
  );
}
