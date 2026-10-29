/**
 * L1 API smoke - 16 后台用户管理
 *
 * 覆盖:
 *   GET /api/admin/user/list
 *   GET /api/admin/user/{id}
 *   PUT /api/admin/user/{id}/status
 *   POST /api/admin/user/{id}/gift-points
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http, AuthError } from '../../libs/http-client.js';
import { ensureAdmin } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L1-16 后台用户', () => {
  let token;
  before(async () => {
    const a = await ensureAdmin(ACCOUNTS.adminOperator.username, ACCOUNTS.adminOperator.password);
    token = a.token;
  });

  it('16.01 后台用户列表(OPERATOR 无 user:read 权限应被拒)', async () => {
    let caught = null;
    try { await http.get('/admin/user/list', { token }); } catch (e) { caught = e; }
    expect(caught).to.exist;
    expect(caught.code).to.equal(1003);
    log.ok(`✓ OPERATOR 无 user:read 权限被拒(${caught.code})`);
  });

  it('16.02 后台用户列表(SUPER_ADMIN 应能访问)', async () => {
    // 用 SUPER_ADMIN 登录
    const { ensureAdmin } = await import('../../libs/auth.js');
    const { ACCOUNTS } = await import('../../config/accounts.js');
    const sp = await ensureAdmin(ACCOUNTS.adminSuper.username, ACCOUNTS.adminSuper.password);
    const data = await http.get('/admin/user/list', { token: sp.token });
    expect(data).to.exist;
    log.ok(`SUPER_ADMIN 用户数=${(data.records || data || []).length}`);
  });

  it('16.02 不存在用户详情', async () => {
    let caught = null;
    try { await http.get('/admin/user/99999', { token }); } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`不存在用户正确拒绝(${caught.code})`);
  });

  it('16.03 启停不存在的用户应失败', async () => {
    let caught = null;
    try {
      await http.put('/admin/user/99999/status?status=0', null, { token });
    } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`启停不存在用户正确拒绝(${caught.code})`);
  });

  it('16.04 赠送积分不存在的用户应失败', async () => {
    let caught = null;
    try {
      await http.post('/admin/user/99999/gift-points?points=100', null, { token });
    } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`赠送积分不存在用户正确拒绝(${caught.code})`);
  });
});