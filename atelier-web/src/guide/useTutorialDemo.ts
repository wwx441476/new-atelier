import { useCallback, useEffect, useRef } from 'react';
import { message } from 'antd';
import { useOnboarding } from './OnboardingContext';
import {
  prepareDemoForStep,
  type DemoFillOutcome,
  type TutorialChain,
} from './demoTutorial';
import type { OnboardingStepId } from './types';

type FillHandler = (outcome: DemoFillOutcome) => void | Promise<void>;

export function useTutorialDemo(stepId: OnboardingStepId, onFill: FillHandler) {
  const {
    demoFillStep,
    clearDemoFill,
    tutorialChain,
    setTutorialChain,
    notifyTutorialSave,
  } = useOnboarding();
  const onFillRef = useRef(onFill);
  onFillRef.current = onFill;

  const runDemoFill = useCallback(async () => {
    const outcome = await prepareDemoForStep(stepId);
    await onFillRef.current(outcome);
    if (outcome.type === 'skip') {
      message.info(outcome.message);
      notifyTutorialSave(stepId, true);
    } else if (outcome.type === 'form') {
      message.info(outcome.hint);
    } else if (outcome.type === 'chain') {
      setTutorialChain({ stepId, chain: outcome.chain });
      message.info('请按引导继续保存演示数据');
    }
  }, [stepId, notifyTutorialSave, setTutorialChain]);

  useEffect(() => {
    if (demoFillStep === stepId) {
      clearDemoFill();
      window.setTimeout(() => {
        runDemoFill();
      }, 350);
    }
  }, [demoFillStep, stepId, clearDemoFill, runDemoFill]);

  const onSaveSuccess = useCallback(() => {
    notifyTutorialSave(stepId);
  }, [notifyTutorialSave, stepId]);

  return {
    tutorialChain: tutorialChain?.stepId === stepId ? tutorialChain.chain : null,
    setTutorialChain: (chain: TutorialChain | null) =>
      setTutorialChain(chain ? { stepId, chain } : null),
    onSaveSuccess,
    triggerDemoFill: runDemoFill,
  };
}
