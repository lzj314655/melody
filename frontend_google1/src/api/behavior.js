// F-R-001 行为埋点——直接打到真实后端 /api/behavior/report（公开）
// 不带 token 也能上报，后端允许匿名
//
// 类型约定（与后端 BehaviorTypeEnum 一致）：
//   VIEW / ADD_CART / FAVORITE / ORDER / REVIEW
// 全项目直接用字符串字面量，没有 BEHAVIOR_TYPES 常量——避免拼写错误靠调用方自觉。

import http from "@/api/http";

/**
 * 静默上报一条用户行为
 * 关键点：fetch 必须显式不带 credentials（不能用默认的 omit 在所有浏览器一致）
 * 否则触发 CORS preflight 时浏览器会带 credentials='include'，与后端
 * Access-Control-Allow-Credentials=false 冲突。
 */
export const reportBehavior = (type, productId, extra = {}) => {
  // 后端 BehaviorReportRequest.extra 是 String（contract 约定）
  // 后端 Controller 内部再 objectMapper.readValue 反序列化为 Map<String, Object>
  // 所以前端必须把 extra 对象序列化成 JSON 字符串再发，否则反序列化失败 → 行为日志根本写不进库
  const payload = {
    behaviorType: type,
    productId,
    extra: JSON.stringify(extra || {}),
    timestamp: new Date().toISOString(),
  };

  if (import.meta.env.DEV) {
    console.log(`[Behavior] ${type} product=${productId}`, extra);
  }

  const url = "http://localhost:8080/api/behavior/report";
  const body = JSON.stringify(payload);

  // 必须带 Authorization：后端用 SecurityUtils.currentId() 把 userId 写进 user_behavior_log.user_id
  // 不带 token → user_id=null → 这条行为算"游客的"，任何登录用户的个性化推荐都不会用到它
  // 这里不能走 axios 拦截器：要走 keepalive（页面卸载也保留请求）+ fetch 才行
  const token = localStorage.getItem("access_token");
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;

  // 直接用 fetch + keepalive。
  // keepalive=true 让浏览器在页面卸载时也保留请求直到完成。
  // 这里不能用 sendBeacon：它在某些 Chromium 客户端会带 implicit credentials='include'，
  // 与服务端 Access-Control-Allow-Credentials=false 冲突造成 preflight CORS 失败。
  try {
    fetch(url, {
      method: "POST",
      headers,
      body,
      keepalive: true,
      credentials: "omit",
      mode: "cors",
    }).catch(() => { /* silent */ });
  } catch (e) { /* silent */ }
};

/**
 * F-U-XXX 拉当前登录用户最近的浏览商品（去重 productId，按最近浏览时间倒序）
 */
export const fetchRecentViewsApi = async (limit = 20) => {
  return await http.get(`/behavior/recent-views?limit=${limit}`);
};
