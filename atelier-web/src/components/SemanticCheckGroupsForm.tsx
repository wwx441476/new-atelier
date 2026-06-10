import { Button, Form, Input, Radio, Select, Space, Tag, Typography } from 'antd';
import { createDefaultSemanticCheck, createDefaultSemanticGroup } from '../utils/semanticRuleForm';

interface SemanticCheckGroupsFormProps {
  textFieldOptions: { label: string; value: string }[];
  metaTableId?: string;
}

export default function SemanticCheckGroupsForm({
  textFieldOptions,
  metaTableId,
}: SemanticCheckGroupsFormProps) {
  return (
    <Form.List name={['ruleConfig', 'semantic', 'semanticGroups']}>
      {(groups, { add: addGroup, remove: removeGroup }) => (
        <>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 12, fontSize: 13 }}>
            组内条件以「且」组合，多个条件组之间以「或」组合
          </Typography.Paragraph>
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
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: 8,
                  }}
                >
                  <Typography.Text type="secondary">语义条件组 {groupIndex + 1}</Typography.Text>
                  {groups.length > 1 && (
                    <Button type="link" danger size="small" onClick={() => removeGroup(group.name)}>
                      删除条件组
                    </Button>
                  )}
                </div>
                <Form.List name={[group.name, 'checks']}>
                  {(checks, { add: addCheck, remove: removeCheck }) => (
                    <>
                      {checks.map((check, checkIndex) => (
                        <div
                          key={check.key}
                          style={{
                            borderTop: checkIndex > 0 ? '1px dashed #e8e8e8' : undefined,
                            paddingTop: checkIndex > 0 ? 12 : 0,
                            marginTop: checkIndex > 0 ? 12 : 0,
                          }}
                        >
                          {checkIndex > 0 && (
                            <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
                              且
                            </Typography.Text>
                          )}
                          <Space direction="vertical" style={{ width: '100%' }} size="small">
                            <Space wrap align="baseline">
                              <Form.Item
                                {...check}
                                name={[check.name, 'fieldCode']}
                                label="检测字段"
                                rules={[{ required: true, message: '请选择字段' }]}
                              >
                                <Select
                                  placeholder="文本字段"
                                  style={{ width: 200 }}
                                  options={textFieldOptions}
                                  showSearch
                                  optionFilterProp="label"
                                  disabled={!metaTableId}
                                />
                              </Form.Item>
                              <Form.Item
                                {...check}
                                name={[check.name, 'checkMode']}
                                label="策略类型"
                                initialValue="VIOLATION"
                              >
                                <Radio.Group>
                                  <Radio value="VIOLATION">违规检测</Radio>
                                  <Radio value="REQUIREMENT">必须符合</Radio>
                                </Radio.Group>
                              </Form.Item>
                              <Button
                                type="link"
                                danger
                                onClick={() => removeCheck(check.name)}
                                disabled={checks.length === 1}
                              >
                                删除
                              </Button>
                            </Space>
                            <Form.Item noStyle shouldUpdate>
                              {({ getFieldValue }) => {
                                const mode = getFieldValue([
                                  'ruleConfig',
                                  'semantic',
                                  'semanticGroups',
                                  group.name,
                                  'checks',
                                  check.name,
                                  'checkMode',
                                ]) as string | undefined;
                                const placeholder =
                                  mode === 'REQUIREMENT'
                                    ? '如：属于学杂费、教材费、学费、杂费等教育收费类项目…'
                                    : '如：不得包含烟酒相关内容，包括茅台、五粮液等品牌…';
                                return (
                                  <Form.Item
                                    {...check}
                                    name={[check.name, 'policy']}
                                    label="合规策略"
                                    rules={[{ required: true, message: '请描述策略' }]}
                                  >
                                    <Input.TextArea rows={2} placeholder={placeholder} />
                                  </Form.Item>
                                );
                              }}
                            </Form.Item>
                            <Form.Item {...check} name={[check.name, 'hintKeywords']} label="示例词（可选）">
                              <Select
                                mode="tags"
                                placeholder="输入后回车"
                                tokenSeparators={[',', '，']}
                              />
                            </Form.Item>
                            <Form.Item
                              {...check}
                              name={[check.name, 'matchMode']}
                              label="判定方式"
                              initialValue="HYBRID"
                            >
                              <Radio.Group>
                                <Radio value="HYBRID">混合（词库 + LLM）</Radio>
                                <Radio value="KEYWORD">仅词库</Radio>
                                <Radio value="LLM">仅 LLM</Radio>
                              </Radio.Group>
                            </Form.Item>
                            <Form.Item {...check} name={[check.name, 'expandedKeywords']} hidden>
                              <input type="hidden" />
                            </Form.Item>
                            <Form.Item noStyle shouldUpdate>
                              {({ getFieldValue }) => {
                                const expanded = getFieldValue([
                                  'ruleConfig',
                                  'semantic',
                                  'semanticGroups',
                                  group.name,
                                  'checks',
                                  check.name,
                                  'expandedKeywords',
                                ]) as string[] | undefined;
                                if (!expanded?.length) {
                                  return null;
                                }
                                return (
                                  <div>
                                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                      扩展词库：
                                    </Typography.Text>
                                    {expanded.slice(0, 8).map((k) => (
                                      <Tag key={k} style={{ marginTop: 4 }}>
                                        {k}
                                      </Tag>
                                    ))}
                                  </div>
                                );
                              }}
                            </Form.Item>
                          </Space>
                        </div>
                      ))}
                      <Button
                        type="dashed"
                        size="small"
                        onClick={() => addCheck(createDefaultSemanticCheck())}
                        style={{ marginTop: 8 }}
                      >
                        添加字段条件
                      </Button>
                    </>
                  )}
                </Form.List>
              </div>
            </div>
          ))}
          <Button type="dashed" onClick={() => addGroup(createDefaultSemanticGroup())} block>
            添加条件组
          </Button>
        </>
      )}
    </Form.List>
  );
}
