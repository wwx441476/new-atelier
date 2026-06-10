import type { WarningRuleJobEventPayload } from '../api/types';

export interface WarningJobEventHandlers {
  onSubmitted?: (payload: WarningRuleJobEventPayload) => void;
  onProgress?: (payload: WarningRuleJobEventPayload) => void;
  onCompleted?: (payload: WarningRuleJobEventPayload) => void;
  onFailed?: (payload: WarningRuleJobEventPayload) => void;
}

function parsePayload(event: MessageEvent): WarningRuleJobEventPayload {
  return JSON.parse(event.data) as WarningRuleJobEventPayload;
}

export function subscribeWarningJob(jobId: string, handlers: WarningJobEventHandlers): EventSource {
  const es = new EventSource(`/api/v2/warning/jobs/${jobId}/events`);

  es.addEventListener('submitted', (event) => {
    handlers.onSubmitted?.(parsePayload(event));
  });
  es.addEventListener('progress', (event) => {
    handlers.onProgress?.(parsePayload(event));
  });
  es.addEventListener('completed', (event) => {
    handlers.onCompleted?.(parsePayload(event));
    es.close();
  });
  es.addEventListener('failed', (event) => {
    handlers.onFailed?.(parsePayload(event));
    es.close();
  });

  es.onerror = () => {
    es.close();
  };

  return es;
}

export function subscribeGlobalWarningNotifications(
  handlers: Pick<WarningJobEventHandlers, 'onCompleted' | 'onFailed'>,
): EventSource {
  const es = new EventSource('/api/v2/warning/notifications/stream');

  es.addEventListener('job_completed', (event) => {
    handlers.onCompleted?.(parsePayload(event));
  });
  es.addEventListener('job_failed', (event) => {
    handlers.onFailed?.(parsePayload(event));
  });

  es.onerror = () => {
    es.close();
  };

  return es;
}
