/**
 * L4 UAT - 04 行为埋点端到端
 *
 * 流程:
 *   1. 上报 4 类行为(VIEW/ADD_CART/FAVORITE/ORDER)
 *   2. 等 5 秒让异步落库
 *   3. 查 DB user_behavior_log 验证
 */

import { expect } from 'chai';
import { http } from '../../libs/http-client.js';
import { db } from '../../libs/db.js';
import { log } from '../../libs/logger.js';
import { ensureUser } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';

const TYPES = ['VIEW', 'ADD_CART', 'FAVORITE', 'REVIEW', 'ORDER'];

describe('L4-04 行为埋点端到端', () => {
  it('04.01 上报 4 类行为(带 user token)', async () => {
    const u = await ensureUser(ACCOUNTS.user1.phone, ACCOUNTS.user1.password);
    for (const t of TYPES) {
      await http.post('/behavior/report', {
        behaviorType: t,
        productId: 1,
        extra: JSON.stringify({ source: 'uat', test: true }),
      }, { token: u.token });
    }
    log.ok(`4 类行为上报完成: ${TYPES.join(',')}`);
  });

  it('04.02 等异步落库后查 DB', async () => {
    log.info('等 5 秒...');
    await new Promise((r) => setTimeout(r, 5000));

    const [rows] = await db.query(
      `SELECT behavior_type, COUNT(*) c FROM user_behavior_log
       WHERE created_at > DATE_SUB(NOW(), INTERVAL 5 MINUTE)
         AND JSON_EXTRACT(extra, '$.source') = 'uat'
       GROUP BY behavior_type`
    );
    log.ok(`DB 统计: ${JSON.stringify(rows)}`);
    const types = rows.map((r) => r.behavior_type);
    TYPES.forEach((t) => {
      expect(types, `${t} 应入库`).to.include(t);
    });
  });

  after(async () => { await db.close(); });
});