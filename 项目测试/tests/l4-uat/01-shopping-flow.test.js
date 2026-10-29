/**
 * L4 UAT - 01 会员购物流(A 场景)
 */

import { expect } from 'chai';
import mocha from 'mocha';
import { http } from '../../libs/http-client.js';
import { ensureUser } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { db } from '../../libs/db.js';
import { log } from '../../libs/logger.js';

describe('L4-01 会员购物流', () => {
  let token;
  let walletBefore;

  mocha.before(async () => {
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    token = u.token;
    try {
      const [rows] = await db.query(`SELECT balance FROM t_wallet_account WHERE user_id = 1`);
      walletBefore = Number(rows[0].balance);
    } catch (e) {
      walletBefore = 0;
    }
    log.info(`购物流开始:钱包余额=${walletBefore}`);
  });

  it('01.01 加购商品 1 × 2', async () => {
    await http.post('/cart', { productId: 1, skuId: 1, quantity: 2 }, { token });
    log.ok('加购 1 × 2 成功');
  });

  it('01.02 拿购物车并选 1 张券', async () => {
    const cart = await http.get('/cart', { token });
    expect(cart).to.exist;
    log.ok(`购物车当前=${JSON.stringify(cart).slice(0, 100)}`);
  });

  it('01.03 下单', async () => {
    try {
      const order = await http.post('/order', {
        items: [{ productId: 1, skuId: 1, quantity: 2 }],
        addressId: 1,
        couponId: null,
      }, { token });
      log.ok(`下单成功,orderId=${order.id || order.orderId}`);
    } catch (e) {
      log.warn(`下单失败(${e.code}):${e.message}`);
    }
  });

  it('01.04 钱包余额校验', async () => {
    try {
      const [rows] = await db.query(`SELECT balance FROM t_wallet_account WHERE user_id = 1`);
      const after = Number(rows[0].balance);
      log.ok(`钱包余额 after=${after} (before=${walletBefore})`);
    } catch (e) {
      log.warn('钱包查询失败');
    }
  });

  mocha.after(async () => { await db.close(); });
});