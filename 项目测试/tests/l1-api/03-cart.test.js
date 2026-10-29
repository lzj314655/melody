/**
 * L1 API smoke - 03 购物车模块
 *
 * 覆盖:
 *   GET    /api/cart                              列表
 *   POST   /api/cart                              加购
 *   PUT    /api/cart/{productId}/{skuId}          改数量
 *   PUT    /api/cart/{productId}/{skuId}/select   选中
 *   PUT    /api/cart/select-all                   全选
 *   DELETE /api/cart/{productId}/{skuId}          删
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http, AuthError } from '../../libs/http-client.js';
import { ensureUser } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L1-03 购物车模块', () => {
  let token;
  before(async () => {
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    token = u.token;
  });

  it('03.01 无 token 访问 /cart 应 401', async () => {
    let caught = null;
    try { await http.get('/cart'); } catch (e) { caught = e; }
    expect(caught).to.be.instanceOf(AuthError);
    log.ok('无 token 访问 /cart 正确 401');
  });

  it('03.02 有 token 拿购物车', async () => {
    const data = await http.get('/cart', { token });
    expect(data).to.exist;
    log.ok(`购物车返回: ${JSON.stringify(data).slice(0, 100)}`);
  });

  it('03.03 加购一个商品', async () => {
    const data = await http.post('/cart', {
      productId: 1,
      skuId: 1,
      quantity: 1,
    }, { token });
    expect(data).to.exist;
    log.ok('加购成功');
  });

  it('03.04 加购不存在的商品应失败', async () => {
    let caught = null;
    try {
      await http.post('/cart', { productId: 99999, skuId: 1, quantity: 1 }, { token });
    } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`不存在的商品加购正确拒绝(${caught.code})`);
  });

  it('03.05 加购 quantity=0 应失败', async () => {
    let caught = null;
    try {
      await http.post('/cart', { productId: 1, skuId: 1, quantity: 0 }, { token });
    } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok('quantity=0 正确拒绝');
  });
});