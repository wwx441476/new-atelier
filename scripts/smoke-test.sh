#!/usr/bin/env bash
# new-atelier API 冒烟测试 — 对应 test-develop.md §8
set -e
BASE=http://localhost:8090/api/v2

echo "== 1. 数据源 =="
curl -sf "$BASE/datasources" | grep -q '"ds-demo"' && echo "OK: datasources"

echo "== 2. 元数据 =="
curl -sf "$BASE/metadata/tables" | grep -q '"orders"' && echo "OK: metadata tables"
curl -sf "$BASE/metadata/tables/mt-orders/fields" | grep -q '"dept_code"' && echo "OK: metadata fields"

echo "== 3. 维度 =="
curl -sf "$BASE/dimensions" | grep -q '"dept"' && echo "OK: dimensions"
curl -sf "$BASE/dimensions/dim-dept/values" | grep -q '"001"' && echo "OK: dimension values"

echo "== 4. 指标 =="
curl -sf "$BASE/metrics/definitions" | grep -q '"revenue"' && echo "OK: metric definitions"
curl -sf "$BASE/metrics/revenue/sql" | grep -q '"sql"' && echo "OK: metric sql preview"
curl -sf -X POST "$BASE/metrics/query" \
  -H 'Content-Type: application/json' \
  -d '{"metricCodes":["revenue"],"filters":[{"field":"dept_code","operator":"IN","values":["001"]}]}' \
  | grep -q '"rows"' && echo "OK: metric query"

echo "== 5. 预警 =="
curl -sf "$BASE/warning/rules" | grep -q '"low_profit"' && echo "OK: warning rules"

echo ""
echo "✅ 全部冒烟测试通过"
