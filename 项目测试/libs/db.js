/**
 * DB 直连 - 用于测试断言或自检
 *
 * 用法:
 *   import { db } from '../libs/db.js';
 *   const [rows] = await db.query('SELECT COUNT(*) c FROM t_product');
 *   expect(rows[0].c).to.be.greaterThan(50);
 */

import mysql from 'mysql2/promise';
import { ENV } from '../config/env.js';

let pool = null;

export function getPool() {
  if (!pool) {
    pool = mysql.createPool({
      host: ENV.DB.host,
      port: ENV.DB.port,
      user: ENV.DB.user,
      password: ENV.DB.password,
      database: ENV.DB.database,
      waitForConnections: true,
      connectionLimit: 5,
    });
  }
  return pool;
}

export const db = {
  query(sql, params) {
    return getPool().query(sql, params);
  },
  async close() {
    if (pool) { await pool.end(); pool = null; }
  },
};

/**
 * 常用自检 SQL
 */
export const CHECKS = {
  productCount: 'SELECT COUNT(*) c FROM t_product',
  remoteImageCount: `SELECT COUNT(*) c FROM t_product WHERE cover_image LIKE 'http%'`,
  localImageCount: `SELECT COUNT(*) c FROM t_product WHERE cover_image LIKE '/img/%'`,
  orderCount: 'SELECT COUNT(*) c FROM t_order',
  couponCount: 'SELECT COUNT(*) c FROM t_coupon',
  behaviorCount: 'SELECT COUNT(*) c FROM user_behavior_log',
};