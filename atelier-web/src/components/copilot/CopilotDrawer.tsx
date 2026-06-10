import { useEffect, useMemo, useRef, useState } from 'react';
import { CloseOutlined, RedoOutlined, SendOutlined } from '@ant-design/icons';
import { useLocation } from 'react-router-dom';
import { copilotApi } from '../../api/copilot';
import type { CopilotActionResult, CopilotSqlQueryResult, SqlExecuteResult } from '../../api/types';
import { ONBOARDING_STEPS } from '../../guide/steps';
import CopilotMessageContent from './CopilotMessageContent';
import CopilotQueryResult from './CopilotQueryResult';
import CopilotWriteResult from './CopilotWriteResult';
import { useCopilot } from './CopilotContext';
import './CopilotDrawer.css';

const SUGGESTIONS = [
  '帮我创建一个 H2 演示数据源',
  '从 PUBLIC schema 同步 orders 表到元数据',
  '创建一个部门 LIST 维度，并添加两个维度值',
  '基于 orders 表创建按部门汇总的营收指标',
  '创建利润低于 500 的预警规则',
];

interface DisplayMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  actions?: CopilotActionResult[];
  error?: boolean;
}

function toApiMessages(history: DisplayMessage[]) {
  return history
    .filter((item) => item.id !== 'welcome')
    .map((item) => ({ role: item.role, content: item.content }));
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

function createId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function resolvePageLabel(pathname: string) {
  return ONBOARDING_STEPS.find((step) => step.path === pathname)?.menuLabel || '工作台';
}

export default function CopilotDrawer() {
  const location = useLocation();
  const { open, setOpen } = useCopilot();
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [dryRun, setDryRun] = useState(false);
  const [messages, setMessages] = useState<DisplayMessage[]>([
    {
      id: 'welcome',
      role: 'assistant',
      content:
        '你好，我是 **Atelier Copilot**。你可以用自然语言创建数据源、元数据、维度、指标和预警规则。\n\n先在右上角配置好 LLM，然后直接告诉我你想搭建什么。',
    },
  ]);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const pageLabel = useMemo(() => resolvePageLabel(location.pathname), [location.pathname]);

  const canSend = input.trim().length > 0 && !loading;

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading, open]);

  const executeChat = async (history: DisplayMessage[]) => {
    setLoading(true);
    try {
      const response = await copilotApi.chat({
        messages: toApiMessages(history),
        currentPage: location.pathname,
        dryRun,
      });
      setMessages((prev) => [
        ...prev,
        {
          id: createId(),
          role: 'assistant',
          content: response.reply || '已完成。',
          actions: response.actions,
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

  const sendMessage = async (text: string) => {
    const content = text.trim();
    if (!content || loading) {
      return;
    }

    const history = [...messages, { id: createId(), role: 'user' as const, content }];
    setMessages(history);
    setInput('');
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
                  message.content
                ) : (
                  <>
                    <CopilotMessageContent content={message.content} />
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
                                  {action.tool}
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
                            {isWriteAction(action.tool) &&
                              (action.success || action.planned) &&
                              isSqlExecuteResult(action.result) && (
                                <CopilotWriteResult
                                  data={action.result}
                                  planned={action.planned}
                                />
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
              {SUGGESTIONS.map((item) => (
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
          <div className="copilot-composer">
            <textarea
              value={input}
              onChange={(event) => setInput(event.target.value)}
              placeholder="描述你想创建的配置，Shift+Enter 换行"
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault();
                  sendMessage(input);
                }
              }}
            />
            <button
              type="button"
              className="copilot-send-btn"
              aria-label="发送"
              disabled={!canSend}
              onClick={() => sendMessage(input)}
            >
              <SendOutlined />
            </button>
          </div>
          <div className="copilot-composer-hint">Enter 发送 · Shift+Enter 换行</div>
        </div>
    </aside>
  );
}
