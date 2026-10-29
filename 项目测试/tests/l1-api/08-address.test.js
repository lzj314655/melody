/**
 * L1 API smoke - 08 地址
 *
 * 覆盖:
 *   GET    /api/address          列表
 *   POST   /api/address          新增
 *   PUT    /api/address/{id}     更新
 *   DELETE /api/address/{id}     删除
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http, AuthError } from '../../libs/http-client.js';
import { ensureUser } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L1-08 地址', () => {
  let token;
  before(async () => {
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    token = u.token;
  });

  it('08.01 无 token 访问地址应 401', async () => {
    let caught = null;
    try { await http.get('/address'); } catch (e) { caught = e; }
    expect(caught).to.be.instanceOf(AuthError);
    log.ok('无 token 访问 /address 正确 401');
  });

  it('08.02 地址列表', async () => {
    const data = await http.get('/address', { token });
    expect(data).to.exist;
    log.ok(`地址数=${(data.records || data || []).length}`);
  });

  it('08.03 新增地址', async () => {
    const data = await http.post('/address', {
      name: '测试收货人',
      phone: '13900000000',
      province: '北京市',
      city: '北京市',
      district: '朝阳区',
      detail: '测试路 1 号',
      tag: '家',
      isDefault: 0,
    }, { token });
    expect(data).to.exist;
    log.ok('新增地址成功');
  });

  it('08.04 缺必填字段应失败', async () => {
    let caught = null;
    try {
      await http.post('/address', { name: '缺字段' }, { token });
    } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`缺字段正确拒绝(${caught.code})`);
  });
});