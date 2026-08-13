import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  Empty,
  List,
  Progress,
  Row,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import {
  DiffOutlined,
  FileTextOutlined,
  RobotOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import PageHeader from '../components/PageHeader';
import FlowStructureReader from '../components/document/FlowStructureReader';
import { scrollToPreviewBlocks } from '../components/document/scrollToPreviewBlock';
import {
  DOCUMENT_COMPARE_MAX_FILE_BYTES,
  documentCompareApi,
  formatFileSize,
  type CompareJob,
  type CompareOptions,
  type DiffOpType,
  type ParagraphOp,
  type StructureOp,
  type TextHunk,
} from '../api/documentCompare';

const ACCEPT =
  '.txt,.md,.markdown,.json,.csv,.xml,.yml,.yaml,.sql,.java,.ts,.tsx,.js,.py,.docx,.xlsx,.pptx,.pdf,.png,.jpg,.jpeg,.gif,.webp';

const OP_COLOR: Record<DiffOpType, string> = {
  ADDED: 'green',
  REMOVED: 'red',
  MODIFIED: 'orange',
  MOVED: 'blue',
  EQUAL: 'default',
};

function opLabel(type: DiffOpType): string {
  switch (type) {
    case 'ADDED':
      return '新增';
    case 'REMOVED':
      return '删除';
    case 'MODIFIED':
      return '修改';
    case 'MOVED':
      return '移动';
    default:
      return type;
  }
}

function TextHunkView({
  hunks,
  onLocate,
}: {
  hunks: TextHunk[];
  onLocate: (hunk: TextHunk) => void;
}) {
  if (!hunks.length) {
    return <Empty description="无文字差异" />;
  }
  return (
    <div style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 12 }}>
      {hunks.map((hunk, idx) => {
        const clickable =
          (hunk.blockIdsA && hunk.blockIdsA.length > 0) ||
          (hunk.blockIdsB && hunk.blockIdsB.length > 0);
        return (
          <div
            key={idx}
            role={clickable ? 'button' : undefined}
            tabIndex={clickable ? 0 : undefined}
            onClick={() => {
              if (clickable) {
                onLocate(hunk);
              } else {
                message.info('该差异暂无预览锚点，请确认已用最新后端重新对比');
              }
            }}
            onKeyDown={(e) => {
              if (clickable && (e.key === 'Enter' || e.key === ' ')) {
                e.preventDefault();
                onLocate(hunk);
              }
            }}
            style={{
              marginBottom: 12,
              border: '1px solid #f0f0f0',
              borderRadius: 6,
              cursor: 'pointer',
            }}
          >
            <div style={{ padding: '4px 8px', background: '#fafafa' }}>
              <Tag color={OP_COLOR[hunk.type]}>{opLabel(hunk.type)}</Tag>
              <Typography.Text type="secondary">
                A@{hunk.oldStart} / B@{hunk.newStart}
                {clickable ? ' · 点击在上方预览中高亮' : ' · 无定位锚点'}
              </Typography.Text>
            </div>
            <Row gutter={0}>
              <Col span={12} style={{ borderRight: '1px solid #f0f0f0', background: '#fff5f5' }}>
                {(hunk.oldLines || []).map((line, i) => (
                  <div key={i} style={{ padding: '2px 8px', whiteSpace: 'pre-wrap' }}>
                    - {line || ' '}
                  </div>
                ))}
              </Col>
              <Col span={12} style={{ background: '#f6ffed' }}>
                {(hunk.newLines || []).map((line, i) => (
                  <div key={i} style={{ padding: '2px 8px', whiteSpace: 'pre-wrap' }}>
                    + {line || ' '}
                  </div>
                ))}
              </Col>
            </Row>
          </div>
        );
      })}
    </div>
  );
}

function UploadPane({
  title,
  fileList,
  onChange,
}: {
  title: string;
  fileList: UploadFile[];
  onChange: (list: UploadFile[]) => void;
}) {
  return (
    <Card size="small" title={title} style={{ marginBottom: 16 }}>
      <Upload.Dragger
        accept={ACCEPT}
        maxCount={1}
        fileList={fileList}
        beforeUpload={() => false}
        onChange={({ fileList: next }) => onChange(next.slice(-1))}
      >
        <p className="ant-upload-drag-icon">
          <FileTextOutlined />
        </p>
        <p className="ant-upload-text">点击或拖拽上传</p>
      </Upload.Dragger>
    </Card>
  );
}

