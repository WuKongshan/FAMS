"""服务入口：装配 FastAPI 并启动。

服务没有鉴权，只允许监听 127.0.0.1，不要绑 0.0.0.0。
启动（工作目录必须是 llm-service/）：
    D:\\Python\\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8000
"""

from __future__ import annotations

import logging

import uvicorn
from fastapi import FastAPI

from app import __version__
from app.api import router
from app.config import load_settings

# 服务无鉴权，监听地址写死回环，不接受外部传入
HOST = "127.0.0.1"


def create_app() -> FastAPI:
    """装配应用：日志、元信息、路由"""
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )

    application = FastAPI(
        title="FAMS LLM 差异分析服务",
        description="接收盘点任务与差异记录，返回每条的 analysis + suggestion",
        version=__version__,
    )
    application.include_router(router)
    return application


app = create_app()


def run() -> None:
    """供 `python main.py` 直接启动"""
    uvicorn.run(app, host=HOST, port=load_settings().port)


if __name__ == "__main__":
    run()
