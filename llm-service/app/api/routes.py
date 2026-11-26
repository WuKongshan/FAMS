"""HTTP 路由。

端点只做三件事：收参数、调 services、返回契约对象；不要在这里写业务逻辑。
"""

from __future__ import annotations

from fastapi import APIRouter

from app.schemas import AnalyzeRequest, AnalyzeResponse
from app.services.analyzer import analyze

router = APIRouter()


@router.get("/health", summary="存活探针")
async def health() -> dict[str, str]:
    """给部署脚本 / 自测用，不触达模型"""
    return {"status": "ok"}


@router.post("/analyze/check-diff", response_model=AnalyzeResponse, summary="盘点差异分析")
async def analyze_check_diff(request: AnalyzeRequest) -> AnalyzeResponse:
    """盘点差异分析：LLM 优先，失败或无 key 时退回规则模板"""
    return await analyze(request.task, request.records)