export default function DocComparePage() {
  const [fileA, setFileA] = useState<UploadFile[]>([]);
  const [fileB, setFileB] = useState<UploadFile[]>([]);
  const [ignoreWhitespace, setIgnoreWhitespace] = useState(true);
  const [excelKeyColumn, setExcelKeyColumn] = useState(false);
  const [enableLlm, setEnableLlm] = useState(true);
  const [enableLlmStyle, setEnableLlmStyle] = useState(true);
  const [enableLlmRefine, setEnableLlmRefine] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [job, setJob] = useState<CompareJob | null>(null);
  const [highlightA, setHighlightA] = useState<string[]>([]);
  const [highlightB, setHighlightB] = useState<string[]>([]);
  const [showUploadAgain, setShowUploadAgain] = useState(false);
  const pollRef = useRef<number | null>(null);
  const previewARef = useRef<HTMLDivElement | null>(null);
  const previewBRef = useRef<HTMLDivElement | null>(null);

  const options: CompareOptions = useMemo(
    () => ({ ignoreWhitespace, excelKeyColumn, enableLlm, enableLlmStyle, enableLlmRefine }),
    [ignoreWhitespace, excelKeyColumn, enableLlm, enableLlmStyle, enableLlmRefine],
  );

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
        const next = await documentCompareApi.getJob(id);
        setJob(next);
        if (next.status === 'SUCCEEDED' || next.status === 'FAILED') {
          stopPoll();
          setSubmitting(false);
          if (next.status === 'FAILED') {
            message.error(next.error || '对比失败');
          }
        }
      } catch {
        stopPoll();
        setSubmitting(false);
      }
    }, 1200);
  };

  const onCompare = async () => {
    const a = fileA[0]?.originFileObj;
    const b = fileB[0]?.originFileObj;
    if (!a || !b) {
      message.warning('请先上传文件 A 与文件 B');
      return;
    }
    const maxLabel = formatFileSize(DOCUMENT_COMPARE_MAX_FILE_BYTES);
    if (a.size > DOCUMENT_COMPARE_MAX_FILE_BYTES) {
      message.error(`文件 A 过大（${formatFileSize(a.size)}），单文件上限 ${maxLabel}`);
      return;
    }
    if (b.size > DOCUMENT_COMPARE_MAX_FILE_BYTES) {
      message.error(`文件 B 过大（${formatFileSize(b.size)}），单文件上限 ${maxLabel}`);
      return;
    }
    setSubmitting(true);
    setJob(null);
    setHighlightA([]);
    setHighlightB([]);
    setShowUploadAgain(false);
    try {
      const created = await documentCompareApi.createJob(a, b, options);
      setJob(created);
      if (created.status === 'SUCCEEDED' || created.status === 'FAILED') {
        setSubmitting(false);
      } else {
        startPoll(created.id);
      }
    } catch {
      setSubmitting(false);
    }
  };

  const locate = (blockIdsA?: string[], blockIdsB?: string[]) => {
    const a = blockIdsA || [];
    const b = blockIdsB || [];
    if (!a.length && !b.length) {
      message.info('该差异暂无预览锚点');
      return;
    }
    setHighlightA(a);
    setHighlightB(b);
    // 先滚到预览区，再闪块
    previewARef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    window.setTimeout(() => {
      scrollToPreviewBlocks(a, b, previewARef.current, previewBRef.current);
    }, 80);
  };

  const result = job?.result;
  const stats = result?.stats;
  const hasPreview =
    !!result &&
    ((result.previewA?.blocks && result.previewA.blocks.length > 0) ||
      (result.previewB?.blocks && result.previewB.blocks.length > 0));
  const showUpload = !result || showUploadAgain || (!hasPreview && job?.status === 'SUCCEEDED');

  return (
    <div>
      <PageHeader
        title="文档对比"
        description="对比完成后，上方上传区变为 A/B 结构预览（与文档预览相同）；点击下方文字/段落差异即可在预览中高亮定位。"
      />

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={`支持 txt/md/json、Word/Excel/PPT、PDF、图片；单文件 ≤ ${formatFileSize(DOCUMENT_COMPARE_MAX_FILE_BYTES)}；开启 LLM 样式/闭环时进度会出现 previewing-a/b；任务约 1 小时过期。`}
      />

      {showUpload && (
        <Row gutter={16}>
          <Col xs={24} md={12}>
            <UploadPane title="文件 A（旧）" fileList={fileA} onChange={setFileA} />
          </Col>
          <Col xs={24} md={12}>
            <UploadPane title="文件 B（新）" fileList={fileB} onChange={setFileB} />
          </Col>
        </Row>
      )}

      {hasPreview && (
        <>
          <Space wrap style={{ marginBottom: 8 }}>
            <Typography.Text type="secondary">
              预览区已替换原上传位 · 与「文档预览」同一套结构阅读
            </Typography.Text>
            <Button size="small" type="link" onClick={() => setShowUploadAgain((v) => !v)}>
              {showUploadAgain ? '收起上传' : '更换文件'}
            </Button>
          </Space>
          <Row gutter={16} style={{ marginBottom: 8 }}>
            <Col xs={24} lg={12}>
              <Card
                size="small"
                title={
                  <Space>
                    <span>预览 A</span>
                    <Typography.Text type="secondary" style={{ fontWeight: 400 }}>
                      {result?.fileNameA || result?.previewA?.fileName || '旧'}
                    </Typography.Text>
                    {result?.previewA?.llmStyleUsed && <Tag>LLM 样式</Tag>}
                  </Space>
                }
                styles={{ body: { maxHeight: 480, overflow: 'auto', paddingTop: 8 } }}
              >
                <div ref={previewARef}>
                  <FlowStructureReader
                    blocks={result?.previewA?.blocks}
                    highlightBlockIds={highlightA}
                  />
                </div>
              </Card>
            </Col>
            <Col xs={24} lg={12}>
              <Card
                size="small"
                title={
                  <Space>
                    <span>预览 B</span>
                    <Typography.Text type="secondary" style={{ fontWeight: 400 }}>
                      {result?.fileNameB || result?.previewB?.fileName || '新'}
                    </Typography.Text>
                    {result?.previewB?.llmStyleUsed && <Tag>LLM 样式</Tag>}
                  </Space>
                }
                styles={{ body: { maxHeight: 480, overflow: 'auto', paddingTop: 8 } }}
              >
                <div ref={previewBRef}>
                  <FlowStructureReader
                    blocks={result?.previewB?.blocks}
                    highlightBlockIds={highlightB}
                  />
                </div>
              </Card>
            </Col>
          </Row>
        </>
      )}

      {result && !hasPreview && job?.status === 'SUCCEEDED' && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="本次结果没有预览内容"
          description="多半是后端尚未加载「对比内嵌预览」代码，或对比完成于升级前。请重启后端后重新点「开始对比」。进度中应出现 previewing-a / previewing-b。"
        />
      )}

      <Space wrap style={{ marginBottom: 16, marginTop: 8 }}>
        <Checkbox checked={ignoreWhitespace} onChange={(e) => setIgnoreWhitespace(e.target.checked)}>
          忽略空白差异
        </Checkbox>
        <Checkbox checked={excelKeyColumn} onChange={(e) => setExcelKeyColumn(e.target.checked)}>
          Excel 首列作 key 对齐
        </Checkbox>
        <Space>
          <Typography.Text>AI 解读</Typography.Text>
          <Switch checked={enableLlm} onChange={setEnableLlm} />
        </Space>
        <Space>
          <Typography.Text>预览 LLM 样式</Typography.Text>
          <Switch checked={enableLlmStyle} onChange={setEnableLlmStyle} />
        </Space>
        <Space>
          <Typography.Text>预览保真闭环</Typography.Text>
          <Switch checked={enableLlmRefine} onChange={setEnableLlmRefine} />
        </Space>
        <Button
          type="primary"
          icon={<SwapOutlined />}
          loading={submitting}
          onClick={() => void onCompare()}
        >
          开始对比
        </Button>
      </Space>

      {job && job.status !== 'SUCCEEDED' && job.status !== 'FAILED' && (
        <Card size="small" style={{ marginBottom: 16 }}>
          <Typography.Text type="secondary">
            任务 {job.id} · {job.progress || job.status}
          </Typography.Text>
          <Progress percent={job.progressPercent || 0} status="active" />
        </Card>
      )}

      {job?.status === 'FAILED' && (
        <Alert type="error" showIcon style={{ marginBottom: 16 }} message={job.error || '对比失败'} />
      )}

      {result && (
        <>
          <Space wrap style={{ marginBottom: 12 }}>
            {stats && (
              <>
                <Tag color="green">新增 {stats.added}</Tag>
                <Tag color="red">删除 {stats.removed}</Tag>
                <Tag color="orange">修改 {stats.modified}</Tag>
                <Tag color="blue">移动 {stats.moved}</Tag>
              </>
            )}
            {result.quality?.ocrUsed && <Tag color="purple">已使用 OCR</Tag>}
          </Space>

          {!!result.quality?.warnings?.length && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
              message="质量提示"
              description={
                <ul style={{ margin: 0, paddingLeft: 18 }}>
                  {result.quality.warnings.map((w) => (
                    <li key={w}>{w}</li>
                  ))}
                </ul>
              }
            />
          )}

          <Card
            size="small"
            title={
              <Space>
                <DiffOutlined />
                差异
                <Typography.Text type="secondary" style={{ fontWeight: 400 }}>
                  点击条目 → 上方预览高亮对应块
                </Typography.Text>
              </Space>
            }
            style={{ marginBottom: 16 }}
          >
            <Tabs
              items={[
                {
                  key: 'text',
                  label: (
                    <span>
                      <DiffOutlined /> 文字差异
                    </span>
                  ),
                  children: (
                    <TextHunkView
                      hunks={result.textHunks || []}
                      onLocate={(h) => locate(h.blockIdsA, h.blockIdsB)}
                    />
                  ),
                },
                {
                  key: 'paragraph',
                  label: `段落差异 (${result.paragraphOps?.length || 0})`,
                  children: (
                    <Table<ParagraphOp>
                      size="small"
                      rowKey={(_, i) => String(i)}
                      pagination={{ pageSize: 20 }}
                      dataSource={result.paragraphOps || []}
                      onRow={(op) => ({
                        onClick: () => locate(op.blockIdsA, op.blockIdsB),
                        style: { cursor: 'pointer' },
                      })}
                      columns={[
                        {
                          title: '类型',
                          dataIndex: 'type',
                          width: 90,
                          render: (t: DiffOpType) => <Tag color={OP_COLOR[t]}>{opLabel(t)}</Tag>,
                        },
                        { title: '块类型', dataIndex: 'blockType', width: 110 },
                        {
                          title: '旧文本',
                          dataIndex: 'oldText',
                          ellipsis: true,
                          render: (v?: string) => v || '—',
                        },
                        {
                          title: '新文本',
                          dataIndex: 'newText',
                          ellipsis: true,
                          render: (v?: string) => v || '—',
                        },
                      ]}
                    />
                  ),
                },
                {
                  key: 'structure',
                  label: `结构差异 (${result.structureOps?.length || 0})`,
                  children: (
                    <Table<StructureOp>
                      size="small"
                      rowKey={(_, i) => String(i)}
                      pagination={{ pageSize: 20 }}
                      dataSource={result.structureOps || []}
                      onRow={(op) => ({
                        onClick: () => locate(op.blockIdsA, op.blockIdsB),
                        style: { cursor: 'pointer' },
                      })}
                      columns={[
                        {
                          title: '类型',
                          dataIndex: 'type',
                          width: 90,
                          render: (t: DiffOpType) => <Tag color={OP_COLOR[t]}>{opLabel(t)}</Tag>,
                        },
                        { title: '路径', dataIndex: 'path', width: 220, ellipsis: true },
                        { title: '块类型', dataIndex: 'blockType', width: 120 },
                        {
                          title: '旧',
                          dataIndex: 'oldText',
                          ellipsis: true,
                          render: (v?: string) => v || '—',
                        },
                        {
                          title: '新',
                          dataIndex: 'newText',
                          ellipsis: true,
                          render: (v?: string) => v || '—',
                        },
                      ]}
                    />
                  ),
                },
              ]}
            />
          </Card>

          <Card
            size="small"
            title={
              <Space>
                <RobotOutlined />
                AI 解读
                <Tag>非逐字校对</Tag>
              </Space>
            }
            style={{ marginBottom: 16 }}
          >
            {!result.interpretation?.available ? (
              <Typography.Text type="secondary">
                {result.interpretation?.error || '未生成 AI 解读'}
              </Typography.Text>
            ) : (
              <>
                <Typography.Paragraph>{result.interpretation.summary}</Typography.Paragraph>
                {!!result.interpretation.impactPoints?.length && (
                  <>
                    <Typography.Text strong>可能影响点</Typography.Text>
                    <List
                      size="small"
                      dataSource={result.interpretation.impactPoints}
                      renderItem={(item) => <List.Item>{item}</List.Item>}
                    />
                  </>
                )}
                {!!result.interpretation.reviewChecklist?.length && (
                  <>
                    <Typography.Text strong>人工核对清单</Typography.Text>
                    <List
                      size="small"
                      dataSource={result.interpretation.reviewChecklist}
                      renderItem={(item) => <List.Item>{item}</List.Item>}
                    />
                  </>
                )}
              </>
            )}
          </Card>
        </>
      )}
    </div>
  );
}
