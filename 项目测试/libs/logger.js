/**
 * 统一日志 - 测试输出格式化
 *
 * 用法:
 *   import { log } from '../libs/logger.js';
 *   log.ok('登录成功');
 *   log.fail('加购失败', e.message);
 *   log.section('==== 鉴权矩阵 ====');
 */

const icons = {
  ok: '✅',
  fail: '❌',
  warn: '⚠️ ',
  info: 'ℹ️ ',
  section: '\n──────',
};

export const log = {
  ok(msg)      { console.log(`${icons.ok}  ${msg}`); },
  fail(msg, detail) { console.log(`${icons.fail} ${msg}${detail ? ` (${detail})` : ''}`); },
  warn(msg)    { console.log(`${icons.warn} ${msg}`); },
  info(msg)    { console.log(`${icons.info}  ${msg}`); },
  section(msg) { console.log(`${icons.section} ${msg} ${icons.section}`); },
};