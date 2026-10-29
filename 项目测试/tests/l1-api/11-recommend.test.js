/**
 * L1 API smoke - 11 推荐
 *
 * 覆盖:
 *   GET /api/recommend/personal      个性推荐(登录)
 *   GET /api/recommend/similar/{id}  相似商品
 *   GET /api/recommend/cold-start    冷启动
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http, AuthError } from '../../libs/http-client.js';
import { ensureUser } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L1-11 推荐', () => {
  let token;
  before(async () => {
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    token = u.token;
  });

  it('11.01 无 token 拿个性推荐应 401', async () => {
    let caught = null;
    try { await http.get('/recommend/personal'); } catch (e) { caught = e; }
    expect(caught).to.be.instanceOf(AuthError);
    log.ok('无 token 访问 /recommend/personal 正确 401');
  });

  it('11.02 登录用户拿个性推荐', async () => {
    const data = await http.get('/recommend/personal', { token });
    expect(data).to.exist;
    log.ok(`个性推荐条数=${(data || []).length}`);
  });

  it('11.03 相似商品(商品 1)', async () => {
    const data = await http.get('/recommend/similar/1', { token });
    expect(data).to.exist;
    log.ok(`商品 1 相似数=${(data || []).length}`);
  });

  it('11.04 相似商品(不存在 id 应返空数组或业务码)', async () => {
    let caught = null;
    let result;
    try { result = await http.get('/recommend/similar/99999', { token }); } catch (e) { caught = e; }
    if (caught) {
      log.ok(`不存在商品相似推荐被拒(${caught.code})`);
    } else {
      // 后端可能直接返空数组
      expect(result).to.exist;
      log.ok(`不存在商品相似推荐返空数组(${JSON.stringify(result).slice(0, 30)})`);
    }
  });

  it('11.05 冷启动', async () => {
    const data = await http.get('/recommend/cold-start', { token });
    expect(data).to.exist;
    log.ok(`冷启动条数=${(data || []).length}`);
  });
});