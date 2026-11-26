"""服务配置：全部从环境变量读取，API Key 不写进代码、不落盘。"""

from __future__ import annotations

import os
from dataclasses import dataclass

DEFAULT_BASE_URL = "https://api.deepseek.com"
DEFAULT_MODEL = "deepseek-chat"
DEFAULT_PORT = 8000


@dataclass(frozen=True)
class Settings:
    """一次调用所需配置的不可变快照"""

    api_key: str
    base_url: str
    model: str
    port: int


def load_settings() -> Settings:
    """每次调用现读环境变量，便于不重启进程就切换 key / 模型 / 端口"""
    return Settings(
        api_key=os.getenv("DEEPSEEK_API_KEY", "").strip(),
        base_url=(os.getenv("DEEPSEEK_BASE_URL") or DEFAULT_BASE_URL).strip().rstrip("/"),
        model=(os.getenv("DEEPSEEK_MODEL") or DEFAULT_MODEL).strip(),
        port=_read_port(),
    )


def _read_port() -> int:
    """PORT 非法时退回默认端口，不因环境变量写错而起不来"""
    raw = os.getenv("PORT", "").strip()
    if not raw:
        return DEFAULT_PORT
    try:
        return int(raw)
    except ValueError:
        return DEFAULT_PORT
