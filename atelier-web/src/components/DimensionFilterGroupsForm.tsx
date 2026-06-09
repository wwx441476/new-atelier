import { Button, Form, Input, Select, Space, Typography } from 'antd';
import type { FilterGroupForm } from '../utils/queryFilters';
import { createDefaultFilterGroup } from '../utils/queryFilters';

const OPERATORS = ['IN', 'EQ', 'GT', 'LT', 'GTE', 'LTE'];

interface DimensionFilterGroupsFormProps {
  fieldOptions: { label: string; value: string }[];
  valueOptionsByField?: Record<string, { label: string; value: string }[]>;
  onCreateGroup?: () => FilterGroupForm;
}

export default function DimensionFilterGroupsForm({
  fieldOptions,
  valueOptionsByField,
  onCreateGroup,
}: DimensionFilterGroupsFormProps) {
  const createGroup = onCreateGroup || (() => createDefaultFilterGroup(fieldOptions[0]?.value || ''));

  return (
    <Form.List name="filterGroups">
      {(groups, { add: addGroup, remove: removeGroup }) => (
        <>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 12, fontSize: 13 }}>
            组内条件以「且」组合，多个条件组之间以「或」组合
          </Typography.Paragraph>
          {groups.map((group, groupIndex) => (
            <div key={group.key}>
              {groupIndex > 0 && (
                <div
                  style={{
                    textAlign: 'center',
                    margin: '8px 0',
                    color: '#1677ff',
                    fontWeight: 500,
                  }}
                >
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
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: 8,
                  }}
                >
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
                            <Typography.Text
                              type="secondary"
                              style={{ display: 'block', margin: '4px 0 4px 4px' }}
                            >
                              且
                            </Typography.Text>
                          )}
                          <Space align="baseline" style={{ display: 'flex', flexWrap: 'wrap' }}>
                            <Form.Item
                              {...cond}
                              name={[cond.name, 'field']}
                              label={condIndex === 0 ? '维度字段' : ''}
                            >
                              <Select
                                placeholder="选择维度"
                                style={{ width: 160 }}
                                showSearch
                                optionFilterProp="label"
                                options={fieldOptions}
                              />
                            </Form.Item>
                            <Form.Item
                              {...cond}
                              name={[cond.name, 'operator']}
                              label={condIndex === 0 ? '运算符' : ''}
                            >
                              <Select
                                style={{ width: 100 }}
                                options={OPERATORS.map((o) => ({ label: o, value: o }))}
                              />
                            </Form.Item>
                            <Form.Item
                              noStyle
                              shouldUpdate={(prev, cur) =>
                                prev?.filterGroups?.[group.name]?.conditions?.[cond.name]?.field
                                  !== cur?.filterGroups?.[group.name]?.conditions?.[cond.name]?.field
                              }
                            >
                              {({ getFieldValue }) => {
                                const field = getFieldValue([
                                  'filterGroups',
                                  group.name,
                                  'conditions',
                                  cond.name,
                                  'field',
                                ]) as string | undefined;
                                const options = field ? valueOptionsByField?.[field] : undefined;
                                return (
                                  <Form.Item
                                    {...cond}
                                    name={[cond.name, 'values']}
                                    label={condIndex === 0 ? '值' : ''}
                                  >
                                    {options && options.length > 0 ? (
                                      <Select
                                        mode="tags"
                                        placeholder="选择或输入"
                                        style={{ minWidth: 200 }}
                                        tokenSeparators={[',']}
                                        options={options}
                                      />
                                    ) : (
                                      <Input placeholder="001,002" style={{ width: 200 }} />
                                    )}
                                  </Form.Item>
                                );
                              }}
                            </Form.Item>
                            <Button
                              type="link"
                              danger
                              onClick={() => removeCond(cond.name)}
                              disabled={conditions.length === 1}
                            >
                              删除
                            </Button>
                          </Space>
                        </div>
                      ))}
                      <Button
                        type="dashed"
                        size="small"
                        onClick={() =>
                          addCond({
                            field: fieldOptions[0]?.value || '',
                            operator: 'IN',
                            values: [],
                          })
                        }
                        style={{ marginTop: 8 }}
                      >
                        添加条件
                      </Button>
                    </>
                  )}
                </Form.List>
              </div>
            </div>
          ))}
          <Button type="dashed" onClick={() => addGroup(createGroup())} block>
            添加条件组
          </Button>
        </>
      )}
    </Form.List>
  );
}
