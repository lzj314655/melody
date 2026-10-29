/**
 * L1 API smoke - 20 静态图片(/img/**)
 *
 * 验证:
 *   - 9 个抽样商品图都返回 200 + 正确 MIME
 *   - 走 Vite 代理(3000)和后端直连(8080)都通
 *   - DB cover_image 不残留 http
 */

import { expect } from 'chai';
import { before, after, beforeEach, afterEach, describe, it } from 'mocha';
import axios from 'axios';
import { http, imgHttp } from '../../libs/http-client.js';
import { ENV } from '../../config/env.js';
import { db, CHECKS } from '../../libs/db.js';
import { log } from '../../libs/logger.js';

describe('L1-20 静态图片与 DB 自检', () => {
  // 9 抽样商品,id → 期望扩展名(从磁盘文件反查)
  const samples = [
    { id: 1,  ext: 'jpg' },
    { id: 5,  ext: 'png' },
    { id: 11, ext: 'jpg' },
    { id: 20, ext: 'png' },
    { id: 30, ext: 'png' },
    { id: 40, ext: 'png' },
    { id: 50, ext: 'png' },
    { id: 55, ext: 'jpg' },
    { id: 58, ext: 'jpg' },
  ];

  samples.forEach((s) => {
    it(`20.${s.id}/9 后端 8080 /img/${s.id}/00.${s.ext} 应 200`, async () => {
      const url = `${ENV.IMG_BASE_BACKEND}/img/${s.id}/00.${s.ext}`;
      const res = await imgHttp.get(url, { validateStatus: () => true });
      expect(res.status, `URL=${url}`).to.equal(200);
      const ct = res.headers['content-type'] || '';
      const expectCt = s.ext === 'jpg' ? 'image/jpeg' : 'image/png';
      expect(ct).to.include(expectCt);
      log.ok(`${url} → ${res.status} ${ct}`);
    });

    it(`20.${s.id}/9 Vite 3000 /img/${s.id}/00.${s.ext} 应 200`, async () => {
      const url = `${ENV.IMG_BASE_VITE}/img/${s.id}/00.${s.ext}`;
      const res = await imgHttp.get(url, { validateStatus: () => true });
      expect(res.status, `URL=${url}`).to.equal(200);
      const ct = res.headers['content-type'] || '';
      const expectCt = s.ext === 'jpg' ? 'image/jpeg' : 'image/png';
      expect(ct).to.include(expectCt);
      log.ok(`${url} → ${res.status} ${ct}`);
    });
  });

  it('20.10 DB cover_image 不残留 http', async () => {
    const [rows] = await db.query(CHECKS.remoteImageCount);
    const c = rows[0].c;
    expect(c).to.equal(0, `还有 ${c} 张远程图未迁移`);
    log.ok(`远程图残留=0`);
  });

  it('20.11 DB cover_image 本地路径数', async () => {
    const [rows] = await db.query(CHECKS.localImageCount);
    const c = rows[0].c;
    expect(c).to.be.greaterThan(0);
    log.ok(`本地图路径数=${c}`);
  });

  after(async () => { await db.close(); });
});