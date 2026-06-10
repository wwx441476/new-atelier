import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Typography,
  message,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import CreateTableModal from './CreateTableModal';
import SqlPreviewBlock from './SqlPreviewBlock';
import { datasourceApi } from '../api/datasource';
import type {
  DataSourceResponse,
  DbBrowseQueryRequest,
  DbColumnInfo,
  DbSchemaInfo,
  DbTableInfo,
  FilterGroupDto,
  QueryResult,
  SqlExecuteResult,
} from '../api/types';

interface DatabaseBrowseModalProps {
  datasource: DataSourceResponse | null;
  open: boolean;
  onClose: () => void;
}

type FilterGroupForm = {
  conditions?: Array<{ field?: string; operator?: string; values?: string }>;
};

function buildFilterRequest(filterGroups: FilterGroupForm[]): Pick<DbBrowseQueryRequest, 'filters' | 'filterGroups'> {
  const groups = (filterGroups || [])
    .map((group) => ({
      conditions: (group.conditions || [])
        .filter((c) => c.field && c.values)
        .map((c) => ({
          field: c.field!,
          operator: c.operator || 'IN',
          values: c.values!.split(',').map((v) => v.trim()).filter(Boolean),
        })),
    }))
    .filter((group) => group.conditions.length > 0);
  const flatFilters = groups.length === 1 ? groups[0].conditions : undefined;
  return {
    filterGroups: groups.length > 1 ? (groups as FilterGroupDto[]) : undefined,
    filters: flatFilters,
  };
}

function qualifyTable(schema: string | undefined, table: string): string {
  return schema ? `${schema}.${table}` : table;
}

function QueryResultPanel({
  result,
  loading,
  page,
  onPageChange,
}: {
  result: QueryResult | null;
  loading: boolean;
  page: { pageIndex: number; pageSize: number };
  onPageChange: (pageIndex: number, pageSize: number) => void;
}) {
  const columns: ColumnsType<Record<string, unknown>> = useMemo(() => {
    const keys = result?.rows?.length
      ? Object.keys(result.rows[0])
      : Object.keys(result?.headers || {});
    return keys.map((key) => ({
      title: result?.headers?.[key] || key,
      dataIndex: key,
      ellipsis: true,
    }));
  }, [result]);

  return (
    <Spin spinning={loading}>
      {result?.sql && (
        <div style={{ marginBottom: 12 }}>
          <SqlPreviewBlock sql={result.sql} maxHeight={120} />
        </div>
      )}
      <Table
        rowKey={(_, index) => String(index)}
        size="small"
        columns={columns}
        dataSource={result?.rows || []}
        scroll={{ x: true }}
        locale={{ emptyText: loading ? '查询中...' : '暂无数据' }}
        pagination={{
          current: page.pageIndex,
          pageSize: page.pageSize,
          total: result?.total ?? 0,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
          onChange: onPageChange,
        }}
      />
    </Spin>
  );
}

