/**
 * L4 UAT - 03 安全 / 异常流(C 场景)
 */

import { expect } from 'chai';
import { http, AuthError, BizError } from '../../libs/http-client.js';
import { log } from '../../libs/logger.js';
import { ACCOUNTS } from '../../config/accounts.js';

describe('L4-03 安全异常流', () => {
  it('03.01 无 token /cart 应 401', async () => {
    let caught = null;
    try { await http.get('/cart'); } catch (e) { caught = e; }
    expect(caught).to.be.instanceOf(AuthError);
    log.ok('✓ 无 token 访问 /cart 正确 401');
  });

  it('03.02 无 token /admin/order/list 应 401', async () => {
    let caught = null;
    try { await http.get('/admin/order/list'); } catch (e) { caught = e; }
    expect(caught).to.be.instanceOf(AuthError);
    log.ok('✓ 无 token 访问后台 正确 401');
  });

  it('03.03 user token 访问后台应 403', async () => {
    const u = await import('../../libs/auth.js').then(m => m.ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password));
    let caught = null;
    try { await http.get('/admin/order/list', { token: u.token }); } catch (e) { caught = e; }
    expect(caught).to.exist;
    expect(caught.code).to.equal(403);
    log.ok(`✓ user 访问后台被拒(${caught.code})`);
  });

  it('03.04 operator 访问 SUPER 接口应 1003', async () => {
    const op = await import('../../libs/auth.js').then(m => m.ensureAdmin(ACCOUNTS.adminOperator.username, ACCOUNTS.adminOperator.password));
    let caught = null;
    try { await http.get('/admin/system/admin/list', { token: op.token }); } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`✓ operator 访问 SUPER 接口被拒(${caught.code})`);
  });

  it('03.05 篡改 token 应 401', async () => {
    let caught = null;
    try { await http.get('/cart', { token: 'fake-xxx-yyy' }); } catch (e) { caught = e; }
    expect(caught).to.be.instanceOf(AuthError);
    log.ok('✓ 篡改 token 正确 401');
  });

  it('03.06 错误密码应业务码 2002(用长字符串避免触 1001)', async () => {
    const cap = await http.get('/captcha');
    let caught = null;
    try {
      await http.post('/auth/login', {
        phone: ACCOUNTS.user1.phone,
        password: 'wrong-password-123',
        captchaId: cap.id,
        captchaCode: cap.code,
      });
    } catch (e) { caught = e; }
    expect(caught).to.be.instanceOf(BizError);
    expect(caught.code).to.equal(2002);
    log.ok(`✓ 错误密码业务码 ${caught.code}`);
  });
});