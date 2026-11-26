"""DeepSeek HTTP 客户端。

只负责「发一次请求」和「解析一次响应」，分片、重试、降级策略都在 ``analyzer`` 里，
换模型或换厂商时只需要改这个文件。
"""

from __future__ import annotations

import json
from typing import Any

import httpx

from app.config import Settings

# 与 Java 版 LLMService 保持一致的采样参数
TEMPERATURE = 0.3
MAX_TOKENS = 4096


async def request_chat(
    client: httpx.AsyncClient,
    settings: Settings,
    messages: list[dict[str, str]],
) -> str:
    """发一次 chat/completions，返回模型正文。

    网络异常、超时、非 200、响应结构不对都直接抛异常，由调用方决定是否重试。
    """
    body = {
        "model": settings.model,
        "messages": messages,
        "temperature": TEMPERATURE,
        "max_tokens": MAX_TOKENS,
        "response_format": {"type": "json_object"},
    }
    response = await client.post(
        f"{settings.base_url}/v1/chat/completions",
        headers={
            "Authorization": f"Bearer {settings.api_key}",
            "Content-Type": "application/json",
        },
        json=body,
    )

    if response.status_code != 200:
        raise RuntimeError(f"HTTP {response.status_code}: {response.text[:300]}")

    data = response.json()
    choices = data.get("choices") or []
    if not choices:
        raise RuntimeError("响应缺少 choices")

    content = (choices[0].get("message") or {}).get("content") or ""
    if not content.strip():
        raise RuntimeError("响应内容为空")
    return content


# 顶层包装键：response_format=json_object 强制返回对象，模型会自己发明键名
# （实测出现过 {"analysis":[...]} 与 {"analyses":[...]}）
_WRAPPER_KEYS = ("items", "analyses", "analysis")


def parse_items(content: str) -> list[Any] | None:
    """解析模型输出，返回条目列表；解析不出返回 None。

    顺序：直接 json.loads → 剥 ``` 包裹 → 顶层取数组。
    顶层是对象时先试已知包装键（items / analyses / analysis），再退化为「取第一个 list 值」。
    注意顶层 ``analysis`` 是列表，条目内的 ``analysis`` 是字符串，两者不能混淆。
    """
    raw = content.strip()

    parsed = _loads(raw)
    if parsed is None:
        unwrapped = _strip_fence(raw)
        if unwrapped is not None:
            parsed = _loads(unwrapped)

    if parsed is None:
        return None
    if isinstance(parsed, list):
        return parsed
    if isinstance(parsed, dict):
        for key in _WRAPPER_KEYS:
            value = parsed.get(key)
            if isinstance(value, list):
                return value
        # 兜底：不认识的包装键，取第一个 list 类型的值
        for value in parsed.values():
            if isinstance(value, list):
                return value
        if "recordId" in parsed:
            # 模型只回了一条对象，包成列表
            return [parsed]
    return None


def _loads(text: str) -> Any:
    """json.loads 的安全版，失败返回 None"""
    try:
        return json.loads(text)
    except (TypeError, ValueError):
        return None


def _strip_fence(raw: str) -> str | None:
    """剥掉 ```json ... ``` 包裹，找不到包裹返回 None"""
    start = raw.find("```")
    end = raw.rfind("```")
    if start == -1 or end <= start:
        return None

    block = raw[start + 3:end].strip()
    if block[:4].lower() == "json":
        block = block[4:].lstrip()
    return block or None
