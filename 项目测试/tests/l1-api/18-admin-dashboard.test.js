/**
 * L1 API smoke - 18 后台数据看板
 *
 * 覆盖:
 *   GET /api/admin/dashboard/kpi
 *   GET /api/admin/dashboard/gmv-trend
 *   GET /api/admin/dashboard/order-trend
 *   GET /api/admin/dashboard/top-products
 *   GET /api/admin/dashboard/category-share
 *   GET /api/admin/dashboard/logs
 *   POST /api/recommend/admin/refresh       手动重算推荐
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http, AuthError } from '../../libs/http-client.js';
import { ensureAdmin } from '../../libs/auth.js';
import { ACCOUNTS } from '../../config/accounts.js';
import { log } from '../../libs/logger.js';

describe('L1-18 后台看板', () => {
  let token;
  before(async () => {
    const a = await ensureAdmin(ACCOUNTS.adminOperator.username, ACCOUNTS.adminOperator.password);
    token = a.token;
  });

  it('18.01 KPI 指标', async () => {
    const data = await http.get('/admin/dashboard/kpi', { token });
    expect(data).to.exist;
    // 验证 conversionRate 是动态计算(不是硬编码 12.8%)
    expect(data.conversionRate).to.be.a('string').that.matches(/^\d+(\.\d+)?%$/);
    expect(data.conversionRate).to.not.equal('12.8%', '修复后不应再硬编码');
    log.ok(`KPI: conversionRate=${data.conversionRate} GMV=${data.gmv}`);
  });

  it('18.02 GMV 趋势', async () => {
    const data = await http.get('/admin/dashboard/gmv-trend', { token });
    expect(data).to.exist;
    log.ok(`GMV 趋势条数=${(data || []).length}`);
  });

  it('18.03 订单趋势', async () => {
    const data = await http.get('/admin/dashboard/order-trend', { token });
    expect(data).to.exist;
    log.ok(`订单趋势条数=${(data || []).length}`);
  });

  it('18.04 Top 商品', async () => {
    const data = await http.get('/admin/dashboard/top-products', { token });
    expect(data).to.exist;
    log.ok(`Top 商品数=${(data || []).length}`);
  });

  it('18.05 分类占比', async () => {
    const data = await http.get('/admin/dashboard/category-share', { token });
    expect(data).to.exist;
    log.ok(`分类数=${(data || []).length}`);
  });

  it('18.06 操作日志', async () => {
    const data = await http.get('/admin/dashboard/logs', { token });
    expect(data).to.exist;
    log.ok(`操作日志数=${(data.records || data || []).length}`);
  });

  it('18.07 手动重算推荐', async () => {
    const data = await http.post('/recommend/admin/refresh', {}, { token });
    expect(data).to.exist;
    log.ok('推荐重算成功');
  });
});