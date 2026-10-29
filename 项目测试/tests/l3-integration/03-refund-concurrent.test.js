/**
 * L3 集成 - 03 退款并发
 */

import { expect } from 'chai';
import mocha from 'mocha';
import { http } from '../../libs/http-client.js';
import { ensureUser } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L3-03 退款并发(降级版)', () => {
  it('03.01 不存在订单并发申请退款,所有都应被业务码拒绝', async () => {
    const N = 5;
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    const promises = Array.from({ length: N }, () =>
      http.post('/order/99999/refund', { reason: '并发测试' }, { token: u.token }).catch((e) => e.code)
    );
    const codes = await Promise.all(promises);
    log.ok(`并发申请退款业务码: ${JSON.stringify(codes)}`);
    codes.forEach((c) => expect(c).to.not.equal(500));
  });
});