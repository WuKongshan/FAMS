"""`analyze` 编排逻辑测试：降级、engine 三态、分片、重试、字段容错。

全部用假 `request_chat` 替换真实网络调用，不触碰 DeepSeek。
"""

import asyncio
import json

import pytest

from app.config import Settings
from app.schemas import RecordIn, TaskIn
from app.services import analyzer

NO_KEY = Settings(api_key="", base_url="http://stub", model="deepseek-chat", port=8000)
WITH_KEY = Settings(api_key="sk-test", base_url="http://stub", model="deepseek-chat", port=8000)
TASK = TaskIn(taskName="测试盘点", deptNames="计算机学院")

# 规则模板里的固定措辞，用来判断"这条是模型给的还是模板兜的"
RULE_MARK = "系统检测到资产"


def rec(i: int, diff: int = -1) -> RecordIn:
    """构造一条差异记录"""
    return RecordIn(
        recordId=i,
        assetName=f"资产{i}",
        bookQuantity=1,
        actualQuantity=1 + diff,
        diffQuantity=diff,
        result="SURPLUS" if diff > 0 else ("SHORTAGE" if diff < 0 else "MATCH"),
    )


def run(coro):
    return asyncio.run(coro)


@pytest.fixture(autouse=True)
def fast_retry(monkeypatch):
    """把退避压到 0，避免重试用例真睡 1s / 2s"""
    monkeypatch.setattr(analyzer, "RETRY_BACKOFF_SECONDS", (0.0, 0.0))


def items_for(ids, analysis="模型分析", suggestion="模型建议"):
    """构造模型返回体（约定形状）"""
    return json.dumps(
        {"items": [{"recordId": i, "analysis": analysis, "suggestion": suggestion} for i in ids]},
        ensure_ascii=False,
    )


def test_empty_records_short_circuits():
    """空批次：不调模型、engine=rule、tookMs=0"""
    out = run(analyzer.analyze(TASK, [], WITH_KEY))
    assert out.items == []
    assert out.engine == "rule"
    assert out.tookMs == 0
    assert out.model is None


def test_no_api_key_goes_all_rule(monkeypatch):
    """没配 key：整批走规则，且不能抛异常"""

    async def boom(*_a, **_k):
        raise AssertionError("没有 key 时不应调用模型")

    monkeypatch.setattr(analyzer, "request_chat", boom)

    out = run(analyzer.analyze(TASK, [rec(1), rec(2, diff=2)], NO_KEY))
    assert out.engine == "rule"
    assert out.model is None
    assert [i.recordId for i in out.items] == [1, 2]
    assert all(RULE_MARK in i.analysis for i in out.items)


def test_all_from_llm(monkeypatch):
    """全部由模型产出：engine=llm，model 有值"""

    async def fake(*_a, **_k):
        return items_for([1, 2])

    monkeypatch.setattr(analyzer, "request_chat", fake)

    out = run(analyzer.analyze(TASK, [rec(1), rec(2)], WITH_KEY))
    assert out.engine == "llm"
    assert out.model == "deepseek-chat"
    assert [i.analysis for i in out.items] == ["模型分析", "模型分析"]


def test_partial_failure_gives_mixed(monkeypatch):
    """第 2 片彻底失败 → 该片用规则补齐 → engine=mixed"""
    calls = {"n": 0}

    async def fake(*_a, **_k):
        calls["n"] += 1
        if calls["n"] == 1:
            return items_for(list(range(1, 21)))  # 第 1 片（20 条）成功
        raise RuntimeError("HTTP 500")

    monkeypatch.setattr(analyzer, "request_chat", fake)

    records = [rec(i) for i in range(1, 26)]  # 25 条 → 2 片
    out = run(analyzer.analyze(TASK, records, WITH_KEY))

    assert out.engine == "mixed"
    assert len(out.items) == 25
    # 前 20 条是模型文本，后 5 条回落到规则模板
    assert all(i.analysis == "模型分析" for i in out.items[:20])
    assert all(RULE_MARK in i.analysis for i in out.items[20:])


def test_chunking_splits_by_20(monkeypatch):
    """25 条记录必须切成 2 片调用（分片大小是任务书定死的）"""
    calls = {"n": 0}

    async def fake(*_a, **_k):
        calls["n"] += 1
        return items_for(list(range(1, 26)))

    monkeypatch.setattr(analyzer, "request_chat", fake)

    run(analyzer.analyze(TASK, [rec(i) for i in range(1, 26)], WITH_KEY))
    assert calls["n"] == 2


def test_retry_then_success(monkeypatch):
    """失败 2 次后第 3 次成功：共调用 3 次，最终 engine=llm"""
    calls = {"n": 0}

    async def fake(*_a, **_k):
        calls["n"] += 1
        if calls["n"] < 3:
            raise RuntimeError("超时")
        return items_for([1])

    monkeypatch.setattr(analyzer, "request_chat", fake)

    out = run(analyzer.analyze(TASK, [rec(1)], WITH_KEY))
    assert calls["n"] == 3
    assert out.engine == "llm"


def test_all_retries_exhausted_falls_back_to_rule(monkeypatch):
    """重试 2 次仍失败：engine=rule，不抛异常"""
    calls = {"n": 0}

    async def fake(*_a, **_k):
        calls["n"] += 1
        raise RuntimeError("一直失败")

    monkeypatch.setattr(analyzer, "request_chat", fake)

    out = run(analyzer.analyze(TASK, [rec(1)], WITH_KEY))
    assert calls["n"] == 3  # 首次 + 2 次重试
    assert out.engine == "rule"
    assert RULE_MARK in out.items[0].analysis


def test_field_synonyms_are_accepted(monkeypatch):
    """模型把字段写成 cause / advice 也要能用（实测出现过）"""

    async def fake(*_a, **_k):
        return json.dumps({"items": [{"recordId": 1, "cause": "因由", "advice": "建议"}]},
                          ensure_ascii=False)

    monkeypatch.setattr(analyzer, "request_chat", fake)

    out = run(analyzer.analyze(TASK, [rec(1)], WITH_KEY))
    assert out.engine == "llm"
    assert out.items[0].analysis == "因由"
    assert out.items[0].suggestion == "建议"


def test_unknown_record_id_is_dropped(monkeypatch):
    """模型返回了本片之外的 recordId：丢弃，该记录回落规则"""

    async def fake(*_a, **_k):
        return items_for([999])

    monkeypatch.setattr(analyzer, "request_chat", fake)

    out = run(analyzer.analyze(TASK, [rec(1)], WITH_KEY))
    assert out.engine == "rule"
    assert RULE_MARK in out.items[0].analysis


def test_missing_suggestion_falls_back_to_rule(monkeypatch):
    """模型给了 analysis 但缺 suggestion：视为没返回，整体回落规则"""

    async def fake(*_a, **_k):
        return json.dumps({"items": [{"recordId": 1, "analysis": "只有分析"}]}, ensure_ascii=False)

    monkeypatch.setattr(analyzer, "request_chat", fake)

    out = run(analyzer.analyze(TASK, [rec(1)], WITH_KEY))
    assert out.engine == "rule"
    assert RULE_MARK in out.items[0].analysis


def test_items_follow_input_order(monkeypatch):
    """输出顺序必须与入参一致（前端按下标对应）"""
    async def fake(*_a, **_k):
        # 故意倒序返回
        return items_for([3, 2, 1])

    monkeypatch.setattr(analyzer, "request_chat", fake)

    out = run(analyzer.analyze(TASK, [rec(1), rec(2), rec(3)], WITH_KEY))
    assert [i.recordId for i in out.items] == [1, 2, 3]
