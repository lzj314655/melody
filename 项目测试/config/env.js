/**
 * 测试环境配置 — 集中管理 baseURL、超时、采样商品 id 等
 */

export const ENV = {
  // 后端 API 根地址(走 8080,不经过 Vite 代理)
  API_BASE: 'http://localhost:8080/api',

  // 前端静态资源根(走 Vite 3000,实际测也可用 8080)
  IMG_BASE_VITE: 'http://localhost:3000',
  IMG_BASE_BACKEND: 'http://localhost:8080',

  // HTTP 超时
  TIMEOUT_MS: 15000,

  // L1 smoke 用抽样商品 id(来自上一轮 9 抽样)
  SAMPLE_PRODUCT_IDS: [1, 5, 11, 20, 30, 40, 50, 55, 58],

  // L3 并发规模
  CONCURRENCY: 10,

  // MySQL
  DB: {
    host: 'localhost',
    port: 3306,
    user: 'root',
    password: process.env.DB_PASSWORD || '', // 本机密码走环境变量，不入库
    database: 'melody_shop',
  },
};