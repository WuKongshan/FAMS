# llm-service 运行镜像
#
# 只装运行依赖（requirements.txt），不含 pytest（见 requirements-dev.txt，那是 CI 用的）。
# 容器内必须监听 0.0.0.0 才能被宿主访问；对外暴露范围由 docker run 的
# `-p 127.0.0.1:8000:8000` 限制在回环，不要把端口映射到 0.0.0.0。

FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# 先拷依赖清单再装，利用镜像层缓存：只要 requirements.txt 没变，改代码不会重装依赖
COPY requirements.txt ./
RUN pip install -r requirements.txt

COPY app ./app
COPY main.py ./

# 非 root 运行
RUN useradd --create-home --uid 10001 --shell /usr/sbin/nologin appuser \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8000

# slim 镜像没有 curl，用 Python 自己探活，避免为此装额外包
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import sys,urllib.request; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3).status == 200 else 1)"

CMD ["python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
