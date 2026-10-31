"""配置加载：从 melody-agent/.env 读取，缺省值与商城后端保持一致。"""
import os
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent
load_dotenv(BASE_DIR / ".env")


def _get(key: str, default: str = "") -> str:
    return os.getenv(key, default).strip()


def _get_int(key: str, default: int) -> int:
    try:
        return int(os.getenv(key, str(default)))
    except ValueError:
        return default


class Settings:
    # DeepSeek（openai SDK 兼容）
    api_key: str = _get("DEEPSEEK_API_KEY")
    base_url: str = _get("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
    model: str = _get("DEEPSEEK_MODEL", "deepseek-chat")

    # 商城 Spring Boot 后端
    backend_url: str = _get("BACKEND_URL", "http://localhost:8080")

    # AI 服务自身
    port: int = _get_int("PORT", 8100)
    cors_origins: list[str] = [
        o.strip() for o in _get("CORS_ORIGINS", "http://localhost:3000,http://localhost:5173").split(",") if o.strip()
    ]

    # MySQL（与商城同库，聊天记录表建在这里）
    db_host: str = _get("DB_HOST", "localhost")
    db_port: int = _get_int("DB_PORT", 3306)
    db_user: str = _get("DB_USER", "root")
    db_password: str = _get("DB_PASSWORD", "123456")
    db_name: str = _get("DB_NAME", "melody_shop")

    # RAG
    kb_dir: str = str(BASE_DIR / _get("KB_DIR", "knowledge"))
    data_dir: str = str(BASE_DIR / _get("DATA_DIR", "data"))
    chroma_dir: str = str(BASE_DIR / _get("CHROMA_DIR", "data/chroma"))  # 兼容旧配置，实际数据在 data/
    kb_top_k: int = _get_int("KB_TOP_K", 3)

    # Agent
    context_window: int = _get_int("CONTEXT_WINDOW", 20)   # LLM 滑动窗口（user/assistant 文本条数）
    max_tool_rounds: int = _get_int("MAX_TOOL_ROUNDS", 8)
    http_timeout: float = 15.0
    session_keep_messages: int = _get_int("SESSION_KEEP_MESSAGES", 500)  # 单会话落库上限

    # 售后多 Agent（LangGraph）
    after_sale_internal_token: str = _get("AFTER_SALE_INTERNAL_TOKEN")
    after_sale_enabled: bool = _get("AFTER_SALE_ENABLED", "true").lower() not in ("0", "false", "no")
    after_sale_kb_top_k: int = _get_int("AFTER_SALE_KB_TOP_K", 3)


settings = Settings()
