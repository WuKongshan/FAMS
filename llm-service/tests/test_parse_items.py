"""`parse_items` 的解析容错测试。

这组用例直接对应一次真实故障：DeepSeek 在 `response_format=json_object` 下
并不总按约定的 `{"items": [...]}` 返回，而是自己发明包装键
（实测出现过 `{"analysis":[...]}` 与 `{"analyses":[...]}`），
导致整片解析失败、静默降级为规则模板。这些用例把当时抓到的三种形状固化下来。
"""

import pytest

from app.services.deepseek import parse_items


def test_bare_array():
    """模型直接返回数组"""
    assert parse_items('[{"recordId": 1, "analysis": "a", "suggestion": "s"}]') == [
        {"recordId": 1, "analysis": "a", "suggestion": "s"}
    ]


def test_items_wrapper():
    """约定形状：{"items": [...]}"""
    raw = '{"items": [{"recordId": 1, "analysis": "a", "suggestion": "s"}]}'
    assert parse_items(raw) == [{"recordId": 1, "analysis": "a", "suggestion": "s"}]


@pytest.mark.parametrize("key", ["items", "analyses", "analysis"])
def test_known_wrapper_keys(key):
    """三个已知包装键都要能认出来；顶层 analysis 是列表、条目内 analysis 是字符串"""
    raw = '{"%s": [{"recordId": 2, "analysis": "a", "suggestion": "s"}]}' % key
    parsed = parse_items(raw)
    assert parsed is not None and len(parsed) == 1
    assert parsed[0]["recordId"] == 2


def test_unknown_wrapper_falls_back_to_first_list():
    """没见过的包装键：退化为取第一个 list 类型的值"""
    raw = '{"results": [{"recordId": 3, "analysis": "a", "suggestion": "s"}], "meta": {"n": 1}}'
    parsed = parse_items(raw)
    assert parsed is not None and parsed[0]["recordId"] == 3


def test_single_object_without_wrapper():
    """模型只回了一条裸对象（带 recordId），包成列表"""
    raw = '{"recordId": 4, "analysis": "a", "suggestion": "s"}'
    assert parse_items(raw) == [{"recordId": 4, "analysis": "a", "suggestion": "s"}]


def test_fenced_json_block():
    """被 ```json 包裹时要能剥掉外壳"""
    raw = '```json\n[{"recordId": 5, "analysis": "a", "suggestion": "s"}]\n```'
    assert parse_items(raw) == [{"recordId": 5, "analysis": "a", "suggestion": "s"}]


def test_prose_returns_none():
    """模型说了人话、完全不是 JSON：返回 None（由调用方降级），不抛异常"""
    assert parse_items("抱歉，我无法分析这批数据。") is None


def test_dict_without_any_list_returns_none():
    """是 JSON 对象但没有任何列表、也没有 recordId：返回 None"""
    assert parse_items('{"error": "quota exceeded", "code": 429}') is None
