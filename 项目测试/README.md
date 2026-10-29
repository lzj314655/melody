# 麦乐迪零食商场 — 测试工程

> **完全独立的测试工程**,放在 `D:\bishe\项目测试\` 下,与业务代码隔离。
> **不修改** `D:\bishe\backend/` 和 `D:\bishe\frontend_google1/` 的任何代码。
> 测试计划参见 `测试计划/` 目录。

---

## 目录结构

```
项目测试/
├── package.json              ← 独立 npm 工程
├── config/                   ← 环境与账号
├── libs/                     ← 自研工具(http/auth/db/logger)
├── tests/
│   ├── l1-api/               ← L2 API smoke (48 端点)
│   ├── l2-auth-matrix/       ← 鉴权矩阵 (5×4)
│   ├── l3-integration/       ← 集成(并发/降级/注入)
│   └── l4-uat/               ← UAT 业务流
├── scripts/                  ← 辅助脚本
└── reports/                  ← 测试报告
```

## 第一次使用

```bash
cd D:\bishe\项目测试
npm install
# 改 config/accounts.js,填 user/admin 账号(从 data-init.sql grep)
```

## 跑测试(确保后端 + 前端 + MySQL + Redis 都已起)

```bash
npm run test:l1       # L1 API smoke
npm run test:l2       # L2 鉴权矩阵
npm run test:l3       # L3 集成
npm run test:l4       # L4 UAT
npm run test:all      # 全部
npm run test:report   # 出 JSON 报告到 reports/
```

## 测试分层

| 层 | 范围 | 文件 | 通过标准 |
|---|---|---|---|
| L1 API | 48 端点 happy path | `tests/l1-api/*.test.js` | 全过 |
| L2 鉴权 | 5 角色 × 5 端点 | `tests/l2-auth-matrix/*.test.js` | 全过 |
| L3 集成 | 并发/降级/注入 | `tests/l3-integration/*.test.js` | 全过 |
| L4 UAT | 4 业务场景 | `tests/l4-uat/*.test.js` | 全过 |

## 测试报告

- **HTML 报告**:`reports/html/report.html`(用 mochawesome 生成)
- **JSON 报告**:`reports/json/output.json`
- **自定义汇总**:`reports/summary/<日期>/summary.md`(DB 自检 + 失败用例清单)

## 与业务代码隔离的保证

| 业务目录 | 是否被改动 | 怎么做 |
|---|---|---|
| `D:\bishe\backend/src/` | ❌ 不改 | 测试工程纯黑盒 HTTP 调用 |
| `D:\bishe\frontend_google1/src/` | ❌ 不改 | UI 测试用 curl 驱动 |
| `D:\bishe/backend/pom.xml` | ❌ 不加 JUnit | L1 单测不做 |
| `D:\bishe/frontend_google1/package.json` | ❌ 不加依赖 | 测试依赖只装在 `项目测试/` 下 |