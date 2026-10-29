/**
 * HTTP 客户端 - 封装 axios,统一处理后端 R{code,msg,data} 包装
 *
 * 用法:
 *   import { http } from '../libs/http-client.js';
 *   const r = await http.get('/product/list');
 *   // r 直接是 data 部分(已是数组),不是 {code, data}
 */

import axios from 'axios';
import { ENV } from '../config/env.js';

class HttpClient {
  constructor(baseURL) {
    this.client = axios.create({
      baseURL,
      timeout: ENV.TIMEOUT_MS,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  /**
   * 发请求,统一处理 R{code,msg,data} 包装
   * - code=200: 直接返回 data
   * - code=401: 抛 AuthError(用于鉴权矩阵判断)
   * - 其他: 抛 BizError(挂 code 和 msg)
   */
  async request(method, path, { body, headers, token } = {}) {
    const config = {
      method,
      url: path,
      headers: { ...(headers || {}) },
      // 关键:让 axios 不抛 4xx/5xx,让我们自己处理
      validateStatus: () => true,
    };
    if (body !== undefined) config.data = body;
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    const res = await this.client.request(config);

    // 处理 raw HTTP 失败(网络层 / 404)
    if (res.status === 404) {
      throw new HttpError(404, 'endpoint not found', path);
    }
    if (res.status >= 500) {
      throw new HttpError(res.status, res.statusText || 'server error', path);
    }

    // 后端 R 包装
    const body0 = res.data;
    if (!body0 || typeof body0 !== 'object' || !('code' in body0)) {
      // 非包装响应(可能是中间件 raw JSON 或 HTML)
      return body0;
    }
    if (body0.code === 200) return body0.data;
    if (body0.code === 401) throw new AuthError(body0.msg || '未登录', body0.code);
    throw new BizError(body0.msg || '业务异常', body0.code);
  }

  get(path, opts) { return this.request('GET', path, opts); }
  post(path, body, opts) { return this.request('POST', path, { body, ...(opts || {}) }); }
  put(path, body, opts) { return this.request('PUT', path, { body, ...(opts || {}) }); }
  delete(path, opts) { return this.request('DELETE', path, opts); }
}

export class HttpError extends Error {
  constructor(status, msg, path) {
    super(`HTTP ${status} ${msg} (${path})`);
    this.status = status;
  }
}
export class AuthError extends Error {
  constructor(msg, code) {
    super(`AUTH ${code || 401} ${msg}`);
    this.code = code || 401;
  }
}
export class BizError extends Error {
  constructor(msg, code) {
    super(`BIZ ${code} ${msg}`);
    this.code = code;
  }
}

// 默认实例:走后端 8080
export const http = new HttpClient(ENV.API_BASE);

// 也导出一个用于静态资源(走 vite 3000 或 backend 8080,均可)
export const imgHttp = axios.create({ timeout: ENV.TIMEOUT_MS });