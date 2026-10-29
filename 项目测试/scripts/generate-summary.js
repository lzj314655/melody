/**
 * 自定义汇总报告 — 读 mocha JSON + DB 自检,产出 summary.md
 *
 * 用法:
 *   npm run test:report        # 生成 reports/json/output.json
 *   npm run test:summary       # 读 JSON + DB 自检,产出 summary
 */

import fs from 'fs';
import path from 'path';
import mysql from 'mysql2/promise';
import { ENV } from '../config/env.js';

const today = new Date().toISOString().slice(0, 10);
const outDir = path.resolve('reports/summary', today);
fs.mkdirSync(outDir, { recursive: true });

// 1. 找最新的 JSON 报告(从 mochawesome 输出目录找)
const jsonCandidates = [
  path.resolve('reports/html/mochawesome.json'),
  path.resolve('reports/json/output.json'),
];
let jsonFile = null;
for (const f of jsonCandidates) {
  if (fs.existsSync(f)) { jsonFile = f; break; }
}
if (!jsonFile) {
  // 全 reports 目录搜
  const root = path.resolve('reports');
  if (fs.existsSync(root)) {
    const walk = (dir) => {
      for (const e of fs.readdirSync(dir)) {
        const p = path.join(dir, e);
        if (fs.statSync(p).isDirectory()) walk(p);
        else if (e.endsWith('.json') && (e.includes('mochawesome') || e === 'output.json')) jsonFile = p;
      }
    };
    walk(root);
  }
}

let stats = { total: 0, passed: 0, failed: 0, duration: 0, failures: [] };
if (jsonFile) {
  const r = JSON.parse(fs.readFileSync(jsonFile, 'utf-8'));
  stats.total = r.stats?.tests || 0;
  stats.passed = r.stats?.passes || 0;
  stats.failed = r.stats?.failures || 0;
  stats.duration = r.stats?.duration || 0;
  // mochawesome 失败项深度嵌套,递归所有 suite/tests 找 fail=true 的项
  const collect = (node, titles) => {
    if (node.tests) {
      for (const t of node.tests) {
        if (t.fail === true) {
          stats.failures.push({
            title: titles.concat(t.title).join(' > '),
            err: (t.err?.message || '').slice(0, 200),
          });
        }
      }
    }
    if (node.suites) for (const c of node.suites) collect(c, titles.concat(node.title || ''));
  };
  if (r.results?.[0]) collect(r.results[0], []);
}

// 2. DB 自检
let dbChecks = '';
const pool = mysql.createPool({ ...ENV.DB, connectionLimit: 2 });
try {
  const sqls = [
    ['t_product 总数', 'SELECT COUNT(*) c FROM t_product'],
    ['cover_image 远程图残留', "SELECT COUNT(*) c FROM t_product WHERE cover_image LIKE 'http%'"],
    ['cover_image 本地图', "SELECT COUNT(*) c FROM t_product WHERE cover_image LIKE '/img/%'"],
    ['t_order 总数', 'SELECT COUNT(*) c FROM t_order'],
    ['t_coupon 总数', 'SELECT COUNT(*) c FROM t_coupon'],
    ['user_behavior_log 总数', 'SELECT COUNT(*) c FROM user_behavior_log'],
  ];
  for (const [name, sql] of sqls) {
    const [rows] = await pool.query(sql);
    dbChecks += `| ${name} | ${rows[0].c} |\n`;
  }
} catch (e) {
  dbChecks += `| DB 自检失败 | ${e.message} |\n`;
} finally {
  await pool.end();
}

// 3. 输出 summary.md
const lines = [];
lines.push(`# 测试报告汇总 — ${today}\n`);
lines.push(`## 1. 总体统计\n`);
lines.push(`| 项 | 值 |`);
lines.push(`|---|---|`);
lines.push(`| 总用例数 | ${stats.total} |`);
lines.push(`| 通过 | ${stats.passed} |`);
lines.push(`| 失败 | ${stats.failed} |`);
lines.push(`| 耗时 | ${(stats.duration / 1000).toFixed(2)}s |`);
lines.push(`| 通过率 | ${stats.total ? ((stats.passed / stats.total) * 100).toFixed(1) : 0}% |`);
lines.push(``);

lines.push(`## 2. 失败用例\n`);
if (stats.failures.length === 0) {
  lines.push(`无 ✅\n`);
} else {
  lines.push(`| 用例 | 错误 |`);
  lines.push(`|---|---|`);
  stats.failures.forEach((f) => {
    lines.push(`| ${f.title.replace(/\|/g, '\\|')} | ${f.err.replace(/\n/g, ' ').replace(/\|/g, '\\|')} |`);
  });
}
lines.push(``);

lines.push(`## 3. 数据库自检\n`);
lines.push(`| 检查项 | 结果 |`);
lines.push(`|---|---|`);
lines.push(dbChecks);
lines.push(``);

lines.push(`## 4. 备注\n`);
lines.push(`- 报告时间: ${new Date().toISOString()}`);
lines.push(`- 后端: ${ENV.API_BASE}`);
lines.push(`- 数据源 JSON: ${jsonFile || '未生成'}`);

fs.writeFileSync(path.join(outDir, 'summary.md'), lines.join('\n'));
console.log(`✓ summary 写入: ${path.join(outDir, 'summary.md')}`);