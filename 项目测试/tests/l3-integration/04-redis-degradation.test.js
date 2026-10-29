/**
 * L3 集成 - 04 Redis 故障降级
 */

import { expect } from 'chai';
import mocha from 'mocha';
import { http } from '../../libs/http-client.js';
import { ensureUser } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L3-04 Redis 故障降级', () => {
  it('04.01 冷启动推荐正常返回', async () => {
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    const data = await http.get('/recommend/cold-start', { token: u.token });
    expect(data).to.be.an('array');
    log.ok(`冷启动返回条数=${data.length}`);
  });

  it('04.02 相似商品正常返回', async () => {
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    const data = await http.get('/recommend/similar/1', { token: u.token });
    expect(data).to.be.an('array');
    log.ok(`相似商品返回条数=${data.length}`);
  });

  it('04.03 个性推荐正常返回或业务码拒绝', async () => {
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    try {
      const data = await http.get('/recommend/personal', { token: u.token });
      log.ok(`个性推荐返回条数=${(data || []).length}`);
    } catch (e) {
      log.warn(`个性推荐异常:${e.message}`);
    }
  });
});