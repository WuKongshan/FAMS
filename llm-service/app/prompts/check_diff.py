"""盘点差异分析 Prompt。

逐字搬自 Java 版 `CheckAnalysisPrompt`，不要重写或「优化」措辞：
保留原文才能对比新旧输出的差异。
"""

from __future__ import annotations

from app.schemas import RecordIn, TaskIn

SYSTEM_PROMPT = """你是一名资深的高校固定资产审计专家，精通资产管理政策与实务。
你的任务是对盘点差异记录进行专业分析，判断差异成因并给出处理建议。

分析维度参考：
- 盘盈（实盘多于账面）：是否新购未入库？是否受赠未登记？是否调入未记账？是否是跨部门借用未归还？
- 盘亏（实盘少于账面）：是否正常报废未销账？是否丢失或被盗？是否借出未归还？是否登记错误（重复登记/错登）？

要求：
1. 结合资产名称和差异数量，推断最可能的成因
2. 分析文字控制在100字以内，简洁专业
3. 建议文字控制在80字以内，明确可操作
4. 严格按 JSON 对象格式返回，形如 {"items":[{"recordId":1,"analysis":"...","suggestion":"..."}]}，只返回 JSON，不要包含其他文字
5. 每个 recordId 对应一条分析结果"""


def build_user_message(task: TaskIn, records: list[RecordIn]) -> str:
    """构造用户消息：markdown 表格形式，行内只放本片记录"""
    lines = [
        "请分析以下盘点任务的差异记录：",
        "",
        "【盘点背景】",
        f"任务名称：{_cell(task.taskName)}",
        f"盘点部门：{_cell(task.deptNames)}",
        f"盘点期间：{_cell(task.startDate)} 至 {_cell(task.endDate)}",
        "",
        "【差异记录列表】",
        "| recordId | 资产名称 | 账面数量 | 实盘数量 | 差异 | 结果 |",
        "|----------|----------|----------|----------|------|------|",
    ]

    for record in records:
        lines.append(
            f"| {record.recordId} | {_cell(record.assetName)} "
            f"| {_cell(record.bookQuantity)} "
            f"| {_cell(record.actualQuantity)} "
            f"| {_format_diff(record.diffQuantity)} "
            f"| {_result_label(record.result)} |"
        )

    lines.extend(["", f"请对以上 {len(records)} 条差异记录逐一分析，返回上述 JSON 对象。"])
    return "\n".join(lines)


def _format_diff(diff: int | None) -> str:
    """差异列：正数带 + 号，负数保留 - 号"""
    if diff is None:
        return "0"
    return f"+{diff}" if diff > 0 else str(diff)


def _result_label(result: str | None) -> str:
    """结果列用中文，未识别的取值原样输出"""
    if result == "SURPLUS":
        return "盘盈"
    if result == "SHORTAGE":
        return "盘亏"
    if result == "MATCH":
        return "相符"
    return _cell(result)


def _cell(value: object) -> str:
    """表格/背景字段取值，缺失时留空，不把 None 写进 prompt"""
    if value is None:
        return ""
    return str(value)
