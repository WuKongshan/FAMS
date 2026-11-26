"""提示词模板包。按分析场景分模块，对外只暴露模板与拼装函数。"""

from app.prompts.check_diff import SYSTEM_PROMPT, build_user_message

__all__ = ["SYSTEM_PROMPT", "build_user_message"]
