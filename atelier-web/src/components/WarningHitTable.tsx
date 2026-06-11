import { useMemo } from 'react';
import { ExclamationCircleFilled } from '@ant-design/icons';
import { Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { WarningRuleType } from '../api/types';
import {
  buildWarningPreviewColumnKeys,
  getWarningPreviewHeader,
  inferWarningRuleType,
  isWarningLlmColumn,
  isWarningReasonColumn,
  isWarningTriggerColumn,
} from '../utils/warningPreviewTable';

interface WarningHitTableProps {
  rows: Record<string, unknown>[];
  headers?: Record<string, string>;
  loading?: boolean;
  ruleType?: WarningRuleType | string;
  keywordOnly?: boolean;
  metricCodes?: string[];
  pagination?: false | {
    current: number;
    pageSize: number;
    total: number;
    onChange: (pageIndex: number, pageSize: number) => void;
  };
}

function isHitSourceCell(key: string, record: Record<string, unknown>): boolean {
  if (!record._triggered) {
    return false;
  }
  if (record[`_semanticCheck.${key}`] === true) {
    return true;
  }
  const matchReason = record[`_matchReason.${key}`];
  return matchReason != null && matchReason !== '';
}

function renderTriggerTag(value: unknown) {
  return value ? (
    <Tag color="error" className="warning-trigger-tag">
      <ExclamationCircleFilled style={{ marginRight: 4 }} />
      是
    </Tag>
  ) : (
    <Tag color="default">否</Tag>
  );
}

function buildColumns(
  headers: Record<string, string> | undefined,
  rows: Record<string, unknown>[],
  options: {
    ruleType?: WarningRuleType | string;
    keywordOnly?: boolean;
    metricCodes?: string[];
  },
) {
  const rowKeys = rows.length ? Object.keys(rows[0]) : Object.keys(headers || {});
  const orderedKeys = buildWarningPreviewColumnKeys(rowKeys, {
    ruleType: options.ruleType || inferWarningRuleType(rowKeys),
    metricCodes: options.metricCodes,
    keywordOnly: options.keywordOnly,
    rows,
  });

  return orderedKeys.map((key) => ({
    key,
    dataIndex: key,
    title: getWarningPreviewHeader(key, headers),
    width: key === '_triggered' ? 96 : undefined,
    ellipsis: key !== '_triggered',
    render: (value: unknown, record: Record<string, unknown>) => {
      if (isWarningTriggerColumn(key)) {
        return renderTriggerTag(value);
      }
      if (isWarningLlmColumn(key)) {
        return value ? (
          <Tag color="processing">是</Tag>
        ) : (
          <Tag color="default">否</Tag>
        );
      }
      if (isWarningReasonColumn(key)) {
        if (!value) {
          return '-';
        }
        return <span className="warning-cell-hit-reason">{String(value)}</span>;
      }
      if (value == null || value === '') {
        return '-';
      }
      if (typeof value === 'object') {
        return JSON.stringify(value);
      }
      const text = String(value);
      if (isHitSourceCell(key, record)) {
        return <span className="warning-cell-hit">{text}</span>;
      }
      return text;
    },
  }));
}

export default function WarningHitTable({
  rows,
  headers,
  loading = false,
  ruleType,
  keywordOnly,
  metricCodes,
  pagination = false,
}: WarningHitTableProps) {
  const columns: ColumnsType<Record<string, unknown>> = useMemo(
    () => buildColumns(headers, rows, { ruleType, keywordOnly, metricCodes }),
    [headers, rows, ruleType, keywordOnly, metricCodes],
  );

  return (
    <Table
      rowKey={(_, index) => String(index)}
      size="small"
      loading={loading}
      columns={columns}
      dataSource={rows}
      scroll={{ x: true }}
      rowClassName={(record) => (record._triggered ? 'warning-row-triggered' : '')}
      locale={{ emptyText: '当前页无命中行' }}
      pagination={
        pagination === false
          ? false
          : {
              current: pagination.current,
              pageSize: pagination.pageSize,
              total: pagination.total,
              showSizeChanger: true,
              pageSizeOptions: ['10', '20', '50', '100'],
              showTotal: (total) => `全表共 ${total} 条`,
              onChange: pagination.onChange,
            }
      }
    />
  );
}
