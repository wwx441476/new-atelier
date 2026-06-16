import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AudioOutlined,
  CloseOutlined,
  DownOutlined,
  PaperClipOutlined,
  RedoOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { Button, message } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { copilotApi } from '../../api/copilot';
import { settingsApi } from '../../api/settings';
import type {
  CopilotActionResult,
  CopilotActivePlan,
  CopilotPlaybook,
  CopilotSqlQueryResult,
  CopilotWarningHitResult as CopilotWarningHitResultData,
  CopilotWarningJobResult,
  SemanticLlmProfileResponse,
  SqlExecuteResult,
} from '../../api/types';
import { formatCopilotReply, copilotToolLabel } from '../../utils/copilotReplyUtils';
import { ONBOARDING_STEPS } from '../../guide/steps';
import CopilotPlanPanel from './CopilotPlanPanel';
import CopilotMessageContent from './CopilotMessageContent';
import CopilotQueryResult from './CopilotQueryResult';
import CopilotWarningHitResultCard from './CopilotWarningHitResult';
import CopilotWarningJobCard from './CopilotWarningJobCard';
import CopilotWriteResult from './CopilotWriteResult';
import { useCopilot } from './CopilotContext';
import {
  MAX_COPILOT_IMAGES,
  isAcceptedImageFile,
  readImageAsDataUrl,
} from '../../utils/copilotImageUtils';
import { readCopilotLlmProfileId, writeCopilotLlmProfileId } from '../../utils/copilotLlmProfile';
import { getVoiceInputShortcutLabel } from '../../utils/copilotVoiceInput';
import { SEMANTIC_LLM_UPDATED_EVENT } from '../../utils/semanticLlmEvents';
import CopilotVoiceWaveform from './CopilotVoiceWaveform';
import { useCopilotVoiceInput } from './useCopilotVoiceInput';
import './CopilotDrawer.css';

const SUGGESTIONS = [
  '帮我创建一个 H2 演示数据源',
  '从 PUBLIC schema 同步 orders 表到元数据',
  '创建一个部门 LIST 维度，并添加两个维度值',
  '基于 orders 表创建按部门汇总的营收指标',
  '创建利润低于 500 的预警规则',
  '跑一下 low_profit 预警，看看命中哪些数据',
];

const DASHBOARD_SUGGESTIONS = [
  '生成财务经营监控大屏，包含 KPI、部门图表、预警和订单表',
  '帮我创建一个简洁的销售概览大屏',
  '根据截图复刻类似的大屏布局',
];

interface DisplayMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  images?: string[];
  actions?: CopilotActionResult[];
  plan?: CopilotActivePlan;
  matchedPlaybooks?: CopilotPlaybook[];
  suggestSavePlaybook?: boolean;
  error?: boolean;
}

function toApiMessages(history: DisplayMessage[]) {
  return history
    .filter((item) => item.id !== 'welcome')
    .map((item) => ({
      role: item.role,
      content: item.content,
      images: item.images && item.images.length > 0 ? item.images : undefined,
    }));
}

function isFailedTurn(messages: DisplayMessage[], userIndex: number) {
  const next = messages[userIndex + 1];
  return next?.role === 'assistant' && next.error === true;
}

function isSqlQueryResult(result: unknown): result is CopilotSqlQueryResult {
  if (!result || typeof result !== 'object') {
    return false;
  }
  const value = result as Record<string, unknown>;
  return typeof value.datasourceId === 'string' && Array.isArray(value.rows);
}

function isSqlExecuteResult(result: unknown): result is SqlExecuteResult {
  if (!result || typeof result !== 'object') {
    return false;
  }
  const value = result as Record<string, unknown>;
  return typeof value.statementType === 'string' && typeof value.message === 'string';
}

function isWriteAction(tool: string) {
  return tool === 'execute_write_sql' || tool === 'create_physical_table';
}

function isWarningJobResult(result: unknown): result is CopilotWarningJobResult {
  if (!result || typeof result !== 'object') {
    return false;
  }
  const value = result as Record<string, unknown>;
  return typeof value.jobId === 'string' && !Array.isArray(value.matchedRows);
}

