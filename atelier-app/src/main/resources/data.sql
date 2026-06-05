DELETE FROM ATELIER_WARNING_RULE;
DELETE FROM ATELIER_METRIC_DEFINITION;
DELETE FROM ATELIER_DIMENSION_VALUE;
DELETE FROM ATELIER_DIMENSION_FIELD;
DELETE FROM ATELIER_DIMENSION;
DELETE FROM ATELIER_META_TABLE_FIELD;
DELETE FROM ATELIER_META_TABLE;
DELETE FROM DMP_DATASOURCE;
DELETE FROM orders;
DELETE FROM dept;

-- 1. 数据源
INSERT INTO DMP_DATASOURCE (
    PK_DATASOURCE, DS_NAME, CONNECT_URL, DS_USERNAME, VERIFICATION, DB_TYPE, ENABLE, CREATE_TIME, MODIFY_TIME
) VALUES (
    'ds-demo', 'Demo H2', 'jdbc:h2:mem:atelier;DB_CLOSE_DELAY=-1;MODE=MySQL', 'sa', '', 'H2', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 2. 元数据表 orders
INSERT INTO ATELIER_META_TABLE (PK_META_TABLE, CATALOG_CODE, TABLE_CODE, TABLE_NAME, PK_DATASOURCE, COMMENTS, CREATE_TIME, MODIFY_TIME)
VALUES ('mt-orders', 'finance', 'orders', '订单事实表', 'ds-demo', '演示订单表', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO ATELIER_META_TABLE_FIELD (PK_META_FIELD, PK_META_TABLE, FIELD_CODE, FIELD_NAME, FIELD_TYPE, SORT_NO) VALUES
('mf-1', 'mt-orders', 'dept_code', '部门编码', 'VARCHAR', 1),
('mf-2', 'mt-orders', 'fiscal_year', '财年', 'VARCHAR', 2),
('mf-3', 'mt-orders', 'amount', '金额', 'DECIMAL', 3),
('mf-4', 'mt-orders', 'cost_amount', '成本', 'DECIMAL', 4);

-- 3. 维度
INSERT INTO ATELIER_DIMENSION (PK_DIMENSION, CATALOG_CODE, DS_CODE, DS_NAME, DS_TYPE, PK_DATASOURCE, PK_META_TABLE, COMMENTS, CREATE_TIME, MODIFY_TIME)
VALUES ('dim-dept', 'finance', 'dept', '部门', 'LIST', 'ds-demo', 'mt-orders', '部门列表维度', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO ATELIER_DIMENSION_FIELD (PK_DIM_FIELD, PK_DIMENSION, FIELD_CODE, FIELD_NAME, CODE_FIELD, NAME_FIELD, SORT_NO) VALUES
('df-1', 'dim-dept', 'dept_code', '部门编码', 1, 0, 1),
('df-2', 'dim-dept', 'dept_name', '部门名称', 0, 1, 2);

INSERT INTO ATELIER_DIMENSION_VALUE (PK_DIM_VALUE, PK_DIMENSION, CODE, NAME, SORT_NO) VALUES
('dv-1', 'dim-dept', '001', '销售部', 1),
('dv-2', 'dim-dept', '002', '研发部', 2);

INSERT INTO ATELIER_DIMENSION (PK_DIMENSION, CATALOG_CODE, DS_CODE, DS_NAME, DS_TYPE, PK_DATASOURCE, PK_META_TABLE, COMMENTS, CREATE_TIME, MODIFY_TIME)
VALUES ('dim-year', 'finance', 'year', '财年', 'TIME_DIM', 'ds-demo', 'mt-orders', '时间维度', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO ATELIER_DIMENSION_FIELD (PK_DIM_FIELD, PK_DIMENSION, FIELD_CODE, FIELD_NAME, CODE_FIELD, NAME_FIELD, SORT_NO) VALUES
('df-3', 'dim-year', 'fiscal_year', '财年', 1, 1, 1);

INSERT INTO ATELIER_DIMENSION_VALUE (PK_DIM_VALUE, PK_DIMENSION, CODE, NAME, SORT_NO) VALUES
('dv-3', 'dim-year', '2024', '2024年', 1),
('dv-4', 'dim-year', '2025', '2025年', 2);

-- 4. 指标定义（声明式 JSON）
INSERT INTO ATELIER_METRIC_DEFINITION (PK_METRIC, METRIC_CODE, METRIC_NAME, CATALOG_CODE, METRIC_TYPE, PK_DATASOURCE, DEFINITION_JSON, ENABLED, CREATE_TIME, MODIFY_TIME) VALUES
('m-revenue', 'revenue', '营业收入', 'finance', 'TABLE', 'ds-demo',
 '{"code":"revenue","name":"营业收入","catalogCode":"finance","type":"TABLE","datasourceId":"ds-demo","modelCode":"finance_model","tableCode":"orders","fieldCode":"amount","aggregation":"SUM","alias":"revenue","dimensions":[{"dimensionCode":"dept","fieldCode":"dept_code","fieldName":"部门","sort":1},{"dimensionCode":"year","fieldCode":"fiscal_year","fieldName":"年度","sort":2}]}',
 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO ATELIER_METRIC_DEFINITION (PK_METRIC, METRIC_CODE, METRIC_NAME, CATALOG_CODE, METRIC_TYPE, PK_DATASOURCE, DEFINITION_JSON, ENABLED, CREATE_TIME, MODIFY_TIME) VALUES
('m-cost', 'cost', '营业成本', 'finance', 'TABLE', 'ds-demo',
 '{"code":"cost","name":"营业成本","catalogCode":"finance","type":"TABLE","datasourceId":"ds-demo","modelCode":"finance_model","tableCode":"orders","fieldCode":"cost_amount","aggregation":"SUM","alias":"cost","dimensions":[{"dimensionCode":"dept","fieldCode":"dept_code","fieldName":"部门","sort":1},{"dimensionCode":"year","fieldCode":"fiscal_year","fieldName":"年度","sort":2}]}',
 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO ATELIER_METRIC_DEFINITION (PK_METRIC, METRIC_CODE, METRIC_NAME, CATALOG_CODE, METRIC_TYPE, PK_DATASOURCE, DEFINITION_JSON, ENABLED, CREATE_TIME, MODIFY_TIME) VALUES
('m-profit', 'profit', '利润', 'finance', 'COMPOSITE', 'ds-demo',
 '{"code":"profit","name":"利润","catalogCode":"finance","type":"COMPOSITE","datasourceId":"ds-demo","formula":"revenue - cost","alias":"profit","dimensions":[{"dimensionCode":"dept","fieldCode":"dept_code","fieldName":"部门","sort":1},{"dimensionCode":"year","fieldCode":"fiscal_year","fieldName":"年度","sort":2}]}',
 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 5. 预警规则
INSERT INTO ATELIER_WARNING_RULE (PK_WARNING_RULE, CATALOG_CODE, RULE_CODE, RULE_NAME, METRIC_CODES, EXPRESSION, ENABLED, WARNING_LEVEL, NOTIFY_CONFIG, COMMENTS, CREATE_TIME, MODIFY_TIME)
VALUES ('wr-1', 'finance', 'low_profit', '利润过低预警', 'profit', 'profit < 500', 1, 2, '{"channels":["email"],"stub":true}', '演示预警规则', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 演示业务数据
INSERT INTO dept (id, name) VALUES ('d1', '销售部');
INSERT INTO dept (id, name) VALUES ('d2', '研发部');

INSERT INTO orders (dept_id, dept_code, fiscal_year, amount, cost_amount) VALUES ('d1', '001', '2024', 1000.00, 600.00);
INSERT INTO orders (dept_id, dept_code, fiscal_year, amount, cost_amount) VALUES ('d1', '001', '2024', 500.00, 300.00);
INSERT INTO orders (dept_id, dept_code, fiscal_year, amount, cost_amount) VALUES ('d2', '002', '2024', 800.00, 400.00);
INSERT INTO orders (dept_id, dept_code, fiscal_year, amount, cost_amount) VALUES ('d2', '002', '2025', 1200.00, 700.00);
