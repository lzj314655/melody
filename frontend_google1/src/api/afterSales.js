import http from "./http";

/**
 * 售后模块 API：
 * - 后台管理：Java /api/admin/after-sales
 * - 前台对话：FastAPI /api/after-sales/chat（SSE）
 */

const AGENT_BASE = import.meta.env.VITE_AGENT_BASE || "http://localhost:8100";
const SESSION_KEY = "melody_after_sale_session";

export function getAfterSaleSessionId() {
  let sid = localStorage.getItem(SESSION_KEY);
  if (!sid) {
    sid = `as-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
    localStorage.setItem(SESSION_KEY, sid);
  }
  return sid;
}

function authHeaders() {
  const token = localStorage.getItem("access_token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

// ========== 后台 ==========
export const fetchAfterSaleTickets = async (status = "ALL") => {
  return await http.get("/admin/after-sales/list", { params: { status } });
};

export const fetchAfterSaleTicket = async (id) => {
  return await http.get(`/admin/after-sales/${id}`);
};

export const decideAfterSaleTicket = async (id, { action, reason, amount }) => {
  return await http.post(`/admin/after-sales/${id}/decision`, { action, reason, amount });
};

// ========== 前台 ==========
export const fetchMyAfterSaleTickets = async () => {
  return await http.get("/after-sales/tickets");
};

export const fetchMyAfterSaleTicket = async (id) => {
  return await http.get(`/after-sales/tickets/${id}`);
};

/**
 * 售后 SSE 对话。
 * callbacks: onAgent({node,label}), onReply(text), onMeta(data), onError(msg), onDone()
 */
export async function streamAfterSaleChat({
  orderId,
  message,
  onAgent,
  onReply,
  onMeta,
  onError,
  onDone,
  signal,
}) {
  const sessionId = getAfterSaleSessionId();
  let resp;
  try {
    resp = await fetch(`${AGENT_BASE}/api/after-sales/chat`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ sessionId, message, orderId }),
      signal,
    });
  } catch (e) {
    onError?.("无法连接售后 Agent 服务（请确认 melody-agent 已启动）");
    return;
  }
  if (!resp.ok) {
    let msg = `售后请求失败（HTTP ${resp.status}）`;
    try {
      const j = await resp.json();
      if (j?.detail) msg = j.detail;
    } catch { /* ignore */ }
    onError?.(msg);
    return;
  }

  const reader = resp.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buf = "";
  let finished = false;
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buf += decoder.decode(value, { stream: true });
      let idx;
      while ((idx = buf.indexOf("\n\n")) >= 0) {
        const block = buf.slice(0, idx);
        buf = buf.slice(idx + 2);
        const line = block.split("\n").find((l) => l.startsWith("data:"));
        if (!line) continue;
        let ev;
        try {
          ev = JSON.parse(line.slice(5).trim());
        } catch {
          continue;
        }
        if (ev.type === "agent") onAgent?.(ev);
        else if (ev.type === "reply") onReply?.(ev.content || "");
        else if (ev.type === "meta") onMeta?.(ev);
        else if (ev.type === "error") onError?.(ev.message || "售后处理异常");
        else if (ev.type === "done") {
          finished = true;
          onDone?.();
        }
      }
    }
    if (!finished) onDone?.();
  } catch (e) {
    if (e?.name === "AbortError") return;
    onError?.("售后连接中断，请重试");
  }
}
