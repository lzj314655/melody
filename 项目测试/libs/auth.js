/**
 * 鉴权工具 - 双 token(user / admin)登录 + 缓存
 *
 * 用法:
 *   import { loginUser, loginAdmin, getUserToken } from '../libs/auth.js';
 *   const r = await loginUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
 *   const token = r.token;
 */

import { http } from './http-client.js';

const cache = {
  user: null,        // { phone, token }
  user2: null,
  adminOperator: null,
  adminSuper: null,
};

/**
 * 用户登录 - POST /api/auth/login
 * body: { phone, password }
 * 返: { token, userInfo, ... }
 */
export async function loginUser(phone, password) {
  // 后端登录需要图片验证码,先取一张
  const cap = await http.get('/captcha');
  const data = await http.post('/auth/login', {
    phone, password,
    captchaId: cap.id,
    captchaCode: cap.code,
  });
  if (!data || !data.token) {
    throw new Error(`loginUser ${phone}: 后端响应无 token`);
  }
  return data;
}

/**
 * 管理员登录 - POST /api/admin/auth/login
 * body: { username, password }
 * 返: { token, ... }
 */
export async function loginAdmin(username, password) {
  const data = await http.post('/admin/auth/login', { username, password });
  if (!data || !data.token) {
    throw new Error(`loginAdmin ${username}: 后端响应无 token`);
  }
  return data;
}

// 缓存包装 - 同一进程内只登录 1 次
export async function ensureUser(phone, password) {
  const key = `u:${phone}`;
  if (cache[key]) return cache[key];
  const r = await loginUser(phone, password);
  cache[key] = { phone, token: r.token, info: r };
  return cache[key];
}
export async function ensureAdmin(username, password) {
  const key = `a:${username}`;
  if (cache[key]) return cache[key];
  const r = await loginAdmin(username, password);
  cache[key] = { username, token: r.token, info: r };
  return cache[key];
}

// 直读 token(不登录)
export function getUserToken(phone) { return cache[`u:${phone}`]?.token; }
export function getAdminToken(username) { return cache[`a:${username}`]?.token; }