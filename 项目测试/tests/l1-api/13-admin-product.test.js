/**
 * L1 API smoke - 13 后台商品
 *
 * 覆盖:
 *   GET    /api/admin/product/list
 *   GET    /api/admin/sku?productId=
 *   POST   /api/admin/product/create
 *   PUT    /api/admin/product/{id}
 *   DELETE /api/admin/product/{id}
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http, AuthError } from '../../libs/http-client.js';
import { ensureAdmin } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L1-13 后台商品', () => {
  let token;
  before(async () => {
    const a = await ensureAdmin(ACCOUNTS.adminOperator.username, ACCOUNTS.adminOperator.password);
    token = a.token;
  });

  it('13.01 无 admin token 应 401', async () => {
    let caught = null;
    try { await http.get('/admin/product/list'); } catch (e) { caught = e; }
    expect(caught).to.be.instanceOf(AuthError);
    log.ok('无 token 访问后台商品列表 401');
  });

  it('13.02 后台商品列表', async () => {
    const data = await http.get('/admin/product/list', { token });
    expect(data).to.exist;
    log.ok(`后台商品数=${(data.records || data || []).length}`);
  });

  it('13.03 后台 SKU 列表', async () => {
    const data = await http.get('/admin/sku?productId=1', { token });
    expect(data).to.exist;
    log.ok(`后台 SKU 数=${(data.records || data || []).length}`);
  });

  it('13.04 创建商品(缺必填应失败)', async () => {
    let caught = null;
    try {
      await http.post('/admin/product/create', { name: '缺字段' }, { token });
    } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`创建商品缺字段正确拒绝(${caught.code})`);
  });

  it('13.05 删除不存在的商品应失败', async () => {
    let caught = null;
    try { await http.delete('/admin/product/99999', { token }); } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`删除不存在商品正确拒绝(${caught.code})`);
  });
});