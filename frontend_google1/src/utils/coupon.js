// 统一展示券面值的辅助（避免 0.8 折券显示成"¥0"）
// type === '折扣' → "8.0 折"
// 其它（满减）    → "¥30"
export function couponFace(type, discount) {
  const d = Number(discount || 0);
  if (type === '折扣') {
    // 折扣字段是 0~1 的小数，0.8 = 八折；展示为 "8 折"
    if (d > 0 && d < 1) return { prefix: '', value: (d * 10).toFixed(1).replace(/\.0$/, ''), suffix: '折' };
    return { prefix: '', value: String(d), suffix: '折' };
  }
  // 满减 / 其它：直接当金额
  return { prefix: '¥', value: d.toFixed(d % 1 === 0 ? 0 : 1), suffix: '' };
}