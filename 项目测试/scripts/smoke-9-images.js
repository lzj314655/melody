/**
 * 9 商品图抽样快速验证(独立可跑,不依赖 mocha)
 *
 * 用法: node scripts/smoke-9-images.js
 */

import axios from 'axios';
import { ENV } from '../config/env.js';

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

const client = axios.create({ timeout: 10000, validateStatus: () => true });

(async () => {
  let ok = 0, fail = 0;
  for (const s of samples) {
    for (const base of [ENV.IMG_BASE_BACKEND, ENV.IMG_BASE_VITE]) {
      const url = `${base}/img/${s.id}/00.${s.ext}`;
      try {
        const r = await client.get(url);
        const ct = r.headers['content-type'] || '';
        const expectCt = s.ext === 'jpg' ? 'image/jpeg' : 'image/png';
        const pass = r.status === 200 && ct.includes(expectCt);
        console.log(`${pass ? '✅' : '❌'} ${url} → ${r.status} ${ct} (${r.data?.length || 0}B)`);
        pass ? ok++ : fail++;
      } catch (e) {
        console.log(`❌ ${url} → ${e.message}`);
        fail++;
      }
    }
  }
  console.log(`\n======\n${ok}/${ok + fail} 通过, ${fail} 失败`);
  process.exit(fail === 0 ? 0 : 1);
})();