function isDashboardScreen(result: unknown): result is { id?: string; code: string; name: string } {
  if (!result || typeof result !== 'object') {
    return false;
  }
  const value = result as Record<string, unknown>;
  return typeof value.code === 'string' && typeof value.name === 'string';
}

function isWarningHitResult(result: unknown): result is CopilotWarningHitResultData {
  if (!result || typeof result !== 'object') {
    return false;
  }
  const value = result as Record<string, unknown>;
  return typeof value.jobId === 'string' && Array.isArray(value.matchedRows);
}

function createId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function resolvePageLabel(pathname: string) {
  return ONBOARDING_STEPS.find((step) => step.path === pathname)?.menuLabel || '工作台';
}

export default function CopilotDrawer() {
  const location = useLocation();
  const navigate = useNavigate();
  const { open, setOpen } = useCopilot();
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [dryRun, setDryRun] = useState(false);
  const [messages, setMessages] = useState<DisplayMessage[]>([
    {
      id: 'welcome',
      role: 'assistant',
      content:
        '你好，我是 **Atelier Copilot**。你可以用自然语言创建数据源、元数据、维度、指标和预警规则。\n\n先在右上角配置好 LLM，然后直接告诉我你想搭建什么。也可以粘贴或上传截图来说明界面问题。',
    },
  ]);
  const [attachments, setAttachments] = useState<string[]>([]);
  const [llmProfiles, setLlmProfiles] = useState<SemanticLlmProfileResponse[]>([]);
  const [selectedLlmProfileId, setSelectedLlmProfileId] = useState<string>();
  const [activePlan, setActivePlan] = useState<CopilotActivePlan | undefined>();
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const imeComposingRef = useRef(false);
  const pageLabel = useMemo(() => resolvePageLabel(location.pathname), [location.pathname]);
  const suggestions = useMemo(
    () =>
      location.pathname.startsWith('/dashboards') || location.pathname.startsWith('/screen/')
        ? DASHBOARD_SUGGESTIONS
        : SUGGESTIONS,
    [location.pathname],
  );

  const canSend = (input.trim().length > 0 || attachments.length > 0) && !loading;
  const voiceShortcut = useMemo(() => getVoiceInputShortcutLabel(), []);

  const {
    supported: voiceSupported,
    listening: voiceListening,
    transcribing: voiceTranscribing,
    audioStream: voiceAudioStream,
    toggle: toggleVoiceInput,
    stop: stopVoiceInput,
  } = useCopilotVoiceInput({
    enabled: open && !loading,
    value: input,
    onChange: setInput,
    llmProfileId: selectedLlmProfileId,
  });

  const loadLlmProfiles = useCallback(async () => {
    const data = await settingsApi.getLlmProfiles();
    setLlmProfiles(data.profiles);
    const stored = readCopilotLlmProfileId();
    const validStored = data.profiles.find((profile) => profile.id === stored)?.id;
    const nextId = validStored || data.activeProfileId || data.profiles[0]?.id;
    setSelectedLlmProfileId(nextId);
    if (nextId) {
      writeCopilotLlmProfileId(nextId);
    }
  }, []);

  useEffect(() => {
    if (!open) {
      return;
    }
    loadLlmProfiles().catch(() => {
      // Copilot 仍可使用工作区默认配置
    });
  }, [open, loadLlmProfiles]);

  useEffect(() => {
    const handleUpdated = () => {
      if (open) {
        loadLlmProfiles().catch(() => undefined);
      }
    };
    window.addEventListener(SEMANTIC_LLM_UPDATED_EVENT, handleUpdated);
    return () => window.removeEventListener(SEMANTIC_LLM_UPDATED_EVENT, handleUpdated);
  }, [open, loadLlmProfiles]);

  const selectedLlmProfile = useMemo(
    () => llmProfiles.find((profile) => profile.id === selectedLlmProfileId),
    [llmProfiles, selectedLlmProfileId],
  );

  const addImageFiles = useCallback(async (files: FileList | File[]) => {
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
      if (fileArray.length > selected.length) {
        message.warning(`最多附带 ${MAX_COPILOT_IMAGES} 张截图，已忽略多余图片`);
      }
    } catch (err) {
      message.error(err instanceof Error ? err.message : '读取图片失败');
    }
  }, [attachments.length]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading, open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const handleShortcut = (event: KeyboardEvent) => {
      if (!(event.metaKey || event.ctrlKey) || !event.shiftKey || event.code !== 'Space') {
        return;
      }
      event.preventDefault();
      toggleVoiceInput();
    };
    window.addEventListener('keydown', handleShortcut);
    return () => window.removeEventListener('keydown', handleShortcut);
  }, [open, toggleVoiceInput]);

  useEffect(() => {
    if (!open) {
      stopVoiceInput();
    }
  }, [open, stopVoiceInput]);

  const executeChat = async (
    history: DisplayMessage[],
    options?: { userText?: string; plan?: CopilotActivePlan; playbookId?: string },
  ) => {
    setLoading(true);
    try {
      const response = await copilotApi.chat({
        messages: toApiMessages(history),
        currentPage: location.pathname,
        dryRun,
        llmProfileId: selectedLlmProfileId,
        activePlan: options?.plan ?? activePlan,
        playbookId: options?.playbookId,
      });
      if (response.plan) {
        setActivePlan(response.plan);
      } else if (response.planCompleted) {
        setActivePlan(undefined);
      }
      setMessages((prev) => [
        ...prev,
        {
          id: createId(),
          role: 'assistant',
          content: formatCopilotReply(response.reply || '已完成。'),
          actions: response.actions,
          plan: response.plan,
          matchedPlaybooks: response.matchedPlaybooks,
          suggestSavePlaybook: response.suggestSavePlaybook,
        },
      ]);
    } catch (err) {
      const message = err instanceof Error ? err.message : '请求失败';
      setMessages((prev) => [
        ...prev,
        {
          id: createId(),
          role: 'assistant',
          content: `**出错了：** ${message}`,
          error: true,
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  const continuePlan = async () => {
    if (!activePlan || loading) {
      return;
    }
    const history = [
      ...messages,
      {
        id: createId(),
        role: 'user' as const,
        content: '继续执行下一步',
      },
    ];
    setMessages(history);
    await executeChat(history, { plan: activePlan, userText: '继续执行下一步' });
  };

  const usePlaybook = async (playbookId: string) => {
    if (loading) {
      return;
    }
    try {
      const plan = await copilotApi.activatePlaybook(playbookId);
      setActivePlan(plan);
      const history = [
        ...messages,
        {
          id: createId(),
          role: 'user' as const,
          content: `使用技能「${plan.playbookName ?? '已选技能'}」开始执行`,
        },
      ];
      setMessages(history);
      await executeChat(history, { plan, playbookId });
    } catch {
      /* handled by interceptor */
    }
  };

  const savePlanAsPlaybook = async (name: string, plan: CopilotActivePlan) => {
    await copilotApi.savePlaybookFromPlan({
      name,
      code: `playbook-${Date.now().toString(36)}`,
      description: `由 Copilot 任务沉淀：${name}`,
      triggerKeywords: [name.replace(/技能|大屏/g, '').trim()].filter(Boolean),
      plan,
    });
    setActivePlan(undefined);
  };

  const sendMessage = async (text: string, images: string[] = attachments) => {
    const content = text.trim();
    const pendingImages = images.length > 0 ? images : undefined;
    if ((!content && !pendingImages) || loading) {
      return;
    }

    const history = [
      ...messages,
      {
        id: createId(),
        role: 'user' as const,
        content: content || '请根据截图理解我的意图并协助配置。',
        images: pendingImages,
      },
    ];
    setMessages(history);
    setInput('');
    setAttachments([]);
    await executeChat(history);
  };

  const retryFromUserMessage = async (userMessageId: string) => {
    if (loading) {
      return;
    }
    const userIndex = messages.findIndex((item) => item.id === userMessageId);
    if (userIndex < 0 || messages[userIndex].role !== 'user') {
      return;
    }
    const history = messages.slice(0, userIndex + 1);
    setMessages(history);
    await executeChat(history);
  };

  if (!open) {
    return null;
  }

  return (
    <aside className="copilot-panel" aria-label="Atelier Copilot">
        <header className="copilot-header">
          <div className="copilot-header-title">
            <strong>Atelier Copilot</strong>
            <span>对话式配置助手</span>
          </div>
          <div className="copilot-header-actions">
            <button
              type="button"
              className="copilot-icon-btn"
              aria-label="关闭"
              onClick={() => setOpen(false)}
            >
              <CloseOutlined />
            </button>
          </div>
        </header>

        <div className="copilot-toolbar">
          <span className="copilot-context-chip">当前页面 · {pageLabel}</span>
          <label className="copilot-toggle">
            <input
              type="checkbox"
              checked={dryRun}
              onChange={(event) => setDryRun(event.target.checked)}
            />
            仅规划
          </label>
        </div>

        <div className="copilot-messages">
          {messages.map((message, index) => (
            <div
              key={message.id}
              className={`copilot-message ${message.role}${message.error ? ' error' : ''}`}
            >
              <div className="copilot-message-header">
                <div className="copilot-message-label">
                  {message.role === 'user' ? 'You' : 'Copilot'}
                </div>
                {message.role === 'user' && isFailedTurn(messages, index) && !loading && (
                  <button
                    type="button"
                    className="copilot-retry-btn"
                    aria-label="重新发送"
                    title="重新发送"
                    onClick={() => retryFromUserMessage(message.id)}
                  >
                    <RedoOutlined />
                  </button>
                )}
              </div>
              <div className="copilot-message-bubble">
                {message.role === 'user' ? (
                  <>
                    {message.images && message.images.length > 0 && (
                      <div className="copilot-message-images">
                        {message.images.map((image, imageIndex) => (
                          <a
                            key={`${message.id}-image-${imageIndex}`}
                            href={image}
                            target="_blank"
                            rel="noreferrer"
                            className="copilot-message-image-link"
                          >
                            <img src={image} alt={`截图 ${imageIndex + 1}`} className="copilot-message-image" />
                          </a>
                        ))}
                      </div>
                    )}
                    {message.content}
                  </>
                ) : (
                  <>
                    <CopilotMessageContent content={message.content} />
                    <CopilotPlanPanel
                      plan={message.plan}
                      matchedPlaybooks={message.matchedPlaybooks}
                      suggestSave={message.suggestSavePlaybook}
                      onContinue={() => void continuePlan()}
                      onUsePlaybook={(id) => void usePlaybook(id)}
                      onSavePlaybook={(name) =>
                        message.plan ? savePlanAsPlaybook(name, message.plan) : Promise.resolve()
                      }
                    />
                    {message.actions && message.actions.length > 0 && (
                      <div className="copilot-actions">
                        {message.actions.map((action, actionIndex) => (
                          <div
                            key={`${action.tool}-${actionIndex}`}
                            className={`copilot-action-block${action.planned ? ' planned' : ''}`}
                          >
                            <div className="copilot-action-item">
                              <span
                                className={`copilot-action-dot ${
                                  action.planned ? 'planned' : action.success ? 'success' : 'error'
                                }`}
                              />
                              <div>
                                <div>{action.message}</div>
                                <div style={{ color: '#8b8b8b', marginTop: 2 }}>
                                  {copilotToolLabel(action.tool)}
                                  {action.planned ? ' · 仅规划' : ''}
                                </div>
                              </div>
                            </div>
                            {action.tool === 'execute_sql' &&
                              (action.success || action.planned) &&
                              isSqlQueryResult(action.result) && (
                                <CopilotQueryResult
                                  data={action.result}
                                  planned={action.planned}
                                />
                              )}
                            {action.tool === 'run_warning_rule' &&
                              (action.success || action.planned) &&
                              isWarningJobResult(action.result) && (
                                <CopilotWarningJobCard
                                  data={action.result}
                                  planned={action.planned}
                                />
                              )}
                            {action.tool === 'get_warning_job_result' &&
                              (action.success || action.planned) &&
                              isWarningHitResult(action.result) && (
                                <CopilotWarningHitResultCard
                                  data={action.result}
                                  planned={action.planned}
                                />
                              )}
                            {isWriteAction(action.tool) &&
                              (action.success || action.planned) &&
                              isSqlExecuteResult(action.result) && (
                                <CopilotWriteResult
                                  data={action.result}
                                  planned={action.planned}
                                />
                              )}
                            {action.tool === 'create_dashboard' &&
                              (action.success || action.planned) &&
                              isDashboardScreen(action.result) && (
                                <div style={{ marginTop: 8 }}>
                                  <Button
                                    type="link"
                                    size="small"
                                    disabled={action.planned}
                                    onClick={() => {
                                      const screen = action.result as { id?: string; code: string };
                                      if (screen.id) {
                                        navigate(`/dashboards/${screen.id}/edit`);
                                      } else {
                                        navigate('/dashboards');
                                      }
                                    }}
                                  >
                                    打开大屏设计器
                                  </Button>
                                </div>
                              )}
                          </div>
                        ))}
                      </div>
                    )}
                    {message.error && !loading && (
                      <button
                        type="button"
                        className="copilot-retry-inline"
                        onClick={() => {
                          const userMessage = [...messages]
                            .slice(0, index)
                            .reverse()
                            .find((item) => item.role === 'user');
                          if (userMessage) {
                            retryFromUserMessage(userMessage.id);
                          }
                        }}
                      >
                        <RedoOutlined />
                        重新发送
                      </button>
                    )}
                  </>
                )}
              </div>
            </div>
          ))}
          {loading && (
            <div className="copilot-message assistant">
              <div className="copilot-message-label">Copilot</div>
              <div className="copilot-typing" aria-label="正在思考">
                <span />
                <span />
                <span />
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {messages.length <= 1 && (
          <div className="copilot-suggestions">
            <div className="copilot-suggestions-label">快速开始</div>
            <div className="copilot-suggestion-list">
              {suggestions.map((item) => (
                <button
                  key={item}
                  type="button"
                  className="copilot-suggestion"
                  onClick={() => sendMessage(item)}
                >
                  {item}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="copilot-composer-wrap">
          <div
            className={`copilot-composer${voiceListening || voiceTranscribing ? ' listening' : ''}`}
            onDragOver={(event) => {
              event.preventDefault();
            }}
            onDrop={(event) => {
              event.preventDefault();
              if (event.dataTransfer.files.length > 0) {
                addImageFiles(event.dataTransfer.files);
              }
            }}
          >
            {attachments.length > 0 && (
              <div className="copilot-attachments">
                {attachments.map((image, index) => (
                  <div key={`attachment-${index}`} className="copilot-attachment">
                    <img src={image} alt={`待发送截图 ${index + 1}`} />
                    <button
                      type="button"
                      className="copilot-attachment-remove"
                      aria-label="移除截图"
                      onClick={() => setAttachments((prev) => prev.filter((_, i) => i !== index))}
                    >
                      <CloseOutlined />
                    </button>
                  </div>
                ))}
              </div>
            )}
            <textarea
              value={input}
              onChange={(event) => setInput(event.target.value)}
              placeholder={
                voiceTranscribing
                  ? '正在识别语音…'
                  : voiceListening
                    ? '正在聆听，说完后点击停止'
                    : '描述你想创建的配置，Shift+Enter 换行'
              }
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
                addImageFiles(imageFiles);
              }}
              onCompositionStart={() => {
                imeComposingRef.current = true;
              }}
              onCompositionEnd={() => {
                imeComposingRef.current = false;
              }}
              onKeyDown={(event) => {
                if (
                  (event.metaKey || event.ctrlKey) &&
                  event.shiftKey &&
                  event.code === 'Space'
                ) {
                  event.preventDefault();
                  toggleVoiceInput();
                  return;
                }
                if (event.key !== 'Enter' || event.shiftKey) {
                  return;
                }
                // 输入法组字期间忽略 Enter，避免误触发送（与 Cursor 一致）
                if (
                  imeComposingRef.current ||
                  event.nativeEvent.isComposing ||
                  event.keyCode === 229
                ) {
                  return;
                }
                event.preventDefault();
                sendMessage(input);
              }}
            />
            <input
              ref={fileInputRef}
              type="file"
              accept="image/png,image/jpeg,image/gif,image/webp"
              multiple
              hidden
              onChange={(event) => {
                if (event.target.files && event.target.files.length > 0) {
                  addImageFiles(event.target.files);
                }
                event.target.value = '';
              }}
            />
            <div className="copilot-composer-footer">
              <div className="copilot-composer-footer-left">
                {llmProfiles.length > 0 && (
                  <>
                    <div
                      className={`copilot-llm-pill${
                        selectedLlmProfile && !selectedLlmProfile.apiKeyConfigured
                          ? ' warning'
                          : ''
                      }`}
                      title={
                        selectedLlmProfile && !selectedLlmProfile.apiKeyConfigured
                          ? '当前模型未配置 API Key'
                          : undefined
                      }
                    >
                      <span className="copilot-llm-pill-text">
                        {selectedLlmProfile?.name || '选择模型'}
                      </span>
                      <DownOutlined className="copilot-llm-pill-chevron" />
                      <select
                        className="copilot-llm-pill-select"
                        value={selectedLlmProfileId || ''}
                        onChange={(event) => {
                          const nextId = event.target.value;
                          setSelectedLlmProfileId(nextId);
                          writeCopilotLlmProfileId(nextId);
                        }}
                        disabled={loading}
                        aria-label="切换 LLM"
                      >
                        {llmProfiles.map((profile) => (
                          <option key={profile.id} value={profile.id}>
                            {profile.name}
                            {profile.model ? ` · ${profile.model}` : ''}
                            {!profile.enabled ? '（未启用）' : ''}
                          </option>
                        ))}
                      </select>
                    </div>
                    {selectedLlmProfile?.model && (
                      <span className="copilot-llm-model">{selectedLlmProfile.model}</span>
                    )}
                  </>
                )}
              </div>
              <div className="copilot-composer-footer-right">
                {voiceListening || voiceTranscribing ? (
                  <>
                    <div className="copilot-voice-recording-controls">
                      <CopilotVoiceWaveform
                        stream={voiceAudioStream}
                        active={voiceListening}
                        transcribing={voiceTranscribing}
                      />
                      {voiceListening && (
                        <button
                          type="button"
                          className="copilot-voice-stop-btn"
                          aria-label="停止录音"
                          title="停止录音"
                          onClick={() => {
                            void stopVoiceInput();
                          }}
                        >
                          <span className="copilot-voice-stop-icon" />
                        </button>
                      )}
                      {voiceTranscribing && (
                        <span className="copilot-voice-status">识别中</span>
                      )}
                    </div>
                    <button
                      type="button"
                      className="copilot-send-btn"
                      aria-label="发送"
                      disabled={!canSend}
                      onClick={() => sendMessage(input)}
                    >
                      <SendOutlined />
                    </button>
                  </>
                ) : (
                  <>
                    <button
                      type="button"
                      className="copilot-attach-btn"
                      aria-label="上传截图"
                      title="上传截图"
                      disabled={loading || attachments.length >= MAX_COPILOT_IMAGES}
                      onClick={() => fileInputRef.current?.click()}
                    >
                      <PaperClipOutlined />
                    </button>
                    {voiceSupported && (
                      <button
                        type="button"
                        className="copilot-voice-btn"
                        aria-label="语音输入"
                        title={`语音输入 (${voiceShortcut})`}
                        disabled={loading}
                        onClick={() => {
                          void toggleVoiceInput();
                        }}
                      >
                        <AudioOutlined />
                      </button>
                    )}
                    <button
                      type="button"
                      className="copilot-send-btn"
                      aria-label="发送"
                      disabled={!canSend}
                      onClick={() => sendMessage(input)}
                    >
                      <SendOutlined />
                    </button>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
    </aside>
  );
}
