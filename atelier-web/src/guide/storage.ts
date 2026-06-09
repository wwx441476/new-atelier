import type { OnboardingStorage, OnboardingStepId } from './types';

const STORAGE_KEY = 'atelier-onboarding-v1';

const DEFAULT_STORAGE: OnboardingStorage = {
  version: 1,
  welcomeSeen: false,
  guideActive: false,
  completedSteps: [],
};

export function loadOnboardingStorage(): OnboardingStorage {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return { ...DEFAULT_STORAGE };
    }
    const parsed = JSON.parse(raw) as Partial<OnboardingStorage>;
    return {
      ...DEFAULT_STORAGE,
      ...parsed,
      completedSteps: parsed.completedSteps ?? [],
    };
  } catch {
    return { ...DEFAULT_STORAGE };
  }
}

export function saveOnboardingStorage(state: OnboardingStorage): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

export function markStepCompleted(
  state: OnboardingStorage,
  stepId: OnboardingStepId,
): OnboardingStorage {
  if (state.completedSteps.includes(stepId)) {
    return state;
  }
  return {
    ...state,
    completedSteps: [...state.completedSteps, stepId],
  };
}
