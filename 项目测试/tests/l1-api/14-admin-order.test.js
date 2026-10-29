/**
 * L1 API smoke - 14 后台订单
 *
 * 覆盖:
 *   GET /api/admin/order/list
 *   GET /api/admin/order/{id}
 *   PUT /api/admin/order/{id}/ship
 *   PUT /api/admin/order/{id}/refund-approve
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http, AuthError } from '../../libs/http-client.js';
import { ensureAdmin } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L1-14 后台订单', () => {
  let token;
  before(async () => {
    const a = await ensureAdmin(ACCOUNTS.adminOperator.username, ACCOUNTS.adminOperator.password);
    token = a.token;
  });

  it('14.01 无 admin token 应 401', async () => {
    let caught = null;
    try { await http.get('/admin/order/list'); } catch (e) { caught = e; }
    expect(caught).to.be.instanceOf(AuthError);
    log.ok('无 token 访问后台订单列表 401');
  });

  it('14.02 后台订单列表', async () => {
    const data = await http.get('/admin/order/list', { token });
    expect(data).to.exist;
    log.ok(`后台订单数=${(data.records || data || []).length}`);
  });

  it('14.03 不存在的订单详情', async () => {
    let caught = null;
    try { await http.get('/admin/order/99999', { token }); } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`不存在订单正确拒绝(${caught.code})`);
  });

  it('14.04 发货不存在的订单应失败', async () => {
    let caught = null;
    try {
      await http.put('/admin/order/99999/ship?trackingNo=SF123', null, { token });
    } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`发货不存在订单正确拒绝(${caught.code})`);
  });

  it('14.05 退款审批不存在的订单应失败', async () => {
    let caught = null;
    try {
      await http.put('/admin/order/99999/refund-approve?approved=true', null, { token });
    } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`退款审批不存在订单正确拒绝(${caught.code})`);
  });
});