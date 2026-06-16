import { Button, Modal, Space, Tag, Typography, message } from 'antd';
import { BookOutlined, SaveOutlined } from '@ant-design/icons';
import type { CopilotActivePlan, CopilotPlanStep, CopilotPlaybook } from '../../api/types';
import './CopilotPlanPanel.css';

interface CopilotPlanPanelProps {
  plan?: CopilotActivePlan;
  matchedPlaybooks?: CopilotPlaybook[];
  suggestSave?: boolean;
  onContinue: () => void;
  onUsePlaybook: (playbookId: string) => void;
  onSavePlaybook: (name: string) => Promise<void>;
}

function stepIcon(step: CopilotPlanStep, index: number, currentIndex?: number) {
  if (step.status === 'done') {
    return '✅';
  }
  if (step.status === 'failed') {
    return '❌';
  }
  if (currentIndex === index) {
    return '▶️';
  }
  return '⬜';
}

export default function CopilotPlanPanel({
  plan,
  matchedPlaybooks,
  suggestSave,
  onContinue,
  onUsePlaybook,
  onSavePlaybook,
}: CopilotPlanPanelProps) {
  const hasPlan = plan?.steps && plan.steps.length > 0;
  const hasPlaybooks = matchedPlaybooks && matchedPlaybooks.length > 0;

  if (!hasPlan && !hasPlaybooks && !suggestSave) {
    return null;
  }

  const handleSave = () => {
    Modal.confirm({
      title: '保存为技能',
      content: (
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          将当前任务步骤沉淀为可复用技能，下次类似需求可一键加载。
        </Typography.Paragraph>
      ),
      okText: '保存',
      onOk: async () => {
        const name = plan?.playbookName ?? '自定义技能';
        await onSavePlaybook(name);
        message.success('技能已保存');
      },
    });
  };

  return (
    <div className="copilot-plan-panel">
      {hasPlaybooks && (
        <div className="copilot-plan-section">
          <Typography.Text type="secondary" className="copilot-plan-label">
            <BookOutlined /> 推荐技能
          </Typography.Text>
          <Space wrap size={[6, 6]} style={{ marginTop: 6 }}>
            {matchedPlaybooks!.map((playbook) => (
              <Tag
                key={playbook.id ?? playbook.code}
                className="copilot-playbook-tag"
                onClick={() => playbook.id && onUsePlaybook(playbook.id)}
              >
                {playbook.name}
              </Tag>
            ))}
          </Space>
        </div>
      )}

      {hasPlan && (
        <div className="copilot-plan-section">
          <Typography.Text type="secondary" className="copilot-plan-label">
            任务步骤
          </Typography.Text>
          <ul className="copilot-plan-steps">
            {plan!.steps!.map((step, index) => (
              <li
                key={step.id ?? `step-${index}`}
                className={`copilot-plan-step status-${step.status ?? 'pending'}${
                  plan!.currentStepIndex === index ? ' current' : ''
                }`}
              >
                <span className="copilot-plan-step-icon">
                  {stepIcon(step, index, plan!.currentStepIndex)}
                </span>
                <span className="copilot-plan-step-title">{step.title}</span>
              </li>
            ))}
          </ul>
          {!plan!.completed && (
            <Button size="small" type="primary" ghost onClick={onContinue} style={{ marginTop: 8 }}>
              继续下一步
            </Button>
          )}
        </div>
      )}

      {suggestSave && (
        <Button
          size="small"
          icon={<SaveOutlined />}
          onClick={() => void handleSave()}
          style={{ marginTop: 8 }}
        >
          保存为技能
        </Button>
      )}
    </div>
  );
}
