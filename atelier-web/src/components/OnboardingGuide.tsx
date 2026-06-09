import { useEffect } from 'react';
import {
  Button,
  Drawer,
  Modal,
  Space,
  Steps,
  Tag,
  Tour,
  Typography,
} from 'antd';
import {
  CheckCircleOutlined,
  CompassOutlined,
  FormOutlined,
  PlayCircleOutlined,
  RightOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useLocation } from 'react-router-dom';
import { useOnboarding } from '../guide/OnboardingContext';
import { getTourStepsForPath, ONBOARDING_STEPS } from '../guide/steps';
import type { OnboardingStepId } from '../guide/types';

export default function OnboardingGuide() {
  const location = useLocation();
  const {
    storage,
    currentStepId,
    currentStepIndex,
    tourOpen,
    drawerOpen,
    welcomeOpen,
    detectedCompleted,
    tutorialMode,
    startGuide,
    startTutorialWithDemo,
    closeDrawer,
    dismissWelcome,
    goToStep,
    triggerDemoFill,
    openPageTour,
    closePageTour,
    markComplete,
    resetGuide,
  } = useOnboarding();

  const tourSteps = getTourStepsForPath(location.pathname) ?? [];
  const completedCount = storage.completedSteps.length;

  useEffect(() => {
    if (tourOpen && tourSteps.length === 0) {
      closePageTour();
    }
  }, [tourOpen, tourSteps.length, closePageTour]);

  const renderStepStatus = (stepId: OnboardingStepId) => {
    if (storage.completedSteps.includes(stepId)) {
      return <Tag color="success">已完成</Tag>;
    }
    if (detectedCompleted.includes(stepId)) {
      return <Tag color="processing">检测到数据</Tag>;
    }
    return <Tag>待完成</Tag>;
  };

  return (
    <>
      <Modal
        title="欢迎使用 Atelier 数据工场"
        open={welcomeOpen}
        onCancel={dismissWelcome}
        footer={[
          <Button key="skip" onClick={dismissWelcome}>
            稍后了解
          </Button>,
          <Button key="start" icon={<PlayCircleOutlined />} onClick={startGuide}>
            开始入门指导
          </Button>,
          <Button
            key="demo"
            type="primary"
            icon={<ThunderboltOutlined />}
            onClick={startTutorialWithDemo}
          >
            演示数据体验
          </Button>,
        ]}
        width={520}
      >
        <Typography.Paragraph>
          Atelier 帮助你从数据源到预警规则，一步步搭建指标分析能力。建议按以下顺序完成配置：
        </Typography.Paragraph>
        <Steps
          direction="vertical"
          size="small"
          current={-1}
          items={ONBOARDING_STEPS.map((step) => ({
            title: step.title,
            description: step.summary,
          }))}
        />
      </Modal>

      <Drawer
        title="入门指导"
        placement="right"
        width={400}
        open={drawerOpen}
        onClose={closeDrawer}
        extra={
          <Button type="link" size="small" onClick={resetGuide}>
            重置进度
          </Button>
        }
      >
        <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
          按顺序完成以下步骤，即可快速搭建完整的指标分析链路。系统会根据已有数据自动检测完成进度。
        </Typography.Paragraph>
        <Button
          type="primary"
          block
          icon={<ThunderboltOutlined />}
          style={{ marginBottom: 16 }}
          onClick={startTutorialWithDemo}
        >
          一键演示流程（自动填入数据，逐步保存）
        </Button>
        {tutorialMode && (
          <Typography.Paragraph type="warning" style={{ fontSize: 13, marginBottom: 16 }}>
            演示模式已开启：每步会自动填入演示数据，请检查后点击保存，系统将引导你进入下一步。
          </Typography.Paragraph>
        )}
        <Steps
          direction="vertical"
          current={currentStepIndex}
          items={ONBOARDING_STEPS.map((step) => ({
            title: (
              <Space size={8}>
                <span>{step.title}</span>
                {renderStepStatus(step.id)}
              </Space>
            ),
            description: (
              <div style={{ marginBottom: 8 }}>
                <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                  {step.summary}
                </Typography.Text>
                <ul style={{ margin: '8px 0 0', paddingLeft: 18, fontSize: 13, color: '#666' }}>
                  {step.tips.map((tip) => (
                    <li key={tip}>{tip}</li>
                  ))}
                </ul>
                <Space style={{ marginTop: 10 }}>
                  <Button
                    size="small"
                    type={step.id === currentStepId ? 'primary' : 'default'}
                    onClick={() => goToStep(step.id)}
                  >
                    前往此步
                  </Button>
                  {step.id === currentStepId && (
                    <>
                      <Button size="small" onClick={openPageTour}>
                        本页导览
                      </Button>
                      <Button
                        size="small"
                        icon={<FormOutlined />}
                        onClick={() => triggerDemoFill(step.id)}
                      >
                        填入演示数据
                      </Button>
                    </>
                  )}
                  {!storage.completedSteps.includes(step.id) && (
                    <Button size="small" type="link" onClick={() => markComplete(step.id)}>
                      标记完成
                    </Button>
                  )}
                </Space>
              </div>
            ),
          }))}
        />
        {currentStepIndex < ONBOARDING_STEPS.length - 1 && (
          <Button
            type="primary"
            block
            style={{ marginTop: 16 }}
            icon={<RightOutlined />}
            onClick={() => goToStep(ONBOARDING_STEPS[currentStepIndex + 1].id)}
          >
            下一步：{ONBOARDING_STEPS[currentStepIndex + 1].title}
          </Button>
        )}
        {completedCount === ONBOARDING_STEPS.length && (
          <Typography.Paragraph style={{ marginTop: 16, textAlign: 'center' }}>
            <CheckCircleOutlined style={{ color: '#52c41a', marginRight: 8 }} />
            恭喜，入门流程已全部完成！
          </Typography.Paragraph>
        )}
      </Drawer>

      <Tour
        open={tourOpen && tourSteps.length > 0}
        onClose={closePageTour}
        steps={tourSteps}
        indicatorsRender={(current, total) => (
          <span>
            {current + 1} / {total}
          </span>
        )}
      />
    </>
  );
}

