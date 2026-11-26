"""FAMS 盘点差异 LLM 分析服务。

包内分层（自上而下，依赖只允许往下走）：

- ``api/``       HTTP 层：路由与端点，只做参数接收和响应返回
- ``services/``  业务层：编排（analyzer）、外部调用（deepseek）、兜底（rules）
- ``prompts/``   提示词模板：纯字符串与拼装逻辑，无 IO
- ``schemas.py`` 契约层：请求/响应模型，字段名与 Java 侧逐字一致
- ``config.py``  配置层：全部读环境变量

启动入口是 ``llm-service/main.py``（应用装配与 uvicorn 启动都在那里，本包内不重复放 ``main.py``）。
工作目录必须是 ``llm-service/``，命令为 ``uvicorn main:app``。
"""

__version__ = "0.1.0"
