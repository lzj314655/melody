/**
 * L4 UAT - 02 后台管理流(B 场景)
 */

import { expect } from 'chai';
import mocha from 'mocha';
import { http } from '../../libs/http-client.js';
import { ensureAdmin } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L4-02 后台管理流', () => {
  let token;
  mocha.before(async () => {
    const a = await ensureAdmin(ACCOUNTS.adminOperator.username, ACCOUNTS.adminOperator.password);
    token = a.token;
  });

  it('02.01 Dashboard KPI', async () => {
    const data = await http.get('/admin/dashboard/kpi', { token });
    expect(data).to.exist;
    log.ok(`KPI=${JSON.stringify(data).slice(0, 80)}`);
  });

  it('02.02 订单列表', async () => {
    const data = await http.get('/admin/order/list', { token });
    expect(data).to.exist;
    log.ok(`订单数=${(data.records || data || []).length}`);
  });

  it('02.03 商品列表', async () => {
    const data = await http.get('/admin/product/list', { token });
    expect(data).to.exist;
    log.ok(`商品数=${(data.records || data || []).length}`);
  });

  it('02.04 券列表', async () => {
    const data = await http.get('/admin/coupon/list', { token });
    expect(data).to.exist;
    log.ok(`券数=${(data.records || data || []).length}`);
  });

  it('02.05 推荐重算', async () => {
    await http.post('/recommend/admin/refresh', {}, { token });
    log.ok('推荐重算 OK');
  });
});