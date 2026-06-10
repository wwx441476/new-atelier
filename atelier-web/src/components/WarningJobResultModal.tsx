import { useCallback, useEffect, useMemo, useState } from 'react';
import { Modal, Switch, Typography } from 'antd';
import { warningApi } from '../api/warning';
import type { WarningRuleJob, WarningRulePreviewResult } from '../api/types';
import { formatWarningJobSummary } from '../utils/warningJobSummary';
import { filterMatchedRows } from '../utils/warningRows';
import SqlPreviewBlock from './SqlPreviewBlock';
import WarningHitTable from './WarningHitTable';

interface WarningJobResultModalProps {
  open: boolean;
  jobId: string | null;
  defaultMatchedOnly?: boolean;
  onClose: () => void;
}

export default function WarningJobResultModal({
  open,
  jobId,
  defaultMatchedOnly = true,
  onClose,
}: WarningJobResultModalProps) {
  const [loading, setLoading] = useState(false);
  const [pageLoading, setPageLoading] = useState(false);
  const [job, setJob] = useState<WarningRuleJob | null>(null);
  const [result, setResult] = useState<WarningRulePreviewResult | null>(null);
  const [page, setPage] = useState({ pageIndex: 1, pageSize: 20 });
  const [matchedOnly, setMatchedOnly] = useState(defaultMatchedOnly);

  const loadPage = useCallback(
    async (ruleId: string, pageIndex: number, pageSize: number, keywordOnly: boolean) => {
      setPageLoading(true);
      try {
        const next = await warningApi.previewRule(ruleId, {
          pageIndex,
          pageSize,
          keywordOnly,
        });
        setResult(next);
        setPage({ pageIndex, pageSize });
      } finally {
        setPageLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    if (!open || !jobId) {
      return;
    }
    setLoading(true);
    setMatchedOnly(defaultMatchedOnly);
    warningApi
      .getJob(jobId)
      .then((loaded) => {
        setJob(loaded);
        setResult(loaded.result ?? null);
        const pageIndex = loaded.params?.pageIndex ?? 1;
        const pageSize = loaded.params?.pageSize ?? 20;
        setPage({ pageIndex, pageSize });
      })
      .finally(() => setLoading(false));
  }, [open, jobId, defaultMatchedOnly]);

  const displayRows = useMemo(() => {
    const rows = result?.rows || [];
    return matchedOnly ? filterMatchedRows(rows) : rows;
  }, [result, matchedOnly]);

  const summaryText = formatWarningJobSummary({
    total: result?.total ?? job?.total,
    matchedCount: result?.matchedCount ?? job?.matchedCount,
    pageIndex: page.pageIndex,
    pageSize: page.pageSize,
  });

  return (
    <Modal
      title={`预警命中数据 — ${job?.ruleName || job?.ruleCode || ''}`}
      open={open}
      onCancel={onClose}
      footer={null}
      width={900}
      destroyOnClose
    >
      {job?.status === 'FAILED' && (
        <Typography.Text type="danger">{job.errorMessage || '任务执行失败'}</Typography.Text>
      )}
      {result && (
        <>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
            {result.expression}
          </Typography.Paragraph>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
            {summaryText}
          </Typography.Paragraph>
          <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 12 }}>
            <Switch
              checked={matchedOnly}
              checkedChildren="仅命中"
              unCheckedChildren="全部行"
              onChange={setMatchedOnly}
            />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {matchedOnly
                ? `当前展示 ${displayRows.length} 条命中行；可切换查看本页全部数据`
                : '预览按页展示；翻页将重新加载对应页数据'}
            </Typography.Text>
          </div>
          {result.sql && !matchedOnly && (
            <div style={{ marginBottom: 16 }}>
              <Typography.Text strong>预警 SQL</Typography.Text>
              <SqlPreviewBlock sql={result.sql} maxHeight={160} />
            </div>
          )}
          <WarningHitTable
            rows={displayRows}
            headers={result.headers}
            loading={loading || pageLoading}
            pagination={
              matchedOnly
                ? false
                : {
                    current: page.pageIndex,
                    pageSize: page.pageSize,
                    total: result.total ?? 0,
                    onChange: (pageIndex, pageSize) => {
                      if (job?.ruleId) {
                        loadPage(job.ruleId, pageIndex, pageSize, job.params?.keywordOnly ?? true);
                      }
                    },
                  }
            }
          />
        </>
      )}
      {!result && !loading && job?.status !== 'FAILED' && (
        <Typography.Text type="secondary">暂无结果数据</Typography.Text>
      )}
    </Modal>
  );
}
