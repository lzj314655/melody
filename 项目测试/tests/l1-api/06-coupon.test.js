/**
 * L1 API smoke - 06 优惠券
 *
 * 覆盖:
 *   GET  /api/coupon/available           可领券(公开)
 *   GET  /api/coupon/mine                我的券
 *   POST /api/coupon/{id}/claim          领券
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http, AuthError } from '../../libs/http-client.js';
import { ensureUser } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L1-06 优惠券', () => {
  it('06.01 可领券列表(公开)', async () => {
    const data = await http.get('/coupon/available');
    expect(data).to.be.an('array');
    log.ok(`可领券数=${data.length}`);
  });

  describe('需要 token 的接口', () => {
    let token;
    before(async () => {
      const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
      token = u.token;
    });

    it('06.02 我的券列表', async () => {
      const data = await http.get('/coupon/mine', { token });
      expect(data).to.exist;
      log.ok(`我的券数=${(data.records || data || []).length}`);
    });

    it('06.03 领不存在的券应失败', async () => {
      let caught = null;
      try {
        await http.post('/coupon/99999/claim', null, { token });
      } catch (e) { caught = e; }
      expect(caught).to.exist;
      log.ok(`领不存在的券正确拒绝(${caught.code})`);
    });

    it('06.04 无 token 领券应 401', async () => {
      let caught = null;
      try { await http.post('/coupon/1/claim'); } catch (e) { caught = e; }
      expect(caught).to.be.instanceOf(AuthError);
      log.ok('无 token 领券正确 401');
    });
  });
});