export function OnboardingHeaderActions() {
  const {
    storage,
    currentStepIndex,
    progressLoading,
    tutorialMode,
    openDrawer,
    startGuide,
    openPageTour,
    triggerDemoFill,
  } = useOnboarding();

  const completedCount = storage.completedSteps.length;
  const total = ONBOARDING_STEPS.length;

  return (
    <Space size={12}>
      <Button
        type="text"
        icon={<CompassOutlined />}
        onClick={openDrawer}
        loading={progressLoading}
      >
        入门指导
        <Tag
          color={completedCount === total ? 'success' : 'processing'}
          style={{ marginLeft: 8, marginRight: 0 }}
        >
          {completedCount}/{total}
        </Tag>
      </Button>
      {storage.guideActive && (
        <>
          <Button size="small" onClick={openPageTour}>
            本页导览
          </Button>
          <Button size="small" icon={<FormOutlined />} onClick={() => triggerDemoFill()}>
            填入演示数据
          </Button>
        </>
      )}
      {tutorialMode && (
        <Tag color="volcano">演示模式</Tag>
      )}
      {completedCount < total && !storage.guideActive && (
        <Button size="small" type="primary" ghost icon={<PlayCircleOutlined />} onClick={startGuide}>
          继续入门
        </Button>
      )}
      {storage.guideActive && currentStepIndex < total - 1 && (
        <Typography.Text type="secondary" style={{ fontSize: 13 }}>
          当前：{ONBOARDING_STEPS[currentStepIndex].title}
        </Typography.Text>
      )}
    </Space>
  );
}
