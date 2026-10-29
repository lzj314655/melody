"""MySQL 连接管理（pymysql）。聊天会话/消息持久化到商城 melody_shop 库。"""
import logging

import pymysql

from .config import settings

log = logging.getLogger(__name__)

_conn: pymysql.Connection | None = None


def get_conn() -> pymysql.Connection:
    """取单例连接，断线自动重连（pymysql ping）。"""
    global _conn
    if _conn is None:
        _conn = pymysql.connect(
            host=settings.db_host,
            port=settings.db_port,
            user=settings.db_user,
            password=settings.db_password,
            database=settings.db_name,
            charset="utf8mb4",
            autocommit=True,
        )
    else:
        _conn.ping(reconnect=True)
    return _conn


def query(sql: str, args: tuple = ()) -> list[dict]:
    with get_conn().cursor(pymysql.cursors.DictCursor) as cur:
        cur.execute(sql, args)
        return cur.fetchall()


def execute(sql: str, args: tuple = ()) -> int:
    with get_conn().cursor() as cur:
        affected = cur.execute(sql, args)
    return affected


def ensure_tables() -> None:
    """服务启动时幂等建表（DDL 与 sql/chat-tables.sql 一致）。"""
    ddl = [
        """
        CREATE TABLE IF NOT EXISTS `t_chat_session` (
          `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
          `session_id` VARCHAR(64)  NOT NULL,
          `user_id`    BIGINT       NULL,
          `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
          `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          PRIMARY KEY (`id`),
          UNIQUE KEY `uk_session_id` (`session_id`),
          KEY `idx_user_id` (`user_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
        """,
        """
        CREATE TABLE IF NOT EXISTS `t_chat_message` (
          `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
          `session_id`  VARCHAR(64)  NOT NULL,
          `role`        VARCHAR(16)  NOT NULL,
          `content`     TEXT         NULL,
          `tool_name`   VARCHAR(64)  NULL,
          `tool_args`   TEXT         NULL,
          `tool_result` TEXT         NULL,
          `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
          PRIMARY KEY (`id`),
          KEY `idx_session` (`session_id`, `id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
        """,
    ]
    for s in ddl:
        execute(s)
    log.info("[db] chat tables ready (melody_shop.t_chat_session / t_chat_message)")
