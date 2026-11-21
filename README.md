# FAMS · 高校固定资产协同管理系统

面向高校的固定资产全生命周期管理系统，覆盖**资产台账 → 领用/归还 → 维修 → 盘点 → 报废**的完整链路，
其中领用、归还、报废三类审批由 **Flowable 7** 驱动，支持院系级审批隔离。

后端 Spring Boot 3 + MyBatis-Plus + MySQL，前端 Vue 3 + Element Plus。

---

## 功能概览

| 模块 | 说明 |
| --- | --- |
| 系统管理 | 用户、角色、部门维护；JWT 登录认证；RBAC 三级角色 |
| 操作审计 | 记录关键操作，落库前对 `password` / `token` 等字段自动脱敏 |
| 资产台账 | 资产分类（两级树）、台账增删改查、条件筛选、导出 Excel |
| 领用 / 归还 | 师生提交申请 → 院系初审 → 校级复审；通过后自动变更资产状态与使用人 |
| 报废管理 | 报废申请 → 两级审批；终审通过后资产置为「已报废」并清空使用人 |
| 维修管理 | 报修工单登记与维修状态跟踪 |
| 协同盘点 | 创建盘点任务、多部门协同填报、差异比对 |
| 智能分析 | 盘点差异接入 DeepSeek，生成自然语言分析结论（可选，未配置 Key 时该功能不可用） |
| 统计看板 | 资产状态分布、分类占比等图表 |

### 审批流说明

三级角色：`1 = 超级管理员`（复审）、`2 = 学院管理员`（初审）、`3 = 普通师生`（无审批权）。

- **院系隔离**：学院管理员只能看到并审批本学院（`dept_id`）的申请单，规则在服务层强制过滤，不依赖前端传参。
- **双路分流**：单据表上有 `process_instance_id`。
  - 非空 → 由 Flowable 流程实例驱动，审批按 BPMN 节点流转；
  - 为空 → 走原有的硬编码状态机分支。

  这样存量数据无需回填，新老逻辑可长期共存，也是「先上线再迁移」的常见做法。
- **状态投影**：`apply_order.status` / `scrap_order.status` 仍保留给前端消费，
  但每次审批后由引擎当前节点反推写回，流程实例才是真相来源。
- **共用流程定义**：领用/归还与报废共用同一张 BPMN（`assetApplyApproval`），
  因为两者的两级流转规则一致；编排组件入参抽象为 `WorkflowBizContext`，不绑定具体实体。

---

## 技术栈

**后端**

| 组件 | 版本 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.2.0 |
| MyBatis-Plus | 3.5.5 |
| Flowable | 7.0.0 |
| Flyway | （随 Spring Boot 管理） |
| MySQL | 8.x |
| JJWT | 0.12.3 |
| Knife4j（OpenAPI 3） | 4.4.0 |
| Hutool / Apache POI | 5.8.24 / 5.2.5 |

**前端**：Vue 3.4 · TypeScript 5.3 · Vite 5 · Element Plus 2.4 · Pinia 2.1 · Vue Router 4.2 · ECharts 5.4

---

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.x（本地或可访问的实例）

### 1. 创建数据库

Flyway 会自动建表，但**不会自动创建数据库**，需先手动建库：

```sql
CREATE DATABASE asset_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

首次启动时 Flyway 会依次执行 `V1 ~ V8` 迁移脚本，Flowable 也会自动创建 `ACT_*` 引擎表。

### 2. 配置环境变量

源码中**不包含任何凭据**，数据库密码与 JWT 密钥均从环境变量注入。变量清单见 [`back-end/.env.example`](back-end/.env.example)。

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `DB_PASSWORD` | ✅ | MySQL 密码 |
| `JWT_SECRET` | ✅ | JWT 签名密钥，**至少 32 字节**；未配置时应用启动即失败 |
| `DB_USERNAME` | — | 默认 `root` |
| `DEEPSEEK_API_KEY` | — | 仅「智能分析」功能需要 |

```bash
# PowerShell
$env:DB_PASSWORD = "your_password"
$env:JWT_SECRET  = "a-random-string-at-least-32-bytes-long"

