# llm-service — 盘点差异 LLM 分析服务

把「盘点差异 LLM 分析」从 Java 侧拆出来的独立 HTTP 服务：prompt、分片、重试、结构化解析都在这里，
Java 侧只需要发一个 `POST /analyze/check-diff`。

- 契约：`POST /analyze/check-diff`，请求 `{task, records}`，响应 `{items, engine, model, tookMs}`
- 分片：每片最多 20 条记录，片间串行
- 重试：每片最多 2 次重试，指数退避 1s、2s
- 超时：单次请求 60s
- `engine` 三态：`llm`（全部由模型产出）/ `rule`（全部走规则模板）/ `mixed`（部分模型部分规则）

## 1. 安装依赖

本机 Python 固定在 D 盘（3.13.3），**不要装到 C 盘，不要建 venv**：

```bat
cd /d E:\FAMS\llm-service
D:\Python\python.exe -m pip install -r requirements.txt
```

依赖只有四个：`fastapi`、`uvicorn`、`httpx`、`pydantic`。

## 2. 配置（环境变量）

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `DEEPSEEK_API_KEY` | 空 | 空值时不调模型，整批走规则，不报错 |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | 接口根地址 |
| `DEEPSEEK_MODEL` | `deepseek-chat` | 模型名 |
| `PORT` | `8000` | 仅 `python main.py` 启动方式读取 |

Key 只从环境变量读，**不要写进代码或提交**。

## 3. 启动服务

```bat
cd /d E:\FAMS\llm-service
D:\Python\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8000
```

也可以直接跑入口文件（端口取环境变量 `PORT`）：

```bat
D:\Python\python.exe main.py
```

`main.py` 是唯一的入口：装配应用（`create_app()`）、挂路由、启动 uvicorn 都在这里，
`app/` 包里没有第二个 `main.py`。

两种方式都只监听 `127.0.0.1`（地址写死在 `main.py` 的 `HOST`）。
**服务没有鉴权，绝对不要绑 `0.0.0.0` 或对外暴露端口。**

本地演练「无 key 路径」（PowerShell，只影响当前窗口）：

```powershell
$env:DEEPSEEK_API_KEY=""
D:\Python\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8000
```

## 4. 自测

### 4.1 健康检查

```bat
curl http://127.0.0.1:8000/health
```

期望：`{"status":"ok"}`

### 4.2 契约

```bat
curl http://127.0.0.1:8000/openapi.json
```

### 4.3 差异分析（完整示例请求体）

示例：3 条记录（1 条盘亏、1 条盘盈、1 条相符）。

cmd 版：

```bat
curl -X POST http://127.0.0.1:8000/analyze/check-diff -H "Content-Type: application/json" -d "{\"task\":{\"taskName\":\"2026年信息中心固定资产盘点\",\"deptNames\":\"信息中心\",\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-20\"},\"records\":[{\"recordId\":101,\"assetName\":\"联想ThinkPad X1 笔记本\",\"assetCode\":\"XX-2023-001\",\"bookQuantity\":1,\"actualQuantity\":0,\"diffQuantity\":-1,\"result\":\"SHORTAGE\"},{\"recordId\":102,\"assetName\":\"惠普激光打印机\",\"assetCode\":\"XX-2022-014\",\"bookQuantity\":2,\"actualQuantity\":3,\"diffQuantity\":1,\"result\":\"SURPLUS\"},{\"recordId\":103,\"assetName\":\"戴尔显示器\",\"assetCode\":\"XX-2021-007\",\"bookQuantity\":5,\"actualQuantity\":5,\"diffQuantity\":0,\"result\":\"MATCH\"}]}"
```

PowerShell 版（中文不会乱码，推荐）：

