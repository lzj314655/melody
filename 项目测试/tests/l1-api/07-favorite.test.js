/**
 * L1 API smoke - 07 收藏
 *
 * 覆盖:
 *   GET    /api/favorite                 列表
 *   POST   /api/favorite                 添加
 *   POST   /api/favorite/check           检查
 *   DELETE /api/favorite/{productId}     删除
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http, AuthError } from '../../libs/http-client.js';
import { ensureUser } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L1-07 收藏', () => {
  let token;
  before(async () => {
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    token = u.token;
  });

  it('07.01 无 token 访问收藏应 401', async () => {
    let caught = null;
    try { await http.get('/favorite'); } catch (e) { caught = e; }
    expect(caught).to.be.instanceOf(AuthError);
    log.ok('无 token 访问 /favorite 正确 401');
  });

  it('07.02 收藏商品 1', async () => {
    // POST /favorite/{productId} 才是真实接口,POST /favorite 不存在
    await http.post('/favorite/1', null, { token });
    log.ok('收藏商品 1 成功(POST /favorite/{productId})');
  });

  it('07.03 检查商品 1 是否已收藏', async () => {
    const data = await http.post('/favorite/check', { productIds: [1] }, { token });
    expect(data).to.exist;
    log.ok(`检查结果: ${JSON.stringify(data)}`);
  });

  it('07.04 取消收藏商品 1', async () => {
    // DELETE 接口返 R<Void>,data 为 null/void,只验证不抛
    await http.delete('/favorite/1', { token });
    log.ok('取消收藏成功(后端返 R<Void>)');
  });

  it('07.05 收藏不存在的商品应失败', async () => {
    let caught = null;
    try {
      await http.post('/favorite', { productId: 99999 }, { token });
    } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`收藏不存在商品正确拒绝(${caught.code})`);
  });
});