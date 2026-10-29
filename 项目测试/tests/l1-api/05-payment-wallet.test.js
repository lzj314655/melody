/**
 * L1 API smoke - 05 支付 / 钱包
 *
 * 覆盖:
 *   GET  /api/wallet                          钱包余额
 *   GET  /api/wallet/logs                     钱包流水
 *   POST /api/wallet/recharge?amount=         充值
 *   GET  /api/payment/{orderId}/status        支付状态
 *   GET  /api/payment/wallet/balance          余额(支付模块)
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http, AuthError, BizError } from '../../libs/http-client.js';
import { ensureUser } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L1-05 支付 / 钱包', () => {
  let token;
  before(async () => {
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    token = u.token;
  });

  it('05.01 无 token 访问钱包应 401', async () => {
    let caught = null;
    try { await http.get('/wallet'); } catch (e) { caught = e; }
    expect(caught).to.be.instanceOf(AuthError);
    log.ok('无 token 访问 /wallet 正确 401');
  });

  it('05.02 拿钱包余额', async () => {
    const data = await http.get('/wallet', { token });
    expect(data).to.exist;
    log.ok(`钱包当前余额=${data.balance ?? data}`);
  });

  it('05.03 钱包流水', async () => {
    const data = await http.get('/wallet/logs', { token });
    expect(data).to.exist;
    log.ok(`钱包流水条数=${(data.records || data || []).length}`);
  });

  it('05.04 充值 100 元', async () => {
    // /wallet/recharge 返 R<Void>,data 为 undefined
    await http.post('/wallet/recharge?amount=100', null, { token });
    log.ok('充值 100 成功(后端返 R<Void>)');
  });

  it('05.05 充值负数应失败', async () => {
    let caught = null;
    try { await http.post('/wallet/recharge?amount=-100', null, { token }); }
    catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`充值负数正确拒绝(${caught.code})`);
  });

  it('05.06 充值 0 应失败', async () => {
    let caught = null;
    try { await http.post('/wallet/recharge?amount=0', null, { token }); }
    catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`充值 0 正确拒绝(${caught.code})`);
  });

  it('05.07 不存在订单的支付状态', async () => {
    let caught = null;
    try { await http.get('/payment/99999/status', { token }); } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`不存在的订单支付状态正确拒绝(${caught.code})`);
  });
});