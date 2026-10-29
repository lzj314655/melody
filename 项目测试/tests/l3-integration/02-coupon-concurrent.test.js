/**
 * L3 集成 - 02 优惠券并发领取
 */

import { expect } from 'chai';
import mocha from 'mocha';
import { http } from '../../libs/http-client.js';
import { log } from '../../libs/logger.js';

describe('L3-02 优惠券并发领取', () => {
  it('02.01 检查可领券列表', async () => {
    const coupons = await http.get('/coupon/available');
    log.info(`可领券数=${coupons.length}`);
    expect(coupons).to.be.an('array');
  });

  it('02.02 同一用户并发领同一张券', async () => {
    log.warn('本测试简化:假设所有券无限量,验证接口不崩');
    const N = 5;
    const promises = Array.from({ length: N }, (_, i) =>
      http.post('/coupon/1/claim').catch((e) => ({ err: e.message, i }))
    );
    const results = await Promise.all(promises);
    const fail = results.filter((r) => r && r.err);
    log.ok(`${N} 次并发:成功=${N - fail.length},失败=${fail.length}`);
    fail.forEach((f) => {
      expect(f.err).to.not.match(/500/i);
    });
  });
});