# Bash
export DB_PASSWORD="your_password"
export JWT_SECRET="a-random-string-at-least-32-bytes-long"
```

生成密钥：`openssl rand -base64 48`

> 在 IDEA 中运行，可在 *Run/Debug Configurations → Environment variables* 里配置。

### 3. 启动后端

```bash
cd back-end
mvn spring-boot:run
```

默认端口 `9090`。接口文档（Knife4j）：http://localhost:9090/doc.html

### 4. 启动前端

```bash
cd front-end
npm install
npm run dev
```

访问 http://localhost:5173 。开发服务器已配置代理，`/api` 转发到 `http://localhost:9090`。

### 演示账号

初始化脚本会写入以下账号，密码均为 `123456`：

| 账号 | 角色 | 所属学院 |
| --- | --- | --- |
| `admin` | 超级管理员 | 资产管理处（校级） |
| `zhangwei` | 学院管理员 | 计算机科学与技术学院 |
| `liuyang` / `wanghao` | 普通师生 | 计算机科学与技术学院 |
| `zhaoming` | 学院管理员 | 电子信息工程学院 |

> ⚠️ 仅用于本地演示，部署到任何真实环境前请立即修改。

---

## 目录结构

```
FAMS/
├── back-end/
│   ├── src/main/java/com/example/asset/
│   │   ├── common/                  # 统一响应、异常、JWT、审计切面、配置
│   │   ├── security/                # Spring Security 过滤器与 UserDetails
│   │   └── modules/
│   │       ├── admin/               # 用户 / 角色 / 部门 / 审计日志
│   │       ├── asset/               # 资产台账与分类
│   │       ├── approval/            # 领用 / 归还审批
│   │       │   └── flow/            # Flowable 编排组件（WorkflowBizContext 等）
│   │       ├── scrap/               # 报废审批
│   │       ├── repair/              # 维修工单
│   │       ├── check/               # 协同盘点 + LLM 差异分析
│   │       └── statistics/          # 统计接口
│   └── src/main/resources/
│       ├── application.yml          # 配置（凭据全部走环境变量）
│       ├── db/migration/            # Flyway 迁移脚本 V1 ~ V8
│       └── processes/               # BPMN 流程定义
└── front-end/
    └── src/
        ├── api/                     # 接口封装
        ├── views/                   # 页面（按业务模块分目录）
        ├── stores/                  # Pinia
        └── router/
```

---

## 数据库迁移约定

- 脚本位于 `back-end/src/main/resources/db/migration/`，由 Flyway 在启动时自动执行。
- **命名必须为 `V<版本号>__<描述>.sql`（双下划线）**。
  单下划线（如 `V9_add_x.sql`）会被 Flyway **静默跳过**，只在启动日志里留一行 INFO 提示，极易被忽略。
  如需让这类错误直接导致启动失败，可开启 `spring.flyway.validate-migration-naming: true`。
- 业务表归 Flyway 管，Flowable 的 `ACT_*` 引擎表归引擎自己管，不要互相插手。

| 版本 | 内容 |
| --- | --- |
| V1 / V2 | 初始表结构与种子数据 |
| V3 | 资产数量字段 |
| V4 / V5 / V6 | 审批、维修的部门字段与历史数据回填 |
| V7 | `apply_order.process_instance_id` |
| V8 | `scrap_order.process_instance_id` |

---

## 设计文档

- [把「申请单审批」接入 Flowable —— 改造教程](docs/Flowable审批流程接入教程.md)
- [把「报废审批」接入 Flowable —— 改造教程](docs/Flowable报废流程接入教程.md)

## 说明

- 本项目为学习与教学演示用途，未附开源许可证。
- 前端开发服务器配置了 `host: '0.0.0.0'` 与 `allowedHosts: true`，方便局域网调试；部署到公网前请改为具体域名。
