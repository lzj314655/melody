/**
 * L3 集成 - 01 钱包充值安全性（回归用例）
 *
 * 背景：旧版 POST /wallet/recharge 是“演示直接入账”，任何用户都能凭空加钱。
 * 现在改为创建支付宝充值单，只有支付成功（异步通知/主动查询）才入账。
 * 本用例验证：并发调用充值接口不会直接改变钱包余额。
 */

import { expect } from 'chai';
import mocha from 'mocha';
import { http } from '../../libs/http-client.js';
import { ensureUser } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { db } from '../../libs/db.js';
import { log } from '../../libs/logger.js';

describe('L3-01 钱包充值安全性', () => {
  let token;
  let beforeBalance;

  mocha.before(async () => {
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    token = u.token;
    const [rows] = await db.query(`SELECT balance FROM t_wallet_account WHERE user_id = 1`);
    beforeBalance = Number(rows[0].balance);
    log.info(`并发前余额=${beforeBalance}`);
  });

  it('01.01 并发 10 次充值请求不应直接入账（余额必须保持不变）', async () => {
    const N = 10;
    // 未配置支付宝沙箱时请求可能失败，但无论如何都不能直接改余额
    const promises = Array.from({ length: N }, () =>
      http.post('/wallet/recharge?amount=1', null, { token }).catch((e) => ({ err: e.message }))
    );
    await Promise.all(promises);

    const [rows] = await db.query(`SELECT balance FROM t_wallet_account WHERE user_id = 1`);
    const after = Number(rows[0].balance);
    const delta = after - beforeBalance;
    log.info(`并发后余额=${after},delta=${delta},期望=0`);
    expect(delta).to.equal(0);
  });

  mocha.after(async () => { await db.close(); });
});
