/**
 * L3 集成 - 01 钱包并发
 */

import { expect } from 'chai';
import mocha from 'mocha';
import { http } from '../../libs/http-client.js';
import { ensureUser } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { db } from '../../libs/db.js';
import { log } from '../../libs/logger.js';

describe('L3-01 钱包并发', () => {
  let token;
  let beforeBalance;

  mocha.before(async () => {
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    token = u.token;
    const [rows] = await db.query(`SELECT balance FROM t_wallet_account WHERE user_id = 1`);
    beforeBalance = Number(rows[0].balance);
    log.info(`并发前余额=${beforeBalance}`);
  });

  it('01.01 并发 10 次充值 1 元', async () => {
    const N = 10;
    const promises = Array.from({ length: N }, () =>
      http.post('/wallet/recharge?amount=1', null, { token }).catch((e) => ({ err: e.message }))
    );
    const results = await Promise.all(promises);
    const ok = results.filter((r) => !r || !r.err).length;
    log.ok(`${N} 次并发请求,成功=${ok}`);

    const [rows] = await db.query(`SELECT balance FROM t_wallet_account WHERE user_id = 1`);
    const after = Number(rows[0].balance);
    const delta = after - beforeBalance;
    log.info(`并发后余额=${after},delta=${delta},期望=${N}`);
    expect(delta).to.equal(N);
  });

  mocha.after(async () => { await db.close(); });
});