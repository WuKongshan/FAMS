"""HTTP 契约测试：走真实 FastAPI 应用，但不联网。

注意：本机环境变量里通常已配了真实的 DEEPSEEK_API_KEY，
所以凡是会触发分析的用例都必须显式把 key 清空，否则会打到真实 API。
"""

import pytest
from fastapi.testclient import TestClient

from main import app

client = TestClient(app)


def _payload(records):
    return {"task": {"taskName": "接口测试", "deptNames": "计算机学院"}, "records": records}


def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "ok"}


def test_openapi_is_served():
    """契约文档可访问，证明路由与模型装配正常"""
    r = client.get("/openapi.json")
    assert r.status_code == 200
    assert "/analyze/check-diff" in r.json()["paths"]


def test_analyze_without_key_returns_rule(monkeypatch):
    """无 key：200 + engine=rule，不报错（这是本地可验收的关键路径）"""
    monkeypatch.setenv("DEEPSEEK_API_KEY", "")
    r = client.post("/analyze/check-diff", json=_payload([
        {"recordId": 7, "assetName": "笔记本", "bookQuantity": 2,
         "actualQuantity": 1, "diffQuantity": -1, "result": "SHORTAGE"},
        {"recordId": 8, "assetName": "打印机", "bookQuantity": 1,
         "actualQuantity": 3, "diffQuantity": 2, "result": "SURPLUS"},
    ]))
    assert r.status_code == 200
    body = r.json()
    assert body["engine"] == "rule"
    assert [i["recordId"] for i in body["items"]] == [7, 8]
    assert all(i["analysis"] and i["suggestion"] for i in body["items"])


def test_analyze_with_empty_records(monkeypatch):
    """空记录列表：items 为空，engine=rule"""
    monkeypatch.setenv("DEEPSEEK_API_KEY", "")
    r = client.post("/analyze/check-diff", json=_payload([]))
    assert r.status_code == 200
    body = r.json()
    assert body["items"] == []
    assert body["engine"] == "rule"


def test_analyze_requires_task_field():
    """缺 task 字段应被 Pydantic 拦成 422，而不是 500 —— 契约收紧的体现"""
    r = client.post("/analyze/check-diff", json={"records": []})
    assert r.status_code == 422
