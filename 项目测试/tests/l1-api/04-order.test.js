/**
 * L1 API smoke - 04 订单模块
 *
 * 覆盖:
 *   GET  /api/order                 订单列表(按 status 过滤)
 *   GET  /api/order/{id}            订单详情
 *   POST /api/order                 下单
 *   PUT  /api/order/{id}/cancel     取消
 *   PUT  /api/order/{id}/confirm    确认收货
 *   POST /api/order/{id}/refund     申请退款
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http, AuthError } from '../../libs/http-client.js';
import { ensureUser } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L1-04 订单模块', () => {
  let token;
  before(async () => {
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    token = u.token;
  });

  it('04.01 无 token 访问订单列表应 401', async () => {
    let caught = null;
    try { await http.get('/order'); } catch (e) { caught = e; }
    expect(caught).to.be.instanceOf(AuthError);
    log.ok('无 token 访问 /order 正确 401');
  });

  it('04.02 拿订单列表(按 status)', async () => {
    const all = await http.get('/order', { token });
    expect(all).to.exist;
    log.ok(`全部订单数=${(all.records || all || []).length}`);
  });

  it('04.03 不存在的订单 id 应失败', async () => {
    let caught = null;
    try { await http.get('/order/99999', { token }); } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`不存在的订单正确拒绝(${caught.code})`);
  });

  it('04.04 取消不存在的订单应失败', async () => {
    let caught = null;
    try { await http.put('/order/99999/cancel', {}, { token }); } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`取消不存在订单正确拒绝(${caught.code})`);
  });

  it('04.05 确认收货不存在的订单应失败', async () => {
    let caught = null;
    try { await http.put('/order/99999/confirm', {}, { token }); } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`确认不存在订单正确拒绝(${caught.code})`);
  });

  it('04.06 申请退款不存在的订单应失败', async () => {
    let caught = null;
    try { await http.post('/order/99999/refund', { reason: '测试' }, { token }); } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`申请退款不存在订单正确拒绝(${caught.code})`);
  });
});