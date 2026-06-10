import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Typography,
  message,
} from 'antd';
import { CloudDownloadOutlined, CodeOutlined, EyeOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import GuidePageShell from '../components/GuidePageShell';
import PageHeader from '../components/PageHeader';
import { buildMetadataFieldDemo, ORDER_FIELDS } from '../guide/demoTutorial';
import { useTutorialDemo } from '../guide/useTutorialDemo';
import MetaTableSyncModal from '../components/MetaTableSyncModal';
import SqlPreviewBlock from '../components/SqlPreviewBlock';
import { metadataApi } from '../api/metadata';
import { datasourceApi } from '../api/datasource';
import type {
  DataSourceResponse,
  DbSchemaInfo,
  MetaTable,
  MetaTableDdlResult,
  MetaTableField,
  QueryResult,
} from '../api/types';

export default function MetadataPage() {
  const [loading, setLoading] = useState(false);
  const [tables, setTables] = useState<MetaTable[]>([]);
  const [datasources, setDatasources] = useState<DataSourceResponse[]>([]);
  const [filterDs, setFilterDs] = useState<string | undefined>();
  const [tableModalOpen, setTableModalOpen] = useState(false);
  const [syncModalOpen, setSyncModalOpen] = useState(false);
  const [fieldModalOpen, setFieldModalOpen] = useState(false);
  const [editingTable, setEditingTable] = useState<MetaTable | null>(null);
  const [editingField, setEditingField] = useState<MetaTableField | null>(null);
  const [insertAfterFieldCode, setInsertAfterFieldCode] = useState<string | null>(null);
  const [currentTableId, setCurrentTableId] = useState<string>('');
  const [fieldsMap, setFieldsMap] = useState<Record<string, MetaTableField[]>>({});
  const [tableForm] = Form.useForm<MetaTable>();
  const [fieldForm] = Form.useForm<MetaTableField>();
  const [previewModalOpen, setPreviewModalOpen] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewTable, setPreviewTable] = useState<MetaTable | null>(null);
  const [previewResult, setPreviewResult] = useState<QueryResult | null>(null);
  const [previewPage, setPreviewPage] = useState({ pageIndex: 1, pageSize: 20 });
  const [previewError, setPreviewError] = useState(false);
  const [ddlModalOpen, setDdlModalOpen] = useState(false);
  const [ddlLoading, setDdlLoading] = useState(false);
  const [ddlExecuting, setDdlExecuting] = useState(false);
  const [ddlSyncExecuting, setDdlSyncExecuting] = useState(false);
  const [ddlTable, setDdlTable] = useState<MetaTable | null>(null);
  const [ddlResult, setDdlResult] = useState<MetaTableDdlResult | null>(null);
  const [schemaOptions, setSchemaOptions] = useState<DbSchemaInfo[]>([]);
  const [schemaLoading, setSchemaLoading] = useState(false);

  const { tutorialChain, setTutorialChain, onSaveSuccess } = useTutorialDemo(
    'metadata',
    async (outcome) => {
      if (outcome.type !== 'form') {
        return;
      }
      setEditingTable(null);
      tableForm.resetFields();
      const values = outcome.values as unknown as MetaTable;
      tableForm.setFieldsValue(values);
      if (values.datasourceId) {
        setFilterDs(values.datasourceId);
        await loadSchemaOptions(values.datasourceId);
      }
      setTableModalOpen(true);
    },
  );

  const loadTables = useCallback(async () => {
    setLoading(true);
    try {
      setTables(await metadataApi.listTables(filterDs));
    } finally {
      setLoading(false);
    }
  }, [filterDs]);

  const loadDatasources = useCallback(async () => {
    setDatasources(await datasourceApi.list());
  }, []);

  useEffect(() => {
    loadDatasources();
  }, [loadDatasources]);

  useEffect(() => {
    loadTables();
  }, [loadTables]);

  useEffect(() => {
    if (!tutorialChain || tutorialChain.kind !== 'metadata-fields') {
      return;
    }
    const field = tutorialChain.fields[tutorialChain.index];
    if (!field) {
      return;
    }
    setCurrentTableId(tutorialChain.tableId);
    setEditingField(null);
    fieldForm.resetFields();
    fieldForm.setFieldsValue(buildMetadataFieldDemo(field));
    setFieldModalOpen(true);
  }, [tutorialChain, fieldForm]);

  const loadFields = async (tableId: string) => {
    const fields = await metadataApi.listFields(tableId);
    setFieldsMap((prev) => ({ ...prev, [tableId]: fields }));
  };

  const loadSchemaOptions = async (datasourceId?: string) => {
    if (!datasourceId) {
      setSchemaOptions([]);
      return;
    }
    setSchemaLoading(true);
    try {
      setSchemaOptions(await datasourceApi.browseSchemas(datasourceId));
    } catch {
      setSchemaOptions([]);
    } finally {
      setSchemaLoading(false);
    }
  };

  const openCreateTable = () => {
    setEditingTable(null);
    tableForm.resetFields();
    tableForm.setFieldsValue({ datasourceId: filterDs });
    setSchemaOptions([]);
    if (filterDs) {
      loadSchemaOptions(filterDs);
    }
    setTableModalOpen(true);
  };

  const openEditTable = (record: MetaTable) => {
    setEditingTable(record);
    tableForm.setFieldsValue(record);
    loadSchemaOptions(record.datasourceId);
    setTableModalOpen(true);
  };

  const handleSaveTable = async () => {
    const values = await tableForm.validateFields();
    if (editingTable?.id) {
      values.id = editingTable.id;
    }
    await metadataApi.saveTable(values);
    message.success('元数据表已保存');
    setTableModalOpen(false);
    await loadTables();
    if (!editingTable) {
      const tables = await metadataApi.listTables(values.datasourceId);
      const saved = tables.find((item) => item.tableCode === values.tableCode);
      if (saved?.id) {
        setTutorialChain({
          kind: 'metadata-fields',
          tableId: saved.id,
          fields: ORDER_FIELDS,
          index: 0,
        });
        return;
      }
    }
    onSaveSuccess();
  };

  const resolveNextSort = (tableId: string) => {
    const fields = fieldsMap[tableId] || [];
    return fields.reduce((max, field) => Math.max(max, field.sort ?? 0), 0) + 1;
  };

  const openCreateField = (tableId: string) => {
    setCurrentTableId(tableId);
    setEditingField(null);
    setInsertAfterFieldCode(null);
    fieldForm.resetFields();
    fieldForm.setFieldsValue({ sort: resolveNextSort(tableId) });
    setFieldModalOpen(true);
  };

  const openCreateFieldAfter = (tableId: string, afterField: MetaTableField) => {
    setCurrentTableId(tableId);
    setEditingField(null);
    setInsertAfterFieldCode(afterField.fieldCode);
    fieldForm.resetFields();
    fieldForm.setFieldsValue({ sort: (afterField.sort ?? 0) + 1 });
    setFieldModalOpen(true);
  };

  const openEditField = (tableId: string, field: MetaTableField) => {
    setCurrentTableId(tableId);
    setEditingField(field);
    setInsertAfterFieldCode(null);
    fieldForm.setFieldsValue(field);
    setFieldModalOpen(true);
  };

  const loadPreview = async (table: MetaTable, pageIndex: number, pageSize: number) => {
    if (!table.id) {
      return;
    }
    setPreviewLoading(true);
    setPreviewError(false);
    try {
      const result = await metadataApi.previewTable(table.id, pageIndex, pageSize);
      setPreviewResult(result);
      setPreviewPage({ pageIndex, pageSize });
    } catch {
      setPreviewResult(null);
      setPreviewError(true);
    } finally {
      setPreviewLoading(false);
    }
  };

  const openPreview = (record: MetaTable) => {
    setPreviewTable(record);
    setPreviewResult(null);
    setPreviewError(false);
    setPreviewPage({ pageIndex: 1, pageSize: 20 });
    setPreviewModalOpen(true);
    loadPreview(record, 1, 20);
  };

  const openDdl = async (record: MetaTable) => {
    if (!record.id) return;
    setDdlTable(record);
    setDdlResult(null);
    setDdlModalOpen(true);
    setDdlLoading(true);
    try {
      setDdlResult(await metadataApi.getCreateTableDdl(record.id));
    } catch {
      setDdlResult(null);
    } finally {
      setDdlLoading(false);
    }
  };

  const handleExecuteDdl = async () => {
    if (!ddlTable?.id) return;
    setDdlExecuting(true);
    try {
      await metadataApi.executeCreateTableDdl(ddlTable.id);
      message.success('建表 SQL 已执行');
      setDdlResult(await metadataApi.getCreateTableDdl(ddlTable.id));
    } finally {
      setDdlExecuting(false);
    }
  };

  const handleExecuteSyncDdl = async (table: MetaTable) => {
    if (!table.id) return;
    setDdlSyncExecuting(true);
    try {
      await metadataApi.executeSyncTableDdl(table.id);
      message.success('增量 DDL 已执行');
      if (ddlTable?.id === table.id) {
        setDdlResult(await metadataApi.getCreateTableDdl(table.id));
      }
      if (previewModalOpen && previewTable?.id === table.id) {
        setPreviewError(false);
        await loadPreview(table, previewPage.pageIndex, previewPage.pageSize);
      }
    } finally {
      setDdlSyncExecuting(false);
    }
  };

  const promptSyncIfNeeded = async (tableId: string) => {
    try {
      const ddl = await metadataApi.getCreateTableDdl(tableId);
      if (ddl.syncNeeded && ddl.missingFieldCodes?.length) {
        Modal.confirm({
          title: '同步物理表字段',
          content: `物理表缺少字段：${ddl.missingFieldCodes.join(', ')}，是否立即执行增量 DDL？`,
          okText: '执行同步',
          cancelText: '稍后',
          onOk: async () => {
            const table = tables.find((t) => t.id === tableId);
            if (table) {
              await handleExecuteSyncDdl(table);
            }
          },
        });
      }
    } catch {
      // ignore
    }
  };

  const handleQuickSync = async (table: MetaTable) => {
    if (!table.id) return;
    try {
      const ddl = await metadataApi.getCreateTableDdl(table.id);
      if (!ddl.tableExists) {
        openDdl(table);
        return;
      }
      if (ddl.syncNeeded) {
        await handleExecuteSyncDdl(table);
        return;
      }
      message.info('物理表字段已是最新');
    } catch {
      openDdl(table);
    }
  };

  const handleSaveField = async () => {
    const values = await fieldForm.validateFields();
    if (editingField?.id) {
      values.id = editingField.id;
    }
    await metadataApi.saveField(currentTableId, values);
    message.success('字段已保存');
    setFieldModalOpen(false);
    setInsertAfterFieldCode(null);
    loadFields(currentTableId);
    if (!editingField?.id) {
      await promptSyncIfNeeded(currentTableId);
    }
    if (tutorialChain?.kind === 'metadata-fields') {
      const nextIndex = tutorialChain.index + 1;
      if (nextIndex < tutorialChain.fields.length) {
        setTutorialChain({
          ...tutorialChain,
          index: nextIndex,
        });
        return;
      }
      setTutorialChain(null);
    }
    onSaveSuccess();
  };

  const tableColumns: ColumnsType<MetaTable> = [
    { title: '表编码', dataIndex: 'tableCode', width: 120 },
    { title: '表名称', dataIndex: 'tableName', width: 140 },
    { title: '目录', dataIndex: 'catalogCode', width: 100 },
    { title: 'Schema', dataIndex: 'schemaCode', width: 100 },
    { title: '数据源', dataIndex: 'datasourceId', width: 120 },
    { title: '备注', dataIndex: 'comments', ellipsis: true },
    {
      title: '操作',
      width: 300,
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => openPreview(record)}
          >
            预览数据
          </Button>
          <Button
            type="link"
            size="small"
            icon={<CodeOutlined />}
            onClick={() => openDdl(record)}
          >
            建表 DDL
          </Button>
          <Button type="link" size="small" onClick={() => openEditTable(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该表及所有字段？"
            onConfirm={async () => {
              await metadataApi.deleteTable(record.id!);
              message.success('已删除');
              loadTables();
            }}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const fieldColumns = (tableId: string): ColumnsType<MetaTableField> => [
    { title: '字段编码', dataIndex: 'fieldCode', width: 140 },
    { title: '字段名称', dataIndex: 'fieldName', width: 140 },
    { title: '类型', dataIndex: 'fieldType', width: 100 },
    { title: '排序', dataIndex: 'sort', width: 70 },
    {
      title: '操作',
      width: 200,
      render: (_, field) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<PlusOutlined />}
            onClick={() => openCreateFieldAfter(tableId, field)}
          >
            后插
          </Button>
          <Button type="link" size="small" onClick={() => openEditField(tableId, field)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该字段？"
            onConfirm={async () => {
              await metadataApi.deleteField(field.id!);
              message.success('已删除');
              loadFields(tableId);
            }}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const previewColumns: ColumnsType<Record<string, unknown>> = (() => {
    const keys = previewResult?.rows?.length
      ? Object.keys(previewResult.rows[0])
      : Object.keys(previewResult?.headers || {});
    return keys.map((key) => ({
      title: previewResult?.headers?.[key] || key,
      dataIndex: key,
      ellipsis: true,
    }));
  })();

  return (
    <>
      <PageHeader
        title="元数据管理"
        description="维护元数据表与字段定义，可按数据源筛选"
      />
      <div className="page-toolbar">
        <div className="page-toolbar-left">
          <Select
            id="guide-filter"
            allowClear
            placeholder="按数据源筛选"
            style={{ width: 220 }}
            value={filterDs}
            onChange={setFilterDs}
            options={datasources.map((d) => ({ label: d.name, value: d.id }))}
          />
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadTables}>
            刷新
          </Button>
          <Button icon={<CloudDownloadOutlined />} onClick={() => setSyncModalOpen(true)}>
            从库同步
          </Button>
          <Button id="guide-primary-action" type="primary" icon={<PlusOutlined />} onClick={openCreateTable}>
            新建元数据表
          </Button>
        </Space>
      </div>
      <GuidePageShell>
        <Table
          rowKey="id"
          loading={loading}
          columns={tableColumns}
          dataSource={tables}
          expandable={{
          onExpand: (expanded, record) => {
            if (expanded && record.id) {
              loadFields(record.id);
            }
          },
          expandedRowRender: (record) => (
            <div style={{ padding: '8px 0' }}>
              <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ fontWeight: 500 }}>字段列表</span>
                <Button
                  size="small"
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => openCreateField(record.id!)}
                >
                  添加字段
                </Button>
              </div>
              <Table
                rowKey="id"
                size="small"
                columns={fieldColumns(record.id!)}
                dataSource={fieldsMap[record.id!] || []}
                pagination={false}
              />
            </div>
          ),
        }}
        pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
        />
      </GuidePageShell>

      <Modal
        title={editingTable ? '编辑元数据表' : '新建元数据表'}
        open={tableModalOpen}
        onCancel={() => setTableModalOpen(false)}
        onOk={handleSaveTable}
        width={560}
      >
        <Form form={tableForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="tableCode" label="表编码" rules={[{ required: true }]}>
            <Input placeholder="如 orders" />
          </Form.Item>
          <Form.Item name="tableName" label="表名称" rules={[{ required: true }]}>
            <Input placeholder="如 订单事实表" />
          </Form.Item>
          <Form.Item name="catalogCode" label="目录编码">
            <Input placeholder="如 finance" />
          </Form.Item>
          <Form.Item name="datasourceId" label="数据源" rules={[{ required: true }]}>
            <Select
              options={datasources.map((d) => ({ label: `${d.name} (${d.id})`, value: d.id }))}
              onChange={(value: string) => {
                tableForm.setFieldValue('schemaCode', undefined);
                loadSchemaOptions(value);
              }}
            />
          </Form.Item>
          <Form.Item
            name="schemaCode"
            label="Schema"
            tooltip="物理库 schema，用于预览、建表 DDL 等；留空则使用数据源默认 schema"
          >
            <Select
              allowClear
              showSearch
              placeholder="选择或留空"
              loading={schemaLoading}
              options={schemaOptions.map((s) => ({ label: s.name, value: s.name }))}
              notFoundContent={schemaLoading ? '加载中...' : '暂无 schema，可留空'}
            />
          </Form.Item>
          <Form.Item name="comments" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <MetaTableSyncModal
        open={syncModalOpen}
        datasources={datasources}
        defaultDatasourceId={filterDs}
        onClose={() => setSyncModalOpen(false)}
        onImported={loadTables}
      />

      <Modal
        title={`数据预览 — ${previewTable?.tableCode || ''}`}
        open={previewModalOpen}
        onCancel={() => setPreviewModalOpen(false)}
        footer={null}
        width={900}
      >
        <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
          数据源: {previewTable?.datasourceId}
          {previewTable?.schemaCode ? ` · Schema: ${previewTable.schemaCode}` : ''} · 表名:{' '}
          {previewTable?.tableName}
        </Typography.Paragraph>
        {previewError && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="数据预览失败，物理表可能尚未创建或缺少新增字段"
            action={
              previewTable ? (
                <Space>
                  <Button size="small" onClick={() => openDdl(previewTable)}>
                    查看 DDL
                  </Button>
                  <Button
                    size="small"
                    type="primary"
                    loading={ddlSyncExecuting}
                    onClick={() => handleQuickSync(previewTable)}
                  >
                    同步字段
                  </Button>
                </Space>
              ) : undefined
            }
          />
        )}
        {previewResult?.sql && (
          <div style={{ marginBottom: 16 }}>
            <Typography.Text strong>预览 SQL</Typography.Text>
            <SqlPreviewBlock sql={previewResult.sql} maxHeight={120} />
          </div>
        )}
        <Table
          rowKey={(_, i) => String(i)}
          size="small"
          loading={previewLoading}
          columns={previewColumns}
          dataSource={previewResult?.rows || []}
          scroll={{ x: true }}
          locale={{ emptyText: previewLoading ? '加载中...' : '暂无数据' }}
          pagination={{
            current: previewPage.pageIndex,
            pageSize: previewPage.pageSize,
            total: previewResult?.total ?? 0,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, pageSize) => {
              if (previewTable) {
                loadPreview(previewTable, page, pageSize);
              }
            },
          }}
        />
      </Modal>

      <Modal
        title={`建表 DDL — ${ddlTable?.tableCode || ''}`}
        open={ddlModalOpen}
        onCancel={() => setDdlModalOpen(false)}
        footer={
          <Space>
            <Button onClick={() => setDdlModalOpen(false)}>关闭</Button>
            {ddlResult?.syncNeeded && ddlTable && (
              <Popconfirm
                title="确认执行增量 DDL？"
                description={`将为物理表添加字段：${ddlResult.missingFieldCodes?.join(', ')}`}
                onConfirm={() => handleExecuteSyncDdl(ddlTable)}
                disabled={ddlLoading}
              >
                <Button type="primary" loading={ddlSyncExecuting} disabled={ddlLoading}>
                  执行增量同步
                </Button>
              </Popconfirm>
            )}
            <Popconfirm
              title={`确认在数据源 ${ddlResult?.datasourceId || ''} 执行建表？`}
              description="将在目标数据库创建物理表，请确认 DDL 无误。"
              onConfirm={handleExecuteDdl}
              disabled={!ddlResult || ddlResult.tableExists || ddlLoading}
            >
              <Button
                loading={ddlExecuting}
                disabled={!ddlResult || ddlResult.tableExists || ddlLoading}
              >
                执行建表
              </Button>
            </Popconfirm>
          </Space>
        }
        width={800}
        styles={{ body: { maxHeight: '70vh', overflow: 'auto' } }}
      >
        <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
          数据源: {ddlResult?.datasourceId || ddlTable?.datasourceId}
          {ddlTable?.schemaCode ? ` · Schema: ${ddlTable.schemaCode}` : ''} · 物理表:{' '}
          {ddlResult?.tableCode || ddlTable?.tableCode}
        </Typography.Paragraph>
        {ddlResult?.tableExists && !ddlResult.syncNeeded && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message="物理表已存在且字段已同步，无需重复建表"
          />
        )}
        {ddlResult?.syncNeeded && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message={`物理表缺少 ${ddlResult.missingFieldCodes?.length ?? 0} 个字段，可执行增量同步`}
            description={`待添加：${ddlResult.missingFieldCodes?.join(', ')}`}
          />
        )}
        {ddlLoading ? (
          <Typography.Text type="secondary">加载 DDL 中...</Typography.Text>
        ) : (
          <>
            {ddlResult?.alterDdl && (
              <div style={{ marginBottom: 16 }}>
                <Typography.Text strong>增量 DDL</Typography.Text>
                <SqlPreviewBlock sql={ddlResult.alterDdl} />
              </div>
            )}
            {ddlResult?.ddl && (
              <div>
                <Typography.Text strong>
                  {ddlResult.tableExists ? '全量建表 DDL（参考）' : '建表 DDL'}
                </Typography.Text>
                <SqlPreviewBlock sql={ddlResult.ddl} />
              </div>
            )}
            {!ddlResult?.ddl && !ddlResult?.alterDdl && (
              <Typography.Text type="secondary">暂无 DDL，请先配置字段定义</Typography.Text>
            )}
          </>
        )}
      </Modal>

      <Modal
        title={
          editingField
            ? '编辑字段'
            : insertAfterFieldCode
              ? `新建字段 — 在 ${insertAfterFieldCode} 之后`
              : '新建字段'
        }
        open={fieldModalOpen}
        onCancel={() => {
          setFieldModalOpen(false);
          setInsertAfterFieldCode(null);
        }}
        onOk={handleSaveField}
        width={480}
      >
        {insertAfterFieldCode && !editingField && (
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            新字段将插入在 <Typography.Text code>{insertAfterFieldCode}</Typography.Text>{' '}
            之后，其后字段排序自动顺延
          </Typography.Paragraph>
        )}
        <Form form={fieldForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="fieldCode" label="字段编码" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="fieldName" label="字段名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="fieldType" label="字段类型" rules={[{ required: true }]}>
            <Select
              options={['VARCHAR', 'INTEGER', 'DECIMAL', 'DATE', 'TIMESTAMP', 'BOOLEAN'].map(
                (t) => ({ label: t, value: t }),
              )}
            />
          </Form.Item>
          <Form.Item
            name="sort"
            label="排序"
            tooltip="后插字段时自动计算；也可手动指定插入位置"
          >
            <Input type="number" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
