export type OnboardingStepId =
  | 'datasources'
  | 'metadata'
  | 'dimensions'
  | 'metrics'
  | 'warning-rules'
  | 'dashboards';

export interface OnboardingStep {
  id: OnboardingStepId;
  path: string;
  menuLabel: string;
  title: string;
  summary: string;
  tips: string[];
}

export interface OnboardingStorage {
  version: 1;
  welcomeSeen: boolean;
  guideActive: boolean;
  completedSteps: OnboardingStepId[];
}
