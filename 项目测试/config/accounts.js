/**
 * 测试账号 — 从 backend/sql/data-init.sql 反查
 *
 * ⚠️ data-init.sql 中所有 user / admin 都用了同一个 BCrypt hash:
 *    $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
 *    对应明文: password (Spring Security 文档示例 hash)
 *
 * 真实账号:
 *   user: 13800138001 / 13800138002 / 13800138003
 *   admin: admin / operator
 *   密码统一: password
 */

export const ACCOUNTS = {
  // 普通用户 #1 - 13800138001 "快乐星猫" V1
  user1: {
    phone: '13800138001',
    password: 'password',
  },
  // 普通用户 #2 - 13800138002 "美食收藏家" V3
  user2: {
    phone: '13800138002',
    password: 'password',
  },
  // 普通用户 #3 - 13800138003 "新用户" V0
  user3: {
    phone: '13800138003',
    password: 'password',
  },
  // 后台 SUPER_ADMIN
  adminSuper: {
    username: 'admin',
    password: 'admin123',
  },
  // 后台 OPERATOR
  adminOperator: {
    username: 'operator',
    password: '123456',
  },
};

/**
 * 临时注册账号 - 避免污染 data-init.sql 数据
 * 用时间戳后缀,跑完即弃
 * 注册需要 code: '123456'(PRD §4.1.1 演示验证码)
 */
export function genTempAccount() {
  const ts = Date.now();
  return {
    phone: `139${String(ts).slice(-8)}`,
    password: 'Test@123',
    code: '123456',
    nickname: `test_${String(ts).slice(-4)}`,
  };
}