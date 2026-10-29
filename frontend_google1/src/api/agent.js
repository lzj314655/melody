/**
 * 智能助手 AI 服务客户端（独立于商城后端的 Python FastAPI 服务）
 * - SSE 流式对话用原生 fetch（axios 不支持 ReadableStream）
 * - 会话 id 存 localStorage，重开浏览器可恢复上下文（服务端 MySQL 持久化）
 * 默认本机裸机服务；Docker 构建注入 VITE_AGENT_BASE=/agent 走 nginx 反代
 */

const AGENT_BASE = import.meta.env.VITE_AGENT_BASE || "http://localhost:8100";
const SESSION_KEY = "agent_session_id";

export function getAgentSessionId() {
  let sid = localStorage.getItem(SESSION_KEY);
  if (!sid) {
    sid =
      Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
    localStorage.setItem(SESSION_KEY, sid);
  }
  return sid;
}

export function resetAgentSessionId(sid) {
  localStorage.setItem(SESSION_KEY, sid || "");
}

function authHeaders() {
  const token = localStorage.getItem("access_token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** 拉取会话历史（含工具调用记录） */
export async function fetchChatHistory(sessionId) {
  const resp = await fetch(
    `${AGENT_BASE}/api/chat/history?sessionId=${encodeURIComponent(sessionId)}`,
    { headers: authHeaders() }
  );
  if (!resp.ok) throw new Error("历史加载失败");
  const body = await resp.json();
  return body.messages || [];
}

/** 新开对话：拿一个新的 sessionId */
export async function newAgentSession() {
  const resp = await fetch(`${AGENT_BASE}/api/chat/new`, { method: "POST" });
  if (!resp.ok) throw new Error("新建会话失败");
  const body = await resp.json();
  resetAgentSessionId(body.sessionId);
  return body.sessionId;
}

export async function fetchAgentHealth() {
  const resp = await fetch(`${AGENT_BASE}/api/health`);
  if (!resp.ok) return null;
  return resp.json();
}

/**
 * SSE 流式对话
 * @param callbacks {onDelta(text), onTool({name,label}), onDone(), onError(message)}
 */
export async function streamChat({ sessionId, message, onDelta, onTool, onDone, onError, signal }) {
  let resp;
  try {
    resp = await fetch(`${AGENT_BASE}/api/chat`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ sessionId, message }),
      signal,
    });
  } catch (e) {
    onError && onError("无法连接智能助手服务（请确认 melody-agent 已启动）");
    return;
  }
  if (!resp.ok) {
    let msg = `请求失败（HTTP ${resp.status}）`;
    try {
      const j = await resp.json();
      if (j && j.detail) msg = j.detail;
    } catch (_) { /* ignore */ }
    onError && onError(msg);
    return;
  }

  const reader = resp.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buf = "";
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buf += decoder.decode(value, { stream: true });
      let idx;
      while ((idx = buf.indexOf("\n\n")) >= 0) {
        const block = buf.slice(0, idx);
        buf = buf.slice(idx + 2);
        const line = block
          .split("\n")
          .find((l) => l.startsWith("data:"));
        if (!line) continue;
        let ev;
        try {
          ev = JSON.parse(line.slice(5).trim());
        } catch (_) {
          continue;
        }
        if (ev.type === "delta") onDelta && onDelta(ev.content || "");
        else if (ev.type === "tool") onTool && onTool(ev);
        else if (ev.type === "error") onError && onError(ev.message || "服务异常");
        else if (ev.type === "done") onDone && onDone();
      }
    }
    onDone && onDone(); // 流意外结束也兜底收尾
  } catch (e) {
    if (e && e.name === "AbortError") return;
    onError && onError("连接中断，请重试");
  }
}
