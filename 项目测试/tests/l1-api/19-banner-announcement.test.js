/**
 * L1 API smoke - 19 R10 回归
 *
 * 覆盖(本轮修复):
 *   GET /api/banner/list                  轮播图(R10-1)
 *   GET /api/announcement/list            公告(R10-2)
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import { http } from '../../libs/http-client.js';
import { log } from '../../libs/logger.js';

describe('L1-19 R10 回归(公告 + 轮播)', () => {
  it('19.01 /api/banner/list 应 200 且返回数组', async () => {
    const data = await http.get('/banner/list');
    expect(data).to.be.an('array');
    log.ok(`banner 数=${data.length}`);
  });

  it('19.02 /api/announcement/list 应 200 且返回数组', async () => {
    const data = await http.get('/announcement/list');
    expect(data).to.be.an('array');
    log.ok(`announcement 数=${data.length}`);
  });

  it('19.03 banner 数据结构', async () => {
    const data = await http.get('/banner/list');
    if (data.length > 0) {
      const b = data[0];
      expect(b).to.have.property('id');
      expect(b).to.have.property('title');
      expect(b).to.have.property('imageUrl');
      log.ok(`banner[0]: ${b.title}`);
    } else {
      log.warn('banner 数据为空(可能是 init 未跑 banner 插入)');
    }
  });

  it('19.04 announcement 数据结构', async () => {
    const data = await http.get('/announcement/list');
    if (data.length > 0) {
      const a = data[0];
      expect(a).to.have.property('id');
      expect(a).to.have.property('title');
      expect(a).to.have.property('content');
      log.ok(`announcement[0]: ${a.title}`);
    } else {
      log.warn('announcement 数据为空');
    }
  });
});