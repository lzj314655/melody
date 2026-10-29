/**
 * L2 鉴权矩阵 - 5 端点 × 4 token 角色
 *
 * 矩阵:
 *              无 token   user    operator    super_admin
 *   公开端点    200        200     200         200
 *   用户端点    401        200     403         403
 *   后台端点    401        403     200         200
 *   SUPER 端点  401        403     403         200
 *
 * 端点选择:
 *   - 公开:    GET  /api/product/list
 *   - 用户:    GET  /api/cart
 *   - 后台:    GET  /api/admin/order/list
 *   - SUPER:   GET  /api/admin/system/admin/list
 */

import { expect } from 'chai';
import { http, AuthError, BizError } from '../../libs/http-client.js';
import { ensureUser, ensureAdmin } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

// 各角色的 token,延迟加载
const tokens = {};
async function getToken(role) {
  if (!tokens[role]) {
    if (role === 'user') tokens[role] = (await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password)).token;
    else if (role === 'operator') tokens[role] = (await ensureAdmin(ACCOUNTS.adminOperator.username, ACCOUNTS.adminOperator.password)).token;
    else if (role === 'super') tokens[role] = (await ensureAdmin(ACCOUNTS.adminSuper.username, ACCOUNTS.adminSuper.password)).token;
  }
  return tokens[role];
}

async function call(endpoint, role) {
  const token = role === 'none' ? null : await getToken(role);
  const opts = token ? { token } : {};
  try {
    await http.get(endpoint, opts);
    return 'ok';
  } catch (e) {
    if (e instanceof AuthError) return '401';
    if (e instanceof BizError) return `biz_${e.code}`;
    return `err_${e.message}`;
  }
}

// 期望值表 - 行:端点,列:角色
const EXPECTED = {
  '公开:/api/product/list':           { none: 'ok', user: 'ok', operator: 'ok', super: 'ok' },
  '用户:/api/cart':                   { none: '401', user: 'ok', operator: 'biz_403', super: 'biz_403' },
  '后台:/api/admin/order/list':       { none: '401', user: 'biz_403', operator: 'ok', super: 'ok' },
  'SUPER:/api/admin/system/admin/list': { none: '401', user: 'biz_403', operator: 'biz_1003', super: 'ok' },
};

const ENDPOINTS = {
  '公开:/api/product/list': '/product/list',
  '用户:/api/cart': '/cart',
  '后台:/api/admin/order/list': '/admin/order/list',
  'SUPER:/api/admin/system/admin/list': '/admin/system/admin/list',
};

const ROLES = ['none', 'user', 'operator', 'super'];

describe('L2 鉴权矩阵 (4×4)', () => {
  for (const epLabel of Object.keys(EXPECTED)) {
    describe(epLabel, () => {
      for (const role of ROLES) {
        const expected = EXPECTED[epLabel][role];
        it(`角色=${role} 期望=${expected}`, async () => {
          const got = await call(ENDPOINTS[epLabel], role);
          log.ok(`${epLabel} × ${role} → ${got} (期望 ${expected})`);
          expect(got).to.equal(expected);
        });
      }
    });
  }
});