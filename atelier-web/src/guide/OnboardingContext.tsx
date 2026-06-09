import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { message } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { datasourceApi } from '../api/datasource';
import { dimensionApi } from '../api/dimension';
import { metadataApi } from '../api/metadata';
import { metricApi } from '../api/metric';
import { warningApi } from '../api/warning';
import type { TutorialChain } from './demoTutorial';
import {
  getNextStepId,
  getStepById,
  getStepIndex,
  ONBOARDING_STEPS,
} from './steps';
import {
  loadOnboardingStorage,
  markStepCompleted,
  saveOnboardingStorage,
} from './storage';
import type { OnboardingStepId, OnboardingStorage } from './types';

interface TutorialChainState {
  stepId: OnboardingStepId;
  chain: TutorialChain;
}

interface OnboardingContextValue {
  storage: OnboardingStorage;
  currentStepId: OnboardingStepId;
  currentStepIndex: number;
  tourOpen: boolean;
  drawerOpen: boolean;
  welcomeOpen: boolean;
  progressLoading: boolean;
  detectedCompleted: OnboardingStepId[];
  tutorialMode: boolean;
  demoFillStep: OnboardingStepId | null;
  tutorialChain: TutorialChainState | null;
  startGuide: () => void;
  startTutorialWithDemo: () => void;
  openDrawer: () => void;
  closeDrawer: () => void;
  dismissWelcome: () => void;
  goToStep: (stepId: OnboardingStepId, options?: { openTour?: boolean }) => void;
  triggerDemoFill: (stepId?: OnboardingStepId) => void;
  clearDemoFill: () => void;
  setTutorialChain: (chain: TutorialChainState | null) => void;
  notifyTutorialSave: (stepId: OnboardingStepId, skipped?: boolean) => void;
  openPageTour: () => void;
  closePageTour: () => void;
  markComplete: (stepId: OnboardingStepId) => void;
  resetGuide: () => void;
}

const OnboardingContext = createContext<OnboardingContextValue | null>(null);

function pathToStepId(pathname: string): OnboardingStepId {
  const step = ONBOARDING_STEPS.find((item) => item.path === pathname);
  return step?.id ?? 'datasources';
}

function mergeCompleted(
  stored: OnboardingStepId[],
  detected: OnboardingStepId[],
): OnboardingStepId[] {
  return Array.from(new Set([...stored, ...detected]));
}

