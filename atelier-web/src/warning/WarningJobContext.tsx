import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { message, notification } from 'antd';
import WarningJobResultModal from '../components/WarningJobResultModal';
import { warningApi } from '../api/warning';
import { subscribeGlobalWarningNotifications } from '../utils/warningJobEvents';
import { formatWarningJobSummary } from '../utils/warningJobSummary';

interface OpenJobResultOptions {
  matchedOnly?: boolean;
}

interface WarningJobContextValue {
  openJobResult: (jobId: string, options?: OpenJobResultOptions) => void;
}

const WarningJobContext = createContext<WarningJobContextValue | null>(null);

export function WarningJobProvider({ children }: { children: ReactNode }) {
  const [modalOpen, setModalOpen] = useState(false);
  const [activeJobId, setActiveJobId] = useState<string | null>(null);
  const [defaultMatchedOnly, setDefaultMatchedOnly] = useState(true);

  const openJobResult = useCallback((jobId: string, options?: OpenJobResultOptions) => {
    setActiveJobId(jobId);
    setDefaultMatchedOnly(options?.matchedOnly ?? true);
    setModalOpen(true);
  }, []);

  useEffect(() => {
    const es = subscribeGlobalWarningNotifications({
      onCompleted: (payload) => {
        const summary = formatWarningJobSummary({
          total: payload.total,
          matchedCount: payload.matchedCount,
          pageIndex: payload.pageIndex,
          pageSize: payload.pageSize,
        });
        notification.success({
          message: '预警任务已完成',
          description: `${payload.ruleName || payload.ruleCode || '预警规则'}：${summary}`,
          duration: 8,
          onClick: () => openJobResult(payload.jobId, { matchedOnly: true }),
        });
      },
      onFailed: (payload) => {
        notification.error({
          message: '预警任务失败',
          description: payload.errorMessage || `${payload.ruleName || payload.ruleCode || '预警规则'} 执行失败`,
          duration: 8,
        });
      },
    });
    return () => es.close();
  }, [openJobResult]);

  useEffect(() => {
    warningApi.listJobs(['PENDING', 'RUNNING'], 5).then((jobs) => {
      if (jobs.length > 0) {
        message.info('有预警任务正在后台执行，完成后将通知您');
      }
    });
  }, []);

  const value = useMemo(() => ({ openJobResult }), [openJobResult]);

  return (
    <WarningJobContext.Provider value={value}>
      {children}
      <WarningJobResultModal
        open={modalOpen}
        jobId={activeJobId}
        defaultMatchedOnly={defaultMatchedOnly}
        onClose={() => {
          setModalOpen(false);
          setActiveJobId(null);
        }}
      />
    </WarningJobContext.Provider>
  );
}

export function useWarningJob() {
  const context = useContext(WarningJobContext);
  if (!context) {
    throw new Error('useWarningJob must be used within WarningJobProvider');
  }
  return context;
}
