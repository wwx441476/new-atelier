import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Input,
  Modal,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  CloseOutlined,
  PaperClipOutlined,
  RobotOutlined,
} from '@ant-design/icons';
import { dashboardApi } from '../../api/dashboard';
import { settingsApi } from '../../api/settings';
import {
  MAX_COPILOT_IMAGES,
  isAcceptedImageFile,
  readImageAsDataUrl,
} from '../../utils/copilotImageUtils';
import { readCopilotLlmProfileId } from '../../utils/copilotLlmProfile';

const EXAMPLE_PROMPTS = [
  '生成财务经营监控大屏：顶部标题，营收/成本/利润三个 KPI，部门柱状图和利润折线图，指标明细表，两个预警统计卡，底部 orders 订单表',
  '做一个简洁的销售概览大屏，包含 KPI 和部门对比柱状图，使用科技蓝主题',
  '参考截图布局，复刻类似的大屏结构与组件分区',
];

interface DashboardGenerateModalProps {
  open: boolean;
  onClose: () => void;
  onGenerated: (dashboardId: string) => void;
}

export default function DashboardGenerateModal({
  open,
  onClose,
  onGenerated,
}: DashboardGenerateModalProps) {
  const [prompt, setPrompt] = useState('');
  const [attachments, setAttachments] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [reply, setReply] = useState('');
  const [llmProfileId, setLlmProfileId] = useState<string>();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const loadLlmProfile = useCallback(async () => {
    try {
      const data = await settingsApi.getLlmProfiles();
      const stored = readCopilotLlmProfileId();
      const validStored = data.profiles.find((profile) => profile.id === stored)?.id;
      setLlmProfileId(validStored || data.activeProfileId || data.profiles[0]?.id);
    } catch {
      // 使用后端默认 LLM 配置
    }
  }, []);

  useEffect(() => {
    if (open) {
      void loadLlmProfile();
    } else {
      setReply('');
    }
  }, [open, loadLlmProfile]);

  const addImageFiles = async (files: FileList | File[]) => {
    const fileArray = Array.from(files).filter((file) => isAcceptedImageFile(file));
    if (fileArray.length === 0) {
      message.warning('仅支持 PNG、JPEG、GIF、WebP 图片');
      return;
    }
    const remaining = MAX_COPILOT_IMAGES - attachments.length;
    if (remaining <= 0) {
      message.warning(`最多附带 ${MAX_COPILOT_IMAGES} 张截图`);
      return;
    }
    const selected = fileArray.slice(0, remaining);
    try {
      const dataUrls = await Promise.all(selected.map((file) => readImageAsDataUrl(file)));
      setAttachments((prev) => [...prev, ...dataUrls].slice(0, MAX_COPILOT_IMAGES));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '读取图片失败');
    }
  };

  const handleGenerate = async () => {
    const text = prompt.trim();
    if (!text && attachments.length === 0) {
      message.warning('请输入描述或上传参考截图');
      return;
    }
    setLoading(true);
    setReply('');
    try {
      const response = await dashboardApi.generate({
        prompt: text || '请根据截图复刻类似的可视化大屏布局，绑定工作区已有指标与规则',
        images: attachments.length > 0 ? attachments : undefined,
        llmProfileId,
        autoSave: true,
      });
      setReply(response.reply);
      message.success(`大屏「${response.dashboard.name}」已生成`);
      onGenerated(response.dashboard.id ?? response.dashboard.code);
    } catch {
      /* handled by interceptor */
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    if (loading) {
      return;
    }
    setPrompt('');
    setAttachments([]);
    setReply('');
    onClose();
  };

  return (
    <Modal
      title={
        <Space>
          <RobotOutlined />
          AI 生成大屏
        </Space>
      }
      open={open}
      onCancel={handleClose}
      width={640}
      footer={[
        <Button key="cancel" onClick={handleClose} disabled={loading}>
          取消
        </Button>,
        <Button
          key="generate"
          type="primary"
          loading={loading}
          icon={<RobotOutlined />}
          onClick={() => void handleGenerate()}
        >
          生成并保存
        </Button>,
      ]}
      destroyOnClose
    >
      <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
        用一句话描述想要的大屏，或上传参考截图（如现有大屏、设计稿），助手将自动布局并绑定已有指标、规则与数据源。
      </Typography.Paragraph>

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="需先在「语义检测设置」中配置 LLM API Key"
      />

      <Input.TextArea
        rows={4}
        value={prompt}
        onChange={(e) => setPrompt(e.target.value)}
        placeholder="例如：生成财务监控大屏，包含营收/成本/利润 KPI、部门图表、预警统计和订单明细表"
        disabled={loading}
        onPaste={(event) => {
          const items = event.clipboardData?.items;
          if (!items) {
            return;
          }
          const imageFiles: File[] = [];
          for (const item of items) {
            if (item.kind === 'file' && item.type.startsWith('image/')) {
              const file = item.getAsFile();
              if (file) {
                imageFiles.push(file);
              }
            }
          }
          if (imageFiles.length === 0) {
            return;
          }
          event.preventDefault();
          void addImageFiles(imageFiles);
        }}
      />

      <div style={{ marginTop: 8, marginBottom: 12 }}>
        <Space wrap>
          {EXAMPLE_PROMPTS.map((item) => (
            <Tag
              key={item}
              style={{ cursor: loading ? 'not-allowed' : 'pointer' }}
              onClick={() => !loading && setPrompt(item)}
            >
              {item.length > 28 ? `${item.slice(0, 28)}…` : item}
            </Tag>
          ))}
        </Space>
      </div>

      <Space style={{ marginBottom: 12 }}>
        <Button
          icon={<PaperClipOutlined />}
          disabled={loading || attachments.length >= MAX_COPILOT_IMAGES}
          onClick={() => fileInputRef.current?.click()}
        >
          上传截图
        </Button>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          支持粘贴截图，最多 {MAX_COPILOT_IMAGES} 张
        </Typography.Text>
      </Space>

      <input
        ref={fileInputRef}
        type="file"
        accept="image/png,image/jpeg,image/gif,image/webp"
        multiple
        hidden
        onChange={(event) => {
          if (event.target.files && event.target.files.length > 0) {
            void addImageFiles(event.target.files);
            event.target.value = '';
          }
        }}
      />

      {attachments.length > 0 && (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 12 }}>
          {attachments.map((image, index) => (
            <div
              key={`img-${index}`}
              style={{
                position: 'relative',
                width: 96,
                height: 72,
                borderRadius: 6,
                overflow: 'hidden',
                border: '1px solid #f0f0f0',
              }}
            >
              <img
                src={image}
                alt={`参考截图 ${index + 1}`}
                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
              />
              <Button
                type="text"
                size="small"
                icon={<CloseOutlined />}
                disabled={loading}
                style={{
                  position: 'absolute',
                  top: 0,
                  right: 0,
                  color: '#fff',
                  background: 'rgba(0,0,0,0.45)',
                }}
                onClick={() =>
                  setAttachments((prev) => prev.filter((_, i) => i !== index))
                }
              />
            </div>
          ))}
        </div>
      )}

      {loading && (
        <div style={{ textAlign: 'center', padding: '24px 0' }}>
          <Spin tip="AI 正在设计大屏布局…" />
        </div>
      )}

      {reply && !loading && (
        <Alert type="success" message="生成说明" description={reply} showIcon />
      )}
    </Modal>
  );
}
