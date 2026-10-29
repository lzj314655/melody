import axios from "axios";

// 统一 baseURL：所有 /api/xxx 直接写相对路径，axios 会自动拼上
// 默认本机裸机开发地址；Docker 构建时注入 VITE_API_BASE=/api 走 nginx 同源反代
const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080/api";
const http = axios.create({
  baseURL: API_BASE,
  timeout: 15000,
  withCredentials: false,
});

// 用户 token key（前台登录）
const USER_TOKEN_KEY = "access_token";
// 管理员 token key（后台登录）—— 与用户 token 独立存储
const ADMIN_TOKEN_KEY = "admin_token";

// 根据 url 路径判断用哪个 token
// 不仅匹配 /admin/xxx，也要兼容 /xxx/admin/yyy（如 /recommend/admin/refresh 这种后台接口散落在业务域里的情况）
const pickTokenKey = (url = "") => {
  const path = String(url);
  return path.startsWith("/admin") || path.includes("/admin/")
    ? ADMIN_TOKEN_KEY
    : USER_TOKEN_KEY;
};

// 初始化时尝试从两个 key 恢复 token（user 优先）
const cachedUser = localStorage.getItem(USER_TOKEN_KEY);
if (cachedUser) {
  http.defaults.headers.common.Authorization = `Bearer ${cachedUser}`;
}
const cachedAdmin = localStorage.getItem(ADMIN_TOKEN_KEY);
if (cachedAdmin) {
  http.defaults.headers.common["X-Admin-Token"] = cachedAdmin;
}

// 请求拦截：始终附带最新 token
http.interceptors.request.use((config) => {
  const key = pickTokenKey(config.url || "");
  const t = localStorage.getItem(key);
  if (t) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${t}`;
  }
  return config;
});

// 响应拦截：后端约定 { code:200, msg, data }
http.interceptors.response.use(
  (resp) => {
    const body = resp.data;
    // 标准 R<T> 包装
    if (body && typeof body === "object" && "code" in body) {
      if (body.code === 200) {
        return body.data; // 直接返回 data，调用方少一层
      }
      // 401 token 失效，根据请求路径踢回对应登录
      if (body.code === 401) {
        const key = pickTokenKey(resp.config?.url || "");
        localStorage.removeItem(key);
        const isAdmin = key === ADMIN_TOKEN_KEY;
        const back = encodeURIComponent(window.location.pathname + window.location.search);
        window.location.href = isAdmin
          ? `/admin/login?redirect=${back}`
          : `/login?redirect=${back}`;
        return Promise.reject(new Error(body.msg || "未登录"));
      }
      // 其它业务错误抛 BizException
      const err = new Error(body.msg || "请求失败");
      err.code = body.code;
      return Promise.reject(err);
    }
    // 非标准响应（理论上不该有）
    return body;
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      const key = pickTokenKey(err.config?.url || "");
      localStorage.removeItem(key);
      const isAdmin = key === ADMIN_TOKEN_KEY;
      window.location.href = isAdmin ? "/admin/login" : "/login";
    }
    return Promise.reject(err);
  }
);

/**
 * 设置 / 清除用户 token 到 axios 默认头
 */
export const setAuthToken = (token) => {
  if (token) {
    http.defaults.headers.common.Authorization = `Bearer ${token}`;
  } else {
    delete http.defaults.headers.common.Authorization;
  }
};

/**
 * 设置 / 清除管理员 token（独立 key，不影响前台用户）
 */
export const setAdminToken = (token) => {
  if (token) {
    localStorage.setItem(ADMIN_TOKEN_KEY, token);
  } else {
    localStorage.removeItem(ADMIN_TOKEN_KEY);
  }
};

export const getAdminToken = () => localStorage.getItem(ADMIN_TOKEN_KEY) || "";

export { USER_TOKEN_KEY, ADMIN_TOKEN_KEY };
export default http;
