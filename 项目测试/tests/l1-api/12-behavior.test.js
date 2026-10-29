/**
 * L1 API smoke - 12 行为埋点
 *
 * 覆盖:
 *   POST /api/behavior/report      上报行为(公开,异步落库)
 *
 * 后端 BehaviorTypeEnum 仅支持: VIEW / ADD_CART / FAVORITE / ORDER
 * (REVIEW 不在枚举内,前端埋点用 rating 字段区分)
 *
 * 不验证上报结果是否入库(异步),仅验证接口可达
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http } from '../../libs/http-client.js';
import { log } from '../../libs/logger.js';

describe('L1-12 行为埋点', () => {
  it('12.01 无 token 上报 VIEW 应 200(公开端点)', async () => {
    // /behavior/report 返 R<Void>,data 为 undefined
    await http.post('/behavior/report', {
      behaviorType: 'VIEW',
      productId: 1,
      extra: JSON.stringify({ source: 'test', category: 'all' }),
    });
    log.ok('VIEW 上报成功(后端返 R<Void>)');
  });

  it('12.02 无 token 上报 ADD_CART', async () => {
    await http.post('/behavior/report', {
      behaviorType: 'ADD_CART',
      productId: 1,
      extra: JSON.stringify({ spec: '150g', quantity: 1 }),
    });
    log.ok('ADD_CART 上报成功');
  });

  it('12.03 无 token 上报 FAVORITE', async () => {
    await http.post('/behavior/report', {
      behaviorType: 'FAVORITE',
      productId: 1,
      extra: JSON.stringify({ action: 'add' }),
    });
    log.ok('FAVORITE 上报成功');
  });

  it('12.04 无 token 上报 ORDER', async () => {
    await http.post('/behavior/report', {
      behaviorType: 'ORDER',
      productId: 1,
      extra: JSON.stringify({ amount: 100 }),
    });
    log.ok('ORDER 上报成功');
  });

  it('12.05 无效 type 应被业务码拒绝', async () => {
    let caught = null;
    try {
      await http.post('/behavior/report', { behaviorType: 'INVALID_TYPE', productId: 1, extra: '{}' });
    } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`无效 type 正确拒绝(${caught.code})`);
  });

  it('12.06 缺 type 应失败', async () => {
    let caught = null;
    try {
      await http.post('/behavior/report', { productId: 1 });
    } catch (e) { caught = e; }
    expect(caught).to.exist;
    log.ok(`缺 type 正确拒绝(${caught.code})`);
  });
});