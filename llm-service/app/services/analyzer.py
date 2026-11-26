"""差异分析编排。

- 分片：每片最多 20 条记录，片间串行
- 重试：每片最多 2 次重试，指数退避 1s、2s
- 降级：某片请求失败或解析不出 JSON，只该片退回规则，不影响其他片
- engine：全部模型 → llm；全部规则 → rule；混合 → mixed
"""

from __future__ import annotations

import asyncio
import logging
import time
from typing import Any

import httpx

from app.config import Settings, load_settings
from app.prompts import SYSTEM_PROMPT, build_user_message
from app.schemas import AnalysisItem, AnalyzeResponse, RecordIn, TaskIn
from app.services.deepseek import parse_items, request_chat
from app.services.rules import analyze_by_rule

log = logging.getLogger("llm-service")

# 分片大小与重试策略（任务书定死，不要随意调）
CHUNK_SIZE = 20
MAX_RETRIES = 2
RETRY_BACKOFF_SECONDS: tuple[float, ...] = (1.0, 2.0)
REQUEST_TIMEOUT_SECONDS = 60.0

# 条目字段的同义词：模型不一定按契约命名（实测出现过 cause / reason / advice）
_ANALYSIS_KEYS = ("analysis", "cause", "reason")
_SUGGESTION_KEYS = ("suggestion", "advice", "recommendation")


async def analyze(
    task: TaskIn,
    records: list[RecordIn],
    settings: Settings | None = None,
) -> AnalyzeResponse:
    """分析盘点差异：能走 LLM 的走 LLM，剩下的用规则补齐"""
    started = time.perf_counter()
    cfg = settings or load_settings()

    if not records:
        # 空批次直接返回，不调用模型
        return AnalyzeResponse(items=[], engine="rule", model=None, tookMs=0)

    rule_items = analyze_by_rule(records)
    if not cfg.api_key:
        log.info("未配置 DEEPSEEK_API_KEY，%d 条记录整批走规则兜底", len(records))
        return AnalyzeResponse(
            items=rule_items,
            engine="rule",
            model=None,
            tookMs=_elapsed_ms(started),
        )

    llm_map: dict[int, AnalysisItem] = {}
    async with httpx.AsyncClient(timeout=httpx.Timeout(REQUEST_TIMEOUT_SECONDS)) as client:
        for index, chunk in enumerate(_split_chunks(records), start=1):
            llm_map.update(await _analyze_chunk(client, cfg, task, chunk, index))

    # 按入参顺序组装：模型没覆盖到的 recordId 用规则结果补
    resolved = [llm_map.get(record.recordId) for record in records]
    llm_count = sum(1 for item in resolved if item is not None)
    items = [item if item is not None else rule_items[i] for i, item in enumerate(resolved)]

    if llm_count == 0:
        engine = "rule"
    elif llm_count == len(records):
        engine = "llm"
    else:
        engine = "mixed"

    return AnalyzeResponse(
        items=items,
        engine=engine,
        model=cfg.model if llm_count > 0 else None,
        tookMs=_elapsed_ms(started),
    )


async def _analyze_chunk(
    client: httpx.AsyncClient,
    cfg: Settings,
    task: TaskIn,
    chunk: list[RecordIn],
    index: int,
) -> dict[int, AnalysisItem]:
    """分析一片记录；失败只影响本片，返回空字典表示本片走规则"""
    allowed = {record.recordId for record in chunk}
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": build_user_message(task, chunk)},
    ]

    for attempt in range(MAX_RETRIES + 1):
        try:
            content = await request_chat(client, cfg, messages)
            raw_items = parse_items(content)
            if raw_items is None:
                # 原文带进异常，保证解析失败时日志里能看到模型到底返回了什么
                raise ValueError(f"返回内容无法解析为 JSON，原文前 500 字符：{_excerpt(content)}")
        except Exception as exc:  # 网络、超时、非 200、响应结构异常、解析失败都算本次失败
            if attempt < MAX_RETRIES:
                backoff = RETRY_BACKOFF_SECONDS[min(attempt, len(RETRY_BACKOFF_SECONDS) - 1)]
                log.warning(
                    "第 %d 片第 %d 次尝试失败（%s），%.0fs 后重试",
                    index, attempt + 1, exc, backoff,
                )
                await asyncio.sleep(backoff)
                continue
            log.error("第 %d 片重试 %d 次仍失败，整片降级为规则（%s）", index, MAX_RETRIES, exc)
            return {}

        parsed = _to_items(raw_items, allowed)
        if not parsed:
            # 有返回却一条都没用上（键名/字段名不认识）：同样把原文打出来
            log.warning(
                "第 %d 片模型返回 %d 条但无一条可用，整片降级为规则；原文前 500 字符：%s",
                index, len(raw_items), _excerpt(content),
            )
        missing = allowed - set(parsed)
        if missing:
            log.info("第 %d 片模型漏返回 %d 条记录，用规则补齐: %s", index, len(missing), sorted(missing))
        return parsed

    return {}


def _excerpt(content: str, limit: int = 500) -> str:
    """取原始返回的前 500 字符，供日志定位问题"""
    text = content.strip()
    if len(text) <= limit:
        return text
    return text[:limit] + "…（已截断）"


def _to_items(raw_items: list[Any], allowed: set[int]) -> dict[int, AnalysisItem]:
    """把模型输出转成 {recordId: item}，丢弃不认识的 recordId 与缺字段的条目"""
    result: dict[int, AnalysisItem] = {}
    for raw in raw_items:
        if not isinstance(raw, dict):
            continue

        record_id = _to_int(raw.get("recordId"))
        if record_id is None or record_id not in allowed or record_id in result:
            continue

        analysis = _pick_text(raw, _ANALYSIS_KEYS)
        suggestion = _pick_text(raw, _SUGGESTION_KEYS)
        if not analysis or not suggestion:
            # 字段缺失等同没返回，交由规则补齐
            continue

        result[record_id] = AnalysisItem(
            recordId=record_id,
            analysis=analysis,
            suggestion=suggestion,
        )
    return result


def _to_int(value: Any) -> int | None:
    """容错转换 recordId（模型有时给字符串）"""
    if isinstance(value, bool):
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _as_text(value: Any) -> str:
    """容错取文本字段"""
    if value is None:
        return ""
    return str(value).strip()


def _pick_text(raw: dict[str, Any], keys: tuple[str, ...]) -> str:
    """按同义词顺序取第一个非空文本；都取不到返回空串"""
    for key in keys:
        text = _as_text(raw.get(key))
        if text:
            return text
    return ""


def _split_chunks(records: list[RecordIn]) -> list[list[RecordIn]]:
    """按 CHUNK_SIZE 切片"""
    return [records[i:i + CHUNK_SIZE] for i in range(0, len(records), CHUNK_SIZE)]


def _elapsed_ms(started: float) -> int:
    """从 perf_counter 起点到现在的毫秒数"""
    return int((time.perf_counter() - started) * 1000)