export default function DatabaseBrowseModal({
  datasource,
  open,
  onClose,
}: DatabaseBrowseModalProps) {
  const [schemasLoading, setSchemasLoading] = useState(false);
  const [tablesLoading, setTablesLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [queryLoading, setQueryLoading] = useState(false);
  const [schemas, setSchemas] = useState<DbSchemaInfo[]>([]);
  const [tables, setTables] = useState<DbTableInfo[]>([]);
  const [columns, setColumns] = useState<DbColumnInfo[]>([]);
  const [previewResult, setPreviewResult] = useState<QueryResult | null>(null);
  const [filterResult, setFilterResult] = useState<QueryResult | null>(null);
  const [sqlResult, setSqlResult] = useState<QueryResult | null>(null);
  const [selectedSchema, setSelectedSchema] = useState<string>();
  const [selectedTable, setSelectedTable] = useState<DbTableInfo | null>(null);
  const [previewPage, setPreviewPage] = useState({ pageIndex: 1, pageSize: 20 });
  const [filterPage, setFilterPage] = useState({ pageIndex: 1, pageSize: 20 });
  const [sqlPage, setSqlPage] = useState({ pageIndex: 1, pageSize: 20 });
  const [customSql, setCustomSql] = useState('');
  const [writeSql, setWriteSql] = useState('');
  const [writeResult, setWriteResult] = useState<SqlExecuteResult | null>(null);
  const [createTableOpen, setCreateTableOpen] = useState(false);
  const [activeTab, setActiveTab] = useState('preview');
  const [filterForm] = Form.useForm();

  const resetState = useCallback(() => {
    setSchemas([]);
    setTables([]);
    setColumns([]);
    setPreviewResult(null);
    setFilterResult(null);
    setSqlResult(null);
    setSelectedSchema(undefined);
    setSelectedTable(null);
    setPreviewPage({ pageIndex: 1, pageSize: 20 });
    setFilterPage({ pageIndex: 1, pageSize: 20 });
    setSqlPage({ pageIndex: 1, pageSize: 20 });
    setCustomSql('');
    setWriteSql('');
    setWriteResult(null);
    setCreateTableOpen(false);
    setActiveTab('preview');
  }, []);

  const loadTables = useCallback(async (datasourceId: string, schema?: string) => {
    setTablesLoading(true);
    try {
      setTables(await datasourceApi.browseTables(datasourceId, schema));
    } catch {
      setTables([]);
    } finally {
      setTablesLoading(false);
    }
  }, []);

  const loadTableColumns = useCallback(
    async (datasourceId: string, table: DbTableInfo) => {
      const schema = table.schema || selectedSchema;
      try {
        setColumns(await datasourceApi.browseColumns(datasourceId, table.name, schema));
      } catch {
        setColumns([]);
      }
    },
    [selectedSchema],
  );

  const loadPreview = useCallback(
    async (datasourceId: string, table: DbTableInfo, pageIndex: number, pageSize: number) => {
      setDetailLoading(true);
      try {
        const schema = table.schema || selectedSchema;
        setPreviewResult(
          await datasourceApi.browsePreview(datasourceId, table.name, schema, pageIndex, pageSize),
        );
        setPreviewPage({ pageIndex, pageSize });
      } catch {
        setPreviewResult(null);
      } finally {
        setDetailLoading(false);
      }
    },
    [selectedSchema],
  );

  const initTableContext = useCallback(
    async (datasourceId: string, table: DbTableInfo) => {
      const schema = table.schema || selectedSchema;
      const qualified = qualifyTable(schema, table.name);
      setCustomSql(`SELECT * FROM ${qualified}`);
      setWriteSql(
        `-- 支持 INSERT / UPDATE / DELETE / CREATE TABLE / ALTER TABLE / DROP TABLE\n` +
          `-- INSERT INTO ${qualified} (col1, col2) VALUES ('v1', 100);\n` +
          `-- UPDATE ${qualified} SET col1 = 'v2' WHERE id = '...';\n` +
          `-- DELETE FROM ${qualified} WHERE id = '...';`,
      );
      setWriteResult(null);
      filterForm.setFieldsValue({
        filterGroups: [
          {
            conditions: [
              {
                field: undefined,
                operator: 'IN',
                values: '',
              },
            ],
          },
        ],
      });
      setFilterResult(null);
      setSqlResult(null);
      setFilterPage({ pageIndex: 1, pageSize: 20 });
      setSqlPage({ pageIndex: 1, pageSize: 20 });
      await Promise.all([
        loadTableColumns(datasourceId, table),
        loadPreview(datasourceId, table, 1, previewPage.pageSize),
      ]);
    },
    [filterForm, loadPreview, loadTableColumns, previewPage.pageSize, selectedSchema],
  );

  useEffect(() => {
    if (!open || !datasource) {
      resetState();
      return;
    }

    let cancelled = false;
    setSchemasLoading(true);
    datasourceApi
      .browseSchemas(datasource.id)
      .then(async (schemaList) => {
        if (cancelled) return;
        setSchemas(schemaList);
        const defaultSchema = schemaList[0]?.name;
        setSelectedSchema(defaultSchema);
        if (defaultSchema) {
          await loadTables(datasource.id, defaultSchema);
        }
      })
      .catch(() => {
        if (!cancelled) setSchemas([]);
      })
      .finally(() => {
        if (!cancelled) setSchemasLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [open, datasource, loadTables, resetState]);

  const handleSchemaSelect = async (schema: string) => {
    if (!datasource || schema === selectedSchema) return;
    setSelectedSchema(schema);
    setSelectedTable(null);
    setColumns([]);
    setPreviewResult(null);
    setFilterResult(null);
    setSqlResult(null);
    await loadTables(datasource.id, schema);
  };

  const handleTableSelect = async (table: DbTableInfo) => {
    if (!datasource) return;
    setSelectedTable(table);
    setActiveTab('preview');
    await initTableContext(datasource.id, table);
  };

  const handleFilterQuery = async (pageIndex = 1, pageSize = filterPage.pageSize) => {
    if (!datasource || !selectedTable) return;
    const values = await filterForm.validateFields();
    const schema = selectedTable.schema || selectedSchema;
    const filterRequest = buildFilterRequest(values.filterGroups || []);
    setQueryLoading(true);
    try {
      const result = await datasourceApi.browseTableQuery(
        datasource.id,
        selectedTable.name,
        schema,
        { ...filterRequest, pageIndex, pageSize },
      );
      setFilterResult(result);
      setFilterPage({ pageIndex, pageSize });
    } catch {
      setFilterResult(null);
    } finally {
      setQueryLoading(false);
    }
  };

  const handleSqlQuery = async (pageIndex = 1, pageSize = sqlPage.pageSize) => {
    if (!datasource || !customSql.trim()) return;
    setQueryLoading(true);
    try {
      const result = await datasourceApi.browseExecuteSql(datasource.id, {
        sql: customSql.trim(),
        pageIndex,
        pageSize,
      });
      setSqlResult(result);
      setSqlPage({ pageIndex, pageSize });
    } catch {
      setSqlResult(null);
    } finally {
      setQueryLoading(false);
    }
  };

  const handleWriteSql = async () => {
    if (!datasource || !writeSql.trim()) return;
    setQueryLoading(true);
    try {
      const result = await datasourceApi.browseExecuteWriteSql(datasource.id, {
        sql: writeSql.trim(),
      });
      setWriteResult(result);
      message.success(result.message || '执行成功');
      if (
        result.statementType === 'INSERT' ||
        result.statementType === 'UPDATE' ||
        result.statementType === 'DELETE' ||
        result.statementType === 'CREATE TABLE'
      ) {
        if (selectedSchema) {
          await loadTables(datasource.id, selectedSchema);
        }
        if (selectedTable) {
          await loadPreview(datasource.id, selectedTable, 1, previewPage.pageSize);
        }
      }
    } catch (err) {
      setWriteResult(null);
      message.error(err instanceof Error ? err.message : '执行失败');
    } finally {
      setQueryLoading(false);
    }
  };

  const handleCreateTableSuccess = async () => {
    if (!datasource || !selectedSchema) return;
    await loadTables(datasource.id, selectedSchema);
  };

  const fieldOptions = useMemo(
    () => columns.map((c) => ({ label: c.name, value: c.name })),
    [columns],
  );

  const columnTableColumns: ColumnsType<DbColumnInfo> = [
    { title: '字段名', dataIndex: 'name', width: 140 },
    { title: '类型', dataIndex: 'typeName', width: 120 },
    {
      title: '长度',
      width: 80,
      render: (_, record) => record.columnSize ?? '-',
    },
    {
      title: '精度',
      width: 80,
      render: (_, record) => record.decimalDigits ?? '-',
    },
    {
      title: '可空',
      width: 70,
      render: (_, record) => (record.nullable == null ? '-' : record.nullable ? '是' : '否'),
    },
    { title: '备注', dataIndex: 'remarks', ellipsis: true },
  ];

  const filterGroupsUi = (
    <Form form={filterForm} layout="vertical">
      <Typography.Paragraph type="secondary" style={{ marginBottom: 12, fontSize: 13 }}>
        组内条件以「且」组合，多个条件组之间以「或」组合
      </Typography.Paragraph>
      <Form.List name="filterGroups">
        {(groups, { add: addGroup, remove: removeGroup }) => (
          <>
            {groups.map((group, groupIndex) => (
              <div key={group.key}>
                {groupIndex > 0 && (
                  <div style={{ textAlign: 'center', margin: '8px 0', color: '#1677ff', fontWeight: 500 }}>
                    或
                  </div>
                )}
                <div
                  style={{
                    border: '1px solid #d9d9d9',
                    borderRadius: 6,
                    padding: 12,
                    marginBottom: 8,
                    background: '#fafafa',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                    <Typography.Text type="secondary">条件组 {groupIndex + 1}</Typography.Text>
                    {groups.length > 1 && (
                      <Button type="link" danger size="small" onClick={() => removeGroup(group.name)}>
                        删除条件组
                      </Button>
                    )}
                  </div>
                  <Form.List name={[group.name, 'conditions']}>
                    {(conditions, { add: addCond, remove: removeCond }) => (
                      <>
                        {conditions.map((cond, condIndex) => (
                          <div key={cond.key}>
                            {condIndex > 0 && (
                              <Typography.Text type="secondary" style={{ display: 'block', margin: '4px 0' }}>
                                且
                              </Typography.Text>
                            )}
                            <Space align="baseline" wrap>
                              <Form.Item {...cond} name={[cond.name, 'field']} label={condIndex === 0 ? '字段' : ''}>
                                <Select
                                  placeholder="选择字段"
                                  style={{ width: 150 }}
                                  showSearch
                                  options={fieldOptions}
                                />
                              </Form.Item>
                              <Form.Item {...cond} name={[cond.name, 'operator']} label={condIndex === 0 ? '运算符' : ''}>
                                <Select
                                  style={{ width: 100 }}
                                  options={['IN', 'EQ', 'GT', 'LT', 'GTE', 'LTE', 'LIKE'].map((o) => ({
                                    label: o,
                                    value: o,
                                  }))}
                                />
                              </Form.Item>
                              <Form.Item
                                {...cond}
                                name={[cond.name, 'values']}
                                label={condIndex === 0 ? '值（逗号分隔）' : ''}
                              >
                                <Input placeholder="001,002" style={{ width: 160 }} />
                              </Form.Item>
                              <Button type="link" danger onClick={() => removeCond(cond.name)}>
                                删除
                              </Button>
                            </Space>
                          </div>
                        ))}
                        <Button type="dashed" size="small" onClick={() => addCond({ operator: 'IN', values: '' })}>
                          添加条件
                        </Button>
                      </>
                    )}
                  </Form.List>
                </div>
              </div>
            ))}
            <Space style={{ marginBottom: 12 }}>
              <Button type="dashed" onClick={() => addGroup({ conditions: [{ operator: 'IN', values: '' }] })}>
                添加条件组
              </Button>
              <Button type="primary" loading={queryLoading} onClick={() => handleFilterQuery()}>
                执行筛选查询
              </Button>
            </Space>
          </>
        )}
      </Form.List>
      <QueryResultPanel
        result={filterResult}
        loading={queryLoading}
        page={filterPage}
        onPageChange={(pageIndex, pageSize) => handleFilterQuery(pageIndex, pageSize)}
      />
    </Form>
  );

  const sqlQueryUi = (
    <div>
      <Input.TextArea
        rows={5}
        value={customSql}
        onChange={(e) => setCustomSql(e.target.value)}
        placeholder="SELECT * FROM schema.table WHERE ..."
        style={{ fontFamily: 'monospace', marginBottom: 12 }}
      />
      <Space style={{ marginBottom: 12 }}>
        <Button type="primary" loading={queryLoading} onClick={() => handleSqlQuery()}>
          执行查询
        </Button>
        <Typography.Text type="secondary">仅支持 SELECT 查询</Typography.Text>
      </Space>
      <QueryResultPanel
        result={sqlResult}
        loading={queryLoading}
        page={sqlPage}
        onPageChange={(pageIndex, pageSize) => handleSqlQuery(pageIndex, pageSize)}
      />
    </div>
  );

  const writeSqlUi = (
    <div>
      <Input.TextArea
        rows={8}
        value={writeSql}
        onChange={(e) => setWriteSql(e.target.value)}
        placeholder="INSERT INTO schema.table (...) VALUES (...);"
        style={{ fontFamily: 'monospace', marginBottom: 12 }}
      />
      <Space style={{ marginBottom: 12 }}>
        <Button type="primary" loading={queryLoading} onClick={handleWriteSql}>
          执行写入
        </Button>
        <Typography.Text type="secondary">
          支持 INSERT / UPDATE / DELETE / CREATE TABLE / ALTER TABLE / DROP TABLE
        </Typography.Text>
      </Space>
      {writeResult && (
        <Alert
          type="success"
          showIcon
          style={{ marginBottom: 12 }}
          message={writeResult.message}
          description={
            <div>
              <div>语句类型：{writeResult.statementType}</div>
              {writeResult.affectedRows > 0 && <div>影响行数：{writeResult.affectedRows}</div>}
              {writeResult.sql && (
                <div style={{ marginTop: 8 }}>
                  <SqlPreviewBlock sql={writeResult.sql} maxHeight={120} />
                </div>
              )}
            </div>
          }
        />
      )}
    </div>
  );

  const schemaToolsUi = (
    <div>
      <Typography.Paragraph type="secondary">
        可在当前 Schema 下新建物理表，或执行 DDL/DML 语句。建表后可在左侧表列表中刷新查看。
      </Typography.Paragraph>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateTableOpen(true)}>
          新建物理表
        </Button>
      </Space>
      {writeSqlUi}
    </div>
  );

  return (
    <Modal
      title={`数据库浏览 — ${datasource?.name || ''}`}
      open={open}
      onCancel={onClose}
      footer={null}
      width={1180}
      styles={{ body: { paddingTop: 12, maxHeight: '80vh', overflow: 'auto' } }}
      destroyOnHidden
    >
      <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
        数据源: {datasource?.id} · {datasource?.dbType} · {datasource?.jdbcUrl}
      </Typography.Paragraph>

      <div style={{ display: 'flex', gap: 12, minHeight: 480 }}>
        <div style={{ width: 180, border: '1px solid #f0f0f0', borderRadius: 6, overflow: 'auto', maxHeight: 560 }}>
          <div style={{ padding: '8px 12px', fontWeight: 500, borderBottom: '1px solid #f0f0f0' }}>Schema</div>
          <Spin spinning={schemasLoading}>
            <List
              size="small"
              dataSource={schemas}
              locale={{ emptyText: '暂无 Schema' }}
              renderItem={(item) => (
                <List.Item
                  style={{
                    cursor: 'pointer',
                    padding: '8px 12px',
                    background: selectedSchema === item.name ? '#e6f4ff' : undefined,
                  }}
                  onClick={() => handleSchemaSelect(item.name)}
                >
                  {item.name}
                </List.Item>
              )}
            />
          </Spin>
        </div>

        <div style={{ width: 240, border: '1px solid #f0f0f0', borderRadius: 6, overflow: 'auto', maxHeight: 560 }}>
          <div
            style={{
              padding: '8px 12px',
              fontWeight: 500,
              borderBottom: '1px solid #f0f0f0',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 8,
            }}
          >
            <span>表 / 视图</span>
            <Button
              type="link"
              size="small"
              icon={<PlusOutlined />}
              disabled={!selectedSchema}
              onClick={() => setCreateTableOpen(true)}
              style={{ padding: 0, height: 'auto' }}
            >
              建表
            </Button>
          </div>
          <Spin spinning={tablesLoading}>
            <List
              size="small"
              dataSource={tables}
              locale={{ emptyText: selectedSchema ? '暂无表' : '请选择 Schema' }}
              renderItem={(item) => (
                <List.Item
                  style={{
                    cursor: 'pointer',
                    padding: '8px 12px',
                    background: selectedTable?.name === item.name ? '#e6f4ff' : undefined,
                  }}
                  onClick={() => handleTableSelect(item)}
                >
                  <div>
                    <div>{item.name}</div>
                    {item.type && (
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {item.type}
                      </Typography.Text>
                    )}
                  </div>
                </List.Item>
              )}
            />
          </Spin>
        </div>

        <div style={{ flex: 1, minWidth: 0 }}>
          {!selectedTable ? (
            selectedSchema ? (
              <Tabs
                items={[
                  { key: 'schema-tools', label: '建表 / 写入', children: schemaToolsUi },
                  { key: 'sql', label: 'SQL 查询', children: sqlQueryUi },
                ]}
              />
            ) : (
              <Empty description="请选择 Schema" style={{ marginTop: 80 }} />
            )
          ) : (
            <div>
              <Typography.Text strong>
                {selectedTable.schema ? `${selectedTable.schema}.` : ''}
                {selectedTable.name}
              </Typography.Text>
              <Tabs
                style={{ marginTop: 8 }}
                activeKey={activeTab}
                onChange={setActiveTab}
                items={[
                  {
                    key: 'columns',
                    label: '字段',
                    children: (
                      <Table
                        rowKey="name"
                        size="small"
                        columns={columnTableColumns}
                        dataSource={columns}
                        pagination={false}
                        scroll={{ y: 360 }}
                      />
                    ),
                  },
                  {
                    key: 'preview',
                    label: '数据预览',
                    children: (
                      <QueryResultPanel
                        result={previewResult}
                        loading={detailLoading}
                        page={previewPage}
                        onPageChange={(pageIndex, pageSize) => {
                          if (datasource && selectedTable) {
                            loadPreview(datasource.id, selectedTable, pageIndex, pageSize);
                          }
                        }}
                      />
                    ),
                  },
                  {
                    key: 'filter',
                    label: '字段筛选',
                    children: filterGroupsUi,
                  },
                  {
                    key: 'sql',
                    label: 'SQL 查询',
                    children: sqlQueryUi,
                  },
                  {
                    key: 'write',
                    label: '数据维护',
                    children: writeSqlUi,
                  },
                ]}
              />
            </div>
          )}
        </div>
      </div>
      {datasource && (
        <CreateTableModal
          datasourceId={datasource.id}
          schema={selectedSchema}
          open={createTableOpen}
          onClose={() => setCreateTableOpen(false)}
          onSuccess={handleCreateTableSuccess}
        />
      )}
    </Modal>
  );
}
