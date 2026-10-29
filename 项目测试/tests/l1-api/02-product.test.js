/**
 * L1 API smoke - 02 商品模块
 *
 * 覆盖:
 *   GET /api/product/categories          分类列表(公开)
 *   GET /api/product/list                商品列表(公开)
 *   GET /api/product/detail/{id}         商品详情(公开)
 *   GET /api/sku?productId=              SKU 列表(公开)
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http } from '../../libs/http-client.js';
import { ENV } from '../../config/env.js';
import { log } from '../../libs/logger.js';

describe('L1-02 商品模块', () => {
  it('02.01 商品分类列表(公开)', async () => {
    const data = await http.get('/product/categories');
    expect(data).to.be.an('array').with.length.greaterThan(0);
    log.ok(`分类数=${data.length}`);
  });

  it('02.02 商品列表(无参数)', async () => {
    const data = await http.get('/product/list');
    expect(data).to.be.an('array').with.length.greaterThan(0);
    log.ok(`商品数=${data.length}`);
  });

  it('02.03 商品列表(带 categoryId)', async () => {
    const data = await http.get('/product/list', { headers: {} });
    // 服务端从 query 读,这里用 params 单独传
    const url = '/product/list?categoryId=1';
    const data2 = await http.get(url);
    expect(data2).to.be.an('array');
    log.ok(`categoryId=1 分类下商品数=${data2.length}`);
  });

  it('02.04 商品详情(存在的 id)', async () => {
    const data = await http.get('/product/detail/1');
    expect(data).to.have.property('id', 1);
    expect(data).to.have.property('name');
    log.ok(`商品详情: ${data.name}`);
  });

  it('02.05 商品详情(不存在的 id 应失败)', async () => {
    let caught = null;
    try {
      await http.get('/product/detail/99999');
    } catch (e) {
      caught = e;
    }
    expect(caught).to.exist;
    log.ok(`不存在的商品正确拒绝(${caught.code || 'err'})`);
  });

  it('02.06 SKU 列表 by productId', async () => {
    const data = await http.get('/sku?productId=1');
    expect(data).to.be.an('array');
    log.ok(`SKU 数=${data.length}`);
  });
});