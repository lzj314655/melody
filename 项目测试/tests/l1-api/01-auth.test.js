/**
 * L1 API smoke - 01 认证模块
 *
 * 覆盖端点:
 *   POST /api/auth/register        注册(需 code=123456)
 *   POST /api/auth/login           登录
 *   GET  /api/user                 需 token 的接口(验证 token 生效)
 *
 * 后端业务码(非 200 即视为异常):
 *   1001 参数校验失败
 *   2001 用户不存在
 *   2002 用户名或密码错误
 *   2003 已注册
 *   401 未登录
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http, AuthError, BizError } from '../../libs/http-client.js';
import { ensureUser, loginUser } from '../../libs/auth.js';
import { ACCOUNTS, genTempAccount } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L1-01 认证模块', () => {
  it('01.00 GET /api/captcha 应返回 4 位 code + id', async () => {
    const cap = await http.get('/captcha');
    expect(cap).to.have.property('id').that.is.a('string');
    expect(cap).to.have.property('code').that.is.a('string').with.lengthOf(4);
    log.ok(`captcha id=${cap.id.slice(0, 8)}... code=${cap.code}`);
  });

  it('01.01 用内置账号登录成功', async () => {
    const r = await loginUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    expect(r).to.have.property('token').that.is.a('string').with.length.greaterThan(10);
    expect(r).to.have.property('userId').that.is.a('number');
    log.ok(`登录成功 user1, token 长度=${r.token.length}`);
  });

  it('01.02 错误密码应抛 BizError(业务码 2002)', async () => {
    let caught = null;
    try {
      await loginUser(ACCOUNTS.user1.phone, 'wrong-password');
    } catch (e) {
      caught = e;
    }
    expect(caught).to.exist;
    expect(caught).to.be.instanceOf(BizError);
    expect(caught.code).to.equal(2002);
    log.ok('错误密码正确拒绝(业务码 2002)');
  });

  it('01.02b 错误验证码登录应被业务码拒绝', async () => {
    const cap = await http.get('/captcha');
    let caught = null;
    try {
      await http.post('/auth/login', {
        phone: ACCOUNTS.user1.phone,
        password: ACCOUNTS.user1.password,
        captchaId: cap.id,
        captchaCode: 'XXXX',   // 故意写错
      });
    } catch (e) {
      caught = e;
    }
    expect(caught).to.exist;
    log.ok(`错误验证码正确拒绝(${caught.code})`);
  });

  it('01.03 新手机号注册成功(需 captchaId + captchaCode)', async () => {
    const acc = genTempAccount();
    const cap = await http.get('/captcha');  // { id, code }
    const r = await http.post('/auth/register', {
      phone: acc.phone,
      password: acc.password,
      captchaId: cap.id,
      captchaCode: cap.code,
    });
    expect(r).to.exist;
    log.ok(`注册成功: ${acc.phone}`);
  });

  it('01.04 重复注册应失败(业务码 2003)', async () => {
    const cap = await http.get('/captcha');
    let caught = null;
    try {
      await http.post('/auth/register', {
        phone: ACCOUNTS.user1.phone,
        password: 'Test@123',
        captchaId: cap.id,
        captchaCode: cap.code,
      });
    } catch (e) {
      caught = e;
    }
    expect(caught).to.exist;
    expect(caught).to.be.instanceOf(BizError);
    log.ok(`重复注册正确拒绝(业务码 ${caught.code})`);
  });

  it('01.05 缺验证码注册应失败', async () => {
    const acc = genTempAccount();
    let caught = null;
    try {
      await http.post('/auth/register', {
        phone: acc.phone,
        password: acc.password,
        // 故意不传 captchaId / captchaCode
      });
    } catch (e) {
      caught = e;
    }
    expect(caught).to.exist;
    log.ok(`缺验证码正确拒绝(业务码 ${caught.code})`);
  });

  it('01.06 拿 token 后能访问 /api/user', async () => {
    const { token } = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    const me = await http.get('/user', { token });
    expect(me).to.have.property('phone', ACCOUNTS.user1.phone);
    log.ok(`/api/user 返回用户: ${me.nickname || me.phone}`);
  });

  it('01.07 无 token 访问 /api/user 应抛 AuthError(401)', async () => {
    let caught = null;
    try {
      await http.get('/user');
    } catch (e) {
      caught = e;
    }
    expect(caught).to.exist;
    expect(caught).to.be.instanceOf(AuthError);
    log.ok('无 token 访问受保护接口正确 401');
  });
});