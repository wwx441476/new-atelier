const STORAGE_KEY = 'atelier.copilot.llmProfileId';

export function readCopilotLlmProfileId(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function writeCopilotLlmProfileId(profileId: string | null) {
  try {
    if (!profileId) {
      localStorage.removeItem(STORAGE_KEY);
      return;
    }
    localStorage.setItem(STORAGE_KEY, profileId);
  } catch {
    // ignore storage errors
  }
}
