/**
 * L3 集成 - 05 SQL 注入
 */

import { expect } from 'chai';
import mocha from 'mocha';
import { http } from '../../libs/http-client.js';
import { ensureUser } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

const PAYLOADS = [
  "' OR 1=1 --",
  "'; DROP TABLE t_user; --",
  "' UNION SELECT * FROM t_user --",
  "1' OR '1'='1",
  "admin'/*",
];

describe('L3-05 SQL 注入', () => {
  PAYLOADS.forEach((p, i) => {
    it(`05.${String(i + 1).padStart(2, '0')} 搜索关键字注入`, async () => {
      const url = `/product/list?keyword=${encodeURIComponent(p)}`;
      const data = await http.get(url);
      expect(data).to.be.an('array');
      log.ok(`注入 "${p.slice(0, 20)}" 返回条数=${data.length}`);
    });

    it(`05.${String(i + 1).padStart(2, '0')} 商品详情 id 注入`, async () => {
      try {
        await http.get(`/product/detail/${encodeURIComponent(p)}`);
      } catch (e) {
        expect(e.code).to.not.equal(500);
        log.ok(`注入 "${p}" 被拒(${e.code})`);
      }
    });
  });

  it('05.99 后端仍然存活', async () => {
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    const data = await http.get('/user', { token: u.token });
    expect(data).to.exist;
    log.ok('注入测试后 /api/user 仍正常');
  });
});