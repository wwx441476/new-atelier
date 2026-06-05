# atelier-web — new-atelier 管理前端

基于 **React 18 + TypeScript + Vite 6 + Ant Design 5** 的数据工场管理控制台，对接后端 `/api/v2/` REST API。

> 完整启动说明、后端架构、API 清单见根目录 [**README.md**](../README.md)。

---

## 快速开始

### 前置：启动后端

```bash
cd /Volumes/S/IdeaProjects/yonyou/new-atelier
mvn clean install
cd atelier-app && mvn spring-boot:run
```

后端默认 **http://localhost:8090**

### 启动前端

```bash
cd atelier-web
npm install
npm run dev
```

前端默认 **http://localhost:5173**，浏览器打开后自动进入数据源管理页。

---

## 技术栈

| 依赖 | 用途 |
|------|------|
| React 18 | UI 框架 |
| TypeScript 5 | 类型安全 |
| Vite 6 | 构建与 dev server |
| Ant Design 5 | 组件库（Table、Form、Modal 等） |
| react-router-dom 6 | 路由 |
| axios | HTTP 客户端 |

---

## 页面与路由

| 页面 | 路由 | 后端 API |
|------|------|----------|
| 数据源管理 | `/datasources` | `/api/v2/datasources` |
| 元数据管理 | `/metadata` | `/api/v2/metadata/*` |
| 维度管理 | `/dimensions` | `/api/v2/dimensions` |
| 指标管理 | `/metrics` | `/api/v2/metrics/definitions` + query/sql |
| 预警规则 | `/warning-rules` | `/api/v2/warning/rules` |

路由定义见 `src/App.tsx`，布局为 `layouts/AdminLayout.tsx` 侧边栏导航。

---

## 开发代理

`vite.config.ts` 将 `/api` 代理到 `http://localhost:8090`：

```ts
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:8090',
      changeOrigin: true,
    },
  },
},
```

axios `baseURL` 为 `/api/v2`（见 `src/api/client.ts`），开发时无需额外配置 CORS。

---

## API 客户端

### 响应格式

```json
{ "code": 0, "message": "success", "data": ... }
```

### 解包逻辑（`src/api/client.ts`）

- 响应拦截器：`code !== 0` 时 `message.error` 并 reject
- `getData` / `postData` / `deleteData` 直接返回 `data` 字段
- 网络异常提示「请检查后端服务是否启动」

### API 模块

```
src/api/
├── client.ts       # axios 实例 + 拦截器
├── types.ts        # 共享类型
├── datasource.ts   # 数据源（含 testConnection）
├── metadata.ts     # 元数据
├── dimension.ts    # 维度
├── metric.ts       # 指标定义 + query + previewSql
└── warning.ts      # 预警规则
```

---

## 关键交互

| 功能 | 页面 | API |
|------|------|-----|
| 测试连接 | 数据源 | `POST /api/v2/datasources/test` |
| SQL 预览 | 指标 | `GET /api/v2/metrics/{code}/sql` |
| 查询预览 | 指标 | `POST /api/v2/metrics/query`（可配置 filters） |

---

## 目录结构

```
atelier-web/
├── src/
│   ├── api/           # axios 客户端 + 各域 API
│   ├── pages/         # 5 个管理页面
│   ├── components/    # 共享组件
│   ├── layouts/       # AdminLayout
│   ├── App.tsx        # 路由入口
│   └── main.tsx
├── vite.config.ts
├── package.json
└── tsconfig.json
```

---

## 构建与预览

```bash
npm run build    # 输出到 dist/
npm run preview  # 预览生产构建（默认另一端口）
```

生产部署时，将 `dist/` 静态资源交由 Nginx 托管，并将 `/api` 反向代理至后端 8090。

---

## 常见问题

| 问题 | 处理 |
|------|------|
| 页面空白 / 接口 404 | 确认后端已启动；检查 Vite 代理配置 |
| `npm install` 失败 | 确认 Node.js ≥ 18；可尝试 `npm cache clean --force` |
| 类型检查失败 | `npm run build` 会先执行 `tsc -b` |

更多后端与 Maven 问题见根目录 [README.md](../README.md#常见问题)。
