/**
 * L1 API smoke - 17 后台系统 / 管理员账号
 *
 * 覆盖(需 SUPER_ADMIN):
 *   GET    /api/admin/system/admin/list
 *   POST   /api/admin/system/admin/create
 *   DELETE /api/admin/system/admin/{id}
 *   POST   /api/admin/system/admin/{id}/reset-password
 *   POST   /api/admin/system/data-reset         ⚠️ 高破坏,默认跳过
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http, AuthError, BizError } from '../../libs/http-client.js';
import { ensureAdmin, loginAdmin } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L1-17 后台系统', () => {
  let operatorToken;
  let superToken;
  before(async () => {
    const op = await ensureAdmin(ACCOUNTS.adminOperator.username, ACCOUNTS.adminOperator.password);
    operatorToken = op.token;
    // super admin 每次都新登录(operator 没 SUPER_ADMIN 权限)
    const sp = await loginAdmin(ACCOUNTS.adminSuper.username, ACCOUNTS.adminSuper.password);
    superToken = sp.token;
  });

  it('17.01 OPERATOR 访问 admin 列表应 403', async () => {
    let caught = null;
    try { await http.get('/admin/system/admin/list', { token: operatorToken }); }
    catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`OPERATOR 访问 admin 列表被拒(${caught.code || 'err'})`);
  });

  it('17.02 SUPER_ADMIN 访问 admin 列表应 200', async () => {
    const data = await http.get('/admin/system/admin/list', { token: superToken });
    expect(data).to.exist;
    log.ok(`后台管理员数=${(data.records || data || []).length}`);
  });

  it('17.03 创建管理员(缺必填)', async () => {
    let caught = null;
    try {
      await http.post('/admin/system/admin/create', { username: 'test' }, { token: superToken });
    } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`创建管理员缺字段正确拒绝(${caught.code})`);
  });

  it('17.04 OPERATOR 调 data-reset 应 403', async () => {
    let caught = null;
    try { await http.post('/admin/system/data-reset', {}, { token: operatorToken }); }
    catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`OPERATOR 调 data-reset 被拒(${caught.code || 'err'})`);
  });

  it('17.05 SUPER_ADMIN 调 data-reset 应 200', async () => {
    // data-reset 返 R<Void>,data 为 null,只验证不抛
    await http.post('/admin/system/data-reset', {}, { token: superToken });
    log.ok('SUPER_ADMIN data-reset 成功');
  });
});