```powershell
$body = @'
{
  "task": {
    "taskName": "2026年信息中心固定资产盘点",
    "deptNames": "信息中心",
    "startDate": "2026-11-01",
    "endDate": "2026-11-20"
  },
  "records": [
    {"recordId": 101, "assetName": "联想ThinkPad X1 笔记本", "assetCode": "XX-2023-001", "bookQuantity": 1, "actualQuantity": 0, "diffQuantity": -1, "result": "SHORTAGE"},
    {"recordId": 102, "assetName": "惠普激光打印机", "assetCode": "XX-2022-014", "bookQuantity": 2, "actualQuantity": 3, "diffQuantity": 1, "result": "SURPLUS"},
    {"recordId": 103, "assetName": "戴尔显示器", "assetCode": "XX-2021-007", "bookQuantity": 5, "actualQuantity": 5, "diffQuantity": 0, "result": "MATCH"}
  ]
}
'@
Invoke-RestMethod -Uri http://127.0.0.1:8000/analyze/check-diff -Method Post `
  -ContentType "application/json; charset=utf-8" `
  -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

无 key 时期望响应（`engine` 为 `rule`，`items` 与入参 `recordId` 一一对应）：

```json
{
  "items": [
    {"recordId": 101, "analysis": "系统检测到资产【联想ThinkPad X1 笔记本】盘亏 1 件，建议核实是否存在资产丢失或误登记。", "suggestion": "请核实资产去向，如确认丢失需按相关规定处理。"},
    {"recordId": 102, "analysis": "系统检测到资产【惠普激光打印机】盘盈 1 件，建议核实是否存在未入账资产。", "suggestion": "请确认是否有新增资产未及时录入系统，如确认则更新台账增加该资产数量。"},
    {"recordId": 103, "analysis": "资产【戴尔显示器】账面与实物相符。", "suggestion": "无需处理。"}
  ],
  "engine": "rule",
  "model": null,
  "tookMs": 0
}
```

`tookMs` 为实际耗时，不会正好是 0。

空记录：

```bat
curl -X POST http://127.0.0.1:8000/analyze/check-diff -H "Content-Type: application/json" -d "{\"task\":{},\"records\":[]}"
```

期望：`{"items":[],"engine":"rule","model":null,"tookMs":0}`

## 5. 行为说明

- 没有 `DEEPSEEK_API_KEY`：不建连接、不抛异常，整批用规则模板出结果（便于本地验收）。
- 每片独立降级：某片请求失败或返回解析不出 JSON，共尝试 3 次（首次 + 2 次重试）后只有该片退回规则，
  其余片仍用模型结果，此时 `engine="mixed"`。
- 解析优先级：直接 `json.loads` → 剥 ` ```json ` 包裹 → 顶层找数组。顶层若是对象，先试
  `items` / `analyses` / `analysis`，再退化取第一个 `list` 值（模型会自造包装键）。
- 条目字段容错：`analysis` 可取 `analysis` / `cause` / `reason`，`suggestion` 可取
  `suggestion` / `advice` / `recommendation`（模型不一定按契约命名）。
- 解析失败或「返回了但一条都用不上」时，日志会带原始返回的前 500 字符，便于线上定位。
- 模型漏掉的 `recordId` 用规则结果补；模型多返回的 `recordId` 直接丢弃。
- 输出顺序与入参 `records` 顺序一致，`items[i].recordId == records[i].recordId`。

## 6. 目录

```
llm-service/
├─ app/                       # 应用包
│  ├─ __init__.py             # 版本号与分层说明
│  ├─ config.py               # 环境变量配置
│  ├─ schemas.py              # 请求/响应契约（字段名与 Java 逐字一致）
│  ├─ api/
│  │  ├─ __init__.py          # 导出 router
│  │  └─ routes.py            # GET /health、POST /analyze/check-diff
│  ├─ services/
│  │  ├─ __init__.py
│  │  ├─ analyzer.py          # 编排：分片 20、每片重试 2 次、降级、补齐、engine
│  │  ├─ deepseek.py          # DeepSeek 请求与 JSON 解析（换模型只改这里）
│  │  └─ rules.py             # 规则兜底（文案同 Java fallbackAnalyze）
│  └─ prompts/
│     ├─ __init__.py          # 统一导出 SYSTEM_PROMPT / build_user_message
│     └─ check_diff.py        # 提示词与 user message 构造（逐字搬自 Java）
├─ main.py                    # 唯一入口：create_app() + 启动 uvicorn
├─ requirements.txt
└─ README.md
```

## 7. 分层约定

依赖只允许往下走，不要反过来：

```
main.py  →  api/  →  services/  →  prompts/ / schemas.py / config.py
```

- **新增端点**：只改 `app/api/routes.py`，业务逻辑放进 `services/`。
- **换模型 / 换厂商**：只改 `app/services/deepseek.py`，分片与重试策略不受影响。
- **新增分析场景**：在 `app/prompts/` 加一个模块，并在 `__init__.py` 里导出。
- **契约变更**：只改 `app/schemas.py`，字段名必须与 Java 侧同步。

包内一律用 `app.xxx` 绝对导入，所以工作目录必须是 `llm-service/`。
目录名带连字符，无法从仓库根用 `uvicorn llm-service.main:app` 启动。
