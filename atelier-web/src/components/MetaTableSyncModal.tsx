import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Form, Input, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { datasourceApi } from '../api/datasource';
import { metadataApi } from '../api/metadata';
import type { DataSourceResponse, DbSchemaInfo, DbTableInfo, MetaTable } from '../api/types';

interface MetaTableSyncModalProps {
  open: boolean;
  datasources: DataSourceResponse[];
  defaultDatasourceId?: string;
  defaultCatalogCode?: string;
  onClose: () => void;
  onImported: () => void;
}

type TableRow = DbTableInfo & {
  key: string;
  imported: boolean;
};

export default function MetaTableSyncModal({
  open,
  datasources,
  defaultDatasourceId,
  defaultCatalogCode,
  onClose,
  onImported,
}: MetaTableSyncModalProps) {
  const [form] = Form.useForm<{ datasourceId: string; schemaCode?: string; catalogCode?: string }>();
  const datasourceId = Form.useWatch('datasourceId', form);
  const schemaCode = Form.useWatch('schemaCode', form);

  const [schemaOptions, setSchemaOptions] = useState<DbSchemaInfo[]>([]);
  const [schemaLoading, setSchemaLoading] = useState(false);
  const [tablesLoading, setTablesLoading] = useState(false);
  const [importing, setImporting] = useState(false);
  const [physicalTables, setPhysicalTables] = useState<DbTableInfo[]>([]);
  const [existingTables, setExistingTables] = useState<MetaTable[]>([]);
  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);

  const loadSchemaOptions = useCallback(async (dsId: string) => {
    setSchemaLoading(true);
    try {
      setSchemaOptions(await datasourceApi.browseSchemas(dsId));
    } catch {
      setSchemaOptions([]);
    } finally {
      setSchemaLoading(false);
    }
  }, []);

  const loadTables = useCallback(
    async (dsId: string, schema?: string) => {
      setTablesLoading(true);
      try {
        const [physical, existing] = await Promise.all([
          datasourceApi.browseTables(dsId, schema),
          metadataApi.listTables(dsId),
        ]);
        setPhysicalTables(physical);
        setExistingTables(existing);
      } catch {
        setPhysicalTables([]);
        setExistingTables([]);
      } finally {
        setTablesLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    if (!open) {
      setPhysicalTables([]);
      setExistingTables([]);
      setSelectedRowKeys([]);
      return;
    }
    form.setFieldsValue({
      datasourceId: defaultDatasourceId,
      catalogCode: defaultCatalogCode,
      schemaCode: undefined,
    });
    if (defaultDatasourceId) {
      loadSchemaOptions(defaultDatasourceId);
    }
  }, [open, defaultDatasourceId, defaultCatalogCode, form, loadSchemaOptions]);

  useEffect(() => {
    if (!open || !datasourceId) {
      return;
    }
    loadTables(datasourceId, schemaCode);
    setSelectedRowKeys([]);
  }, [open, datasourceId, schemaCode, loadTables]);

  const tableRows: TableRow[] = useMemo(() => {
    return physicalTables.map((table) => {
      const imported = existingTables.some(
        (meta) => meta.tableCode?.toLowerCase() === table.name.toLowerCase(),
      );
      return {
        ...table,
        key: `${table.schema || schemaCode || ''}.${table.name}`,
        imported,
      };
    });
  }, [physicalTables, existingTables, schemaCode]);

  const selectableRowKeys = useMemo(
    () => tableRows.filter((row) => !row.imported).map((row) => row.key),
    [tableRows],
  );

  const columns: ColumnsType<TableRow> = [
    { title: '表名', dataIndex: 'name', width: 180 },
    {
      title: 'Schema',
      width: 120,
      render: (_, record) => record.schema || schemaCode || '-',
    },
    { title: '类型', dataIndex: 'type', width: 90 },
    { title: '备注', dataIndex: 'remarks', ellipsis: true },
    {
      title: '状态',
      width: 100,
      render: (_, record) =>
        record.imported ? <Tag color="default">已同步</Tag> : <Tag color="blue">可同步</Tag>,
    },
  ];

  const handleImport = async () => {
    if (!datasourceId) {
      message.warning('请选择数据源');
      return;
    }
    const tableNames = tableRows
      .filter((row) => selectedRowKeys.includes(row.key) && !row.imported)
      .map((row) => row.name);
    if (tableNames.length === 0) {
      message.warning('请至少选择一张未同步的表');
      return;
    }
    const values = form.getFieldsValue();
    setImporting(true);
    try {
      const result = await metadataApi.importTables({
        datasourceId,
        schemaCode: values.schemaCode,
        catalogCode: values.catalogCode,
        tableNames,
      });
      const imported = result.importedCount ?? result.imported?.length ?? 0;
      const skipped = result.skippedCount ?? result.skipped?.length ?? 0;
      message.success(`已同步 ${imported} 张表${skipped > 0 ? `，跳过 ${skipped} 张已存在表` : ''}`);
      onImported();
      onClose();
    } finally {
      setImporting(false);
    }
  };

  return (
    <Modal
      title="从数据库同步元数据表"
      open={open}
      onCancel={onClose}
      width={900}
      destroyOnHidden
      footer={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={importing} onClick={handleImport}>
            同步选中表 ({selectedRowKeys.length})
          </Button>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="从所选数据源读取物理表结构，批量创建元数据表及字段。已同步的表会自动跳过。"
      />
      <Form form={form} layout="vertical">
        <Space align="start" style={{ display: 'flex', width: '100%' }} size="large">
          <Form.Item
            name="datasourceId"
            label="数据源"
            rules={[{ required: true, message: '请选择数据源' }]}
            style={{ flex: 1, minWidth: 220 }}
          >
            <Select
              placeholder="选择数据源"
              options={datasources.map((d) => ({ label: `${d.name} (${d.id})`, value: d.id }))}
              onChange={(value: string) => {
                form.setFieldValue('schemaCode', undefined);
                loadSchemaOptions(value);
              }}
            />
          </Form.Item>
          <Form.Item name="schemaCode" label="Schema" style={{ flex: 1, minWidth: 180 }}>
            <Select
              allowClear
              showSearch
              placeholder="选择 Schema"
              loading={schemaLoading}
              options={schemaOptions.map((s) => ({ label: s.name, value: s.name }))}
            />
          </Form.Item>
          <Form.Item name="catalogCode" label="目录编码" style={{ flex: 1, minWidth: 160 }}>
            <Input placeholder="如 finance（可选）" />
          </Form.Item>
        </Space>
      </Form>

      <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography.Text strong>物理表列表</Typography.Text>
        <Space>
          <Button
            size="small"
            disabled={selectableRowKeys.length === 0}
            onClick={() => setSelectedRowKeys(selectableRowKeys)}
          >
            全选可同步
          </Button>
          <Button size="small" onClick={() => setSelectedRowKeys([])}>
            清空选择
          </Button>
        </Space>
      </div>

      <Table
        rowKey="key"
        size="small"
        loading={tablesLoading}
        columns={columns}
        dataSource={tableRows}
        pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 张表` }}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => setSelectedRowKeys(keys as string[]),
          getCheckboxProps: (record) => ({ disabled: record.imported }),
        }}
        scroll={{ y: 360 }}
      />
    </Modal>
  );
}