export function OnboardingProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [storage, setStorage] = useState<OnboardingStorage>(() => loadOnboardingStorage());
  const [tourOpen, setTourOpen] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [welcomeOpen, setWelcomeOpen] = useState(false);
  const [progressLoading, setProgressLoading] = useState(true);
  const [detectedCompleted, setDetectedCompleted] = useState<OnboardingStepId[]>([]);
  const [tutorialMode, setTutorialMode] = useState(false);
  const [demoFillStep, setDemoFillStep] = useState<OnboardingStepId | null>(null);
  const [tutorialChain, setTutorialChain] = useState<TutorialChainState | null>(null);

  const currentStepId = pathToStepId(location.pathname);
  const currentStepIndex = getStepIndex(currentStepId);

  const persist = useCallback((updater: OnboardingStorage | ((prev: OnboardingStorage) => OnboardingStorage)) => {
    setStorage((prev) => {
      const next = typeof updater === 'function' ? updater(prev) : updater;
      saveOnboardingStorage(next);
      return next;
    });
  }, []);

  const refreshProgress = useCallback(async () => {
    setProgressLoading(true);
    try {
      const [datasources, tables, dimensions, metrics, rules] = await Promise.all([
        datasourceApi.list(),
        metadataApi.listTables(),
        dimensionApi.list(),
        metricApi.listDefinitions(),
        warningApi.list(),
      ]);
      const detected: OnboardingStepId[] = [];
      if (datasources.length > 0) detected.push('datasources');
      if (tables.length > 0) detected.push('metadata');
      if (dimensions.length > 0) detected.push('dimensions');
      if (metrics.length > 0) detected.push('metrics');
      if (rules.length > 0) detected.push('warning-rules');
      setDetectedCompleted(detected);
      setStorage((prev) => {
        const next = {
          ...prev,
          completedSteps: mergeCompleted(prev.completedSteps, detected),
        };
        saveOnboardingStorage(next);
        return next;
      });
    } finally {
      setProgressLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshProgress();
  }, [refreshProgress, location.pathname]);

  useEffect(() => {
    if (!storage.welcomeSeen && !storage.guideActive) {
      setWelcomeOpen(true);
    }
  }, [storage.welcomeSeen, storage.guideActive]);

  const openPageTour = useCallback(() => {
    window.setTimeout(() => setTourOpen(true), 300);
  }, []);

  const goToStep = useCallback(
    (stepId: OnboardingStepId, options?: { openTour?: boolean }) => {
      const step = getStepById(stepId);
      persist((prev) => ({ ...prev, guideActive: true }));
      setDrawerOpen(false);
      navigate(step.path);
      if (options?.openTour !== false) {
        openPageTour();
      }
    },
    [navigate, openPageTour, persist],
  );

  const startGuide = useCallback(() => {
    setTutorialMode(false);
    setWelcomeOpen(false);
    setStorage((prev) => {
      const firstIncomplete =
        ONBOARDING_STEPS.find((step) => !prev.completedSteps.includes(step.id)) ??
        ONBOARDING_STEPS[0];
      const next = {
        ...prev,
        welcomeSeen: true,
        guideActive: true,
      };
      saveOnboardingStorage(next);
      window.setTimeout(() => {
        navigate(getStepById(firstIncomplete.id).path);
        openPageTour();
      }, 0);
      return next;
    });
  }, [navigate, openPageTour]);

  const triggerDemoFill = useCallback(
    (stepId?: OnboardingStepId) => {
      setDemoFillStep(stepId ?? currentStepId);
    },
    [currentStepId],
  );

  const startTutorialWithDemo = useCallback(() => {
    setTutorialMode(true);
    setWelcomeOpen(false);
    persist((prev) => ({ ...prev, welcomeSeen: true, guideActive: true }));
    setDrawerOpen(false);
    const firstStep = ONBOARDING_STEPS[0].id;
    navigate(getStepById(firstStep).path);
    window.setTimeout(() => {
      setDemoFillStep(firstStep);
    }, 350);
  }, [navigate, persist]);

  const notifyTutorialSave = useCallback(
    (stepId: OnboardingStepId, skipped = false) => {
      if (!skipped) {
        persist((prev) => markStepCompleted(prev, stepId));
      }
      refreshProgress();
      if (!tutorialMode) {
        return;
      }
      if (!skipped && tutorialChain?.stepId === stepId) {
        return;
      }
      const nextStepId = getNextStepId(stepId);
      if (!nextStepId) {
        message.success('教程演示流程已全部完成！');
        setTutorialMode(false);
        return;
      }
      window.setTimeout(() => {
        navigate(getStepById(nextStepId).path);
        setDemoFillStep(nextStepId);
      }, 400);
    },
    [tutorialMode, tutorialChain, persist, refreshProgress, navigate],
  );

  const value = useMemo<OnboardingContextValue>(
    () => ({
      storage,
      currentStepId,
      currentStepIndex,
      tourOpen,
      drawerOpen,
      welcomeOpen,
      progressLoading,
      detectedCompleted,
      tutorialMode,
      demoFillStep,
      tutorialChain,
      startGuide,
      startTutorialWithDemo,
      openDrawer: () => setDrawerOpen(true),
      closeDrawer: () => setDrawerOpen(false),
      dismissWelcome: () => {
        persist((prev) => ({ ...prev, welcomeSeen: true }));
        setWelcomeOpen(false);
      },
      goToStep,
      triggerDemoFill,
      clearDemoFill: () => setDemoFillStep(null),
      setTutorialChain,
      notifyTutorialSave,
      openPageTour,
      closePageTour: () => setTourOpen(false),
      markComplete: (stepId) => {
        persist((prev) => markStepCompleted(prev, stepId));
      },
      resetGuide: () => {
        const next: OnboardingStorage = {
          version: 1,
          welcomeSeen: true,
          guideActive: false,
          completedSteps: [],
        };
        persist(next);
        setDetectedCompleted([]);
        setTutorialMode(false);
        setTutorialChain(null);
        setDemoFillStep(null);
        refreshProgress();
      },
    }),
    [
      storage,
      currentStepId,
      currentStepIndex,
      tourOpen,
      drawerOpen,
      welcomeOpen,
      progressLoading,
      detectedCompleted,
      tutorialMode,
      demoFillStep,
      tutorialChain,
      startGuide,
      startTutorialWithDemo,
      goToStep,
      triggerDemoFill,
      notifyTutorialSave,
      openPageTour,
      persist,
      refreshProgress,
    ],
  );

  return <OnboardingContext.Provider value={value}>{children}</OnboardingContext.Provider>;
}

export function useOnboarding(): OnboardingContextValue {
  const context = useContext(OnboardingContext);
  if (!context) {
    throw new Error('useOnboarding must be used within OnboardingProvider');
  }
  return context;
}
