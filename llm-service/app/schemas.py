"""接口契约。

字段名与 Java 侧逐字一致（驼峰命名照抄），改名会导致对接失败。
"""

from typing import Literal

from pydantic import BaseModel


class RecordIn(BaseModel):
    """一条盘点差异记录（由 Java 侧传入）"""

    recordId: int
    assetName: str | None = None
    assetCode: str | None = None
    bookQuantity: int | None = None
    actualQuantity: int | None = None
    diffQuantity: int | None = None
    result: str | None = None          # MATCH / SURPLUS / SHORTAGE


class TaskIn(BaseModel):
    """盘点任务背景信息"""

    taskName: str | None = None
    deptNames: str | None = None
    startDate: str | None = None
    endDate: str | None = None


class AnalyzeRequest(BaseModel):
    """POST /analyze/check-diff 的请求体"""

    task: TaskIn
    records: list[RecordIn]


class AnalysisItem(BaseModel):
    """单条记录的分析结果"""

    recordId: int
    analysis: str
    suggestion: str


class AnalyzeResponse(BaseModel):
    """分析结果。engine 标明这批结果由谁产出：llm / rule / mixed"""

    items: list[AnalysisItem]
    engine: Literal["llm", "rule", "mixed"]
    model: str | None = None
    tookMs: int
