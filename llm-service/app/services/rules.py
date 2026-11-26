"""规则兜底分析。

文案逐字搬自 Java 版 `CheckServiceImpl.fallbackAnalyze`，不要改写措辞，
否则新旧输出无法对比。
"""

from __future__ import annotations

from app.schemas import AnalysisItem, RecordIn


def analyze_by_rule(records: list[RecordIn]) -> list[AnalysisItem]:
    """按差异数量给出模板化分析，顺序与入参一致"""
    return [analyze_one(record) for record in records]


def analyze_one(record: RecordIn) -> AnalysisItem:
    """单条记录的模板化分析"""
    name = record.assetName or ""
    diff = _diff_quantity(record)

    if diff > 0:
        analysis = f"系统检测到资产【{name}】盘盈 {diff} 件，建议核实是否存在未入账资产。"
        suggestion = "请确认是否有新增资产未及时录入系统，如确认则更新台账增加该资产数量。"
    elif diff < 0:
        analysis = f"系统检测到资产【{name}】盘亏 {abs(diff)} 件，建议核实是否存在资产丢失或误登记。"
        suggestion = "请核实资产去向，如确认丢失需按相关规定处理。"
    else:
        analysis = f"资产【{name}】账面与实物相符。"
        suggestion = "无需处理。"

    return AnalysisItem(recordId=record.recordId, analysis=analysis, suggestion=suggestion)


def _diff_quantity(record: RecordIn) -> int:
    """取差异数量；缺失时用账实之差兜底，避免 None 参与比较"""
    if record.diffQuantity is not None:
        return record.diffQuantity
    if record.bookQuantity is not None and record.actualQuantity is not None:
        return record.actualQuantity - record.bookQuantity
    return 0
