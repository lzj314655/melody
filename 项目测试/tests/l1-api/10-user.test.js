/**
 * L1 API smoke - 10 用户
 *
 * 覆盖:
 *   GET /api/user                当前用户信息
 *   PUT /api/user                更新资料
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http, AuthError } from '../../libs/http-client.js';
import { ensureUser } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L1-10 用户', () => {
  let token;
  before(async () => {
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    token = u.token;
  });

  it('10.01 无 token 拿用户信息应 401', async () => {
    let caught = null;
    try { await http.get('/user'); } catch (e) { caught = e; }
    expect(caught).to.be.instanceOf(AuthError);
    log.ok('无 token 访问 /user 正确 401');
  });

  it('10.02 拿当前用户信息', async () => {
    const me = await http.get('/user', { token });
    expect(me).to.have.property('phone', ACCOUNTS.user1.phone);
    log.ok(`当前用户: ${me.nickname || me.phone}`);
  });

  it('10.03 更新昵称', async () => {
    const data = await http.put('/user', { nickname: '测试昵称' }, { token });
    expect(data).to.exist;
    log.ok('更新昵称成功');
  });
});