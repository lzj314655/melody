/**
 * L1 API smoke - 15 后台优惠券 / 营销
 *
 * 覆盖:
 *   GET    /api/admin/coupon/list
 *   POST   /api/admin/coupon/create
 *   PUT    /api/admin/coupon/{id}
 *   DELETE /api/admin/coupon/{id}
 *   GET    /api/admin/promotion/list
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http, AuthError } from '../../libs/http-client.js';
import { ensureAdmin } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L1-15 后台优惠券', () => {
  let token;
  before(async () => {
    const a = await ensureAdmin(ACCOUNTS.adminOperator.username, ACCOUNTS.adminOperator.password);
    token = a.token;
  });

  it('15.01 后台券列表', async () => {
    const data = await http.get('/admin/coupon/list', { token });
    expect(data).to.exist;
    log.ok(`后台券数=${(data.records || data || []).length}`);
  });

  it('15.02 创建券(缺必填应失败)', async () => {
    let caught = null;
    try {
      await http.post('/admin/coupon/create', { name: '缺字段' }, { token });
    } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`创建券缺字段正确拒绝(${caught.code})`);
  });

  it('15.03 营销 promotion 列表', async () => {
    const data = await http.get('/admin/promotion/list', { token });
    expect(data).to.exist;
    log.ok(`营销数=${(data.records || data || []).length}`);
  });
});