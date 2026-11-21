# 把「申请单审批」接入 Flowable —— 完整改造教程

> 项目：高校固定资产协同管理系统（FAMS）
> 模块：领用/归还申请审批（`apply_order`）
> 技术栈：Spring Boot 3.2.0 · Flowable 7.0.0 · MyBatis-Plus 3.5.5 · Flyway · MySQL 8 · Vue 3 + Element Plus
> 本次范围：**只把「领用（BORROW）」接入 Flowable**，「归还（RETURN）」按第七章的手动指引自行开启

---

## 目录

1. [这次改了什么（先看结论）](#一这次改了什么先看结论)
2. [改造前：硬编码状态机及其痛点](#二改造前硬编码状态机及其痛点)
3. [Flowable 核心概念速览（对照本项目）](#三flowable-核心概念速览对照本项目)
4. [整体设计](#四整体设计)
5. [动手步骤（逐文件讲解）](#五动手步骤逐文件讲解)
6. [一次完整审批的时序与数据变化](#六一次完整审批的时序与数据变化)
7. [【手动更改指南】把「归还」也接入 Flowable](#七手动更改指南把归还也接入-flowable)
8. [扩展：新增审批节点 / 新增申请类型](#八扩展新增审批节点--新增申请类型)
9. [自测清单](#九自测清单)
10. [踩坑与排查手册](#十踩坑与排查手册)
11. [附录 A：ACT_ 引擎表速查](#附录-aact_-引擎表速查)
12. [附录 B：常用 Flowable API 速查](#附录-b常用-flowable-api-速查)

---

## 一、这次改了什么（先看结论）

### 1.1 一句话总结

> **把「审批怎么流转」从 Java 里的 `if-else` 搬进了 BPMN 流程图文件，Java 代码只负责「业务校验 + 调用引擎」。**

改造后：

- 流程走向、审批层级、驳回路径，全部由 `asset-apply-approval.bpmn20.xml` 这张流程图描述；
- 每一次审批在引擎里都是一条可追溯的**流程实例**（谁在什么时候办的、走到了哪一步、下一步该谁）；
- 想加一级审批 / 加会签 / 加条件分支，改流程图即可，不用再堆 `if-else`。

### 1.2 文件清单

| 类型 | 文件 | 说明 |
| --- | --- | --- |
| 🆕 新增 | `back-end/src/main/resources/processes/asset-apply-approval.bpmn20.xml` | BPMN 2.0 流程定义，`process id = assetApplyApproval` |
| 🆕 新增 | `back-end/src/main/resources/db/migration/V7__add_process_instance_id.sql` | 给 `apply_order` 加 `process_instance_id` 列 + 索引 |
| 🆕 新增 | `.../modules/approval/flow/ApplyWorkflowProperties.java` | 工作流配置类（流程 Key、启用的申请类型） |
| 🆕 新增 | `.../modules/approval/flow/ApplyWorkflowService.java` | **Flowable 编排组件**，封装启动/查任务/办理任务 |
| ✏️ 修改 | `.../modules/approval/entity/ApplyOrder.java` | 增加 `processInstanceId` 字段 |
| ✏️ 修改 | `.../modules/approval/service/impl/ApprovalServiceImpl.java` | 改为**双路分流**：有流程实例走 Flowable，否则走原状态机 |
| ✏️ 修改 | `back-end/src/main/resources/application.yml` | 细化 Flowable 配置 + 新增 `approval.workflow` 业务配置 |
| 🗑️ 删除 | `back-end/src/main/resources/processes/.bpmn20.xml` | Flowable 设计器误存的空壳文件（`process id=""`），会导致启动校验失败。已备份至 `.workbuddy/backup/empty-process.bpmn20.xml.bak` |

### 1.3 没有改动的部分（重要）

| 未改动 | 原因 |
| --- | --- |
| `ApprovalController` | 接口路径、入参、返回值全部保持原样 |
| `ApprovalService` 接口 | 方法签名未变 |
| **前端所有代码** | `apply_order.status` 的取值仍然是 `PENDING_FIRST / PENDING_SECOND / COMPLETED / FIRST_REJECT / SECOND_REJECT`，前端零改动 |
| `approval_record` 表 | 审批记录仍写入该表，前端「审批记录」表格照常显示 |

---

## 二、改造前：硬编码状态机及其痛点

### 2.1 原来的实现

原来的 `ApprovalServiceImpl.approve()` 把整个流程逻辑写死在代码里：

```java
// 改造前（简化版）
String currentStatus = order.getStatus();

if (roleId == 2 && !"FIRST".equals(level))  throw new BusinessException("学院管理员仅可初审");
if (roleId == 1 && !"SECOND".equals(level)) throw new BusinessException("超级管理员仅可复审");

if ("FIRST".equals(level) && !"PENDING_FIRST".equals(currentStatus))  throw ...;
if ("SECOND".equals(level) && !"PENDING_SECOND".equals(currentStatus)) throw ...;

String newStatus;
if ("FIRST".equals(level)) {
    newStatus = "PASS".equals(result) ? "PENDING_SECOND" : "FIRST_REJECT";
} else {
    newStatus = "PASS".equals(result) ? "COMPLETED" : "SECOND_REJECT";
}

// 用「状态必须等于旧值」做乐观锁
updateWrapper.eq(ApplyOrder::getStatus, currentStatus).set(ApplyOrder::getStatus, newStatus);
```

状态全集写在实体注释里：

```
PENDING_FIRST → PENDING_SECOND → COMPLETED
      ↓                ↓
 FIRST_REJECT    SECOND_REJECT
```

### 2.2 四个痛点

| # | 痛点 | 具体表现 |
| --- | --- | --- |
| 1 | **流程固化的代码里** | 想加一级「资产管理处复核」、想让金额 > 5 万才需复审，只能改 Java 代码 → 编译 → 发版 |
| 2 | **状态字段是唯一事实来源** | `status` 既是「流程走到哪」又是「并发锁」。审批记录表 `approval_record` 与 `status` 之间没有任何机制保证一致 |
| 3 | **回答不了「卡在谁那里」** | 没有流程实例概念，无法查询「当前节点是谁的待办」「平均每个节点耗时多久」 |
| 4 | **重复代码** | `ScrapServiceImpl`（报废）里有一份**几乎逐行相同**的逻辑。两处一起维护，改一处忘一处 |

### 2.3 换 Flowable 后

| 痛点 | Flowable 的解法 |
| --- | --- |
| 流程固化 | 流程是**外部文件**（BPMN XML），改流程 = 改文件 + 重启（或热部署），不碰 Java |
| 事实来源混乱 | 「走到哪」由**流程实例**决定；`status` 退化为**给前端看的投影** |
| 卡在谁那里 | `ACT_RU_TASK` + `ACT_RU_IDENTITYLINK` 天然支持「我的待办」查询 |
| 重复代码 | 多个模块可共用一个流程定义（本项目的召回/归还流程完全一致，可复用同一个 BPMN） |

---

## 三、Flowable 核心概念速览（对照本项目）

### 3.1 概念对照表

| 概念 | 一句话解释 | 落库位置 | 本项目对应 |
| --- | --- | --- | --- |
| **流程定义** ProcessDefinition | 一张「流程图模板」，由 `.bpmn20.xml` 部署而来 | `ACT_RE_PROCDEF` | `assetApplyApproval` |
| **部署** Deployment | 把 BPMN 文件发布进引擎的动作（启动时自动完成） | `ACT_RE_DEPLOYMENT` | 应用启动自动部署 |
| **流程实例** ProcessInstance | 流程图的一次「执行」。每提交一张申请单就产生一个 | `ACT_RU_EXECUTION` | 一张领用申请单 = 一个实例 |
| **任务** Task | 需要人来办理的节点（UserTask）。流程停在这里等人 | `ACT_RU_TASK` | `firstApproval`、`secondApproval` |
| **流程变量** Variable | 随流程实例走的键值对，网关靠它做判断 | `ACT_RU_VARIABLE` | `firstApproved`、`secondApproved`、`deptId`… |
| **身份关联** IdentityLink | 任务与「候选人 / 候选组 / 办理人」的关联 | `ACT_RU_IDENTITYLINK` | 初审候选人 = 本学院管理员 |
| **排他网关** ExclusiveGateway | 「岔路口」：按条件只选一条路走 | 无独立表 | `gatewayFirst`、`gatewaySecond` |
| **历史** History | 流程结束后运行时表会被清空，轨迹留在 `ACT_HI_*` | `ACT_HI_*` | 审批轨迹、审批意见 |

### 3.2 表名前缀的含义

| 前缀 | 含义 | 特点 |
| --- | --- | --- |
| `ACT_RE_` | **Re**pository 资源库 | 流程定义、部署记录、图片 |
| `ACT_RU_` | **Ru**ntime 运行时 | **流程结束即删除**，只保留在跑的 |
| `ACT_HI_` | **Hi**story 历史 | **永久保留**，流程结束后的唯一痕迹 |
| `ACT_ID_` | **Id**entity 身份 | 用户/组（本项目未用，业务用户仍在 `sys_user`） |
| `ACT_GE_` | **Ge**neral 通用 | 属性表、二进制表（BPMN 原文存在 `ACT_GE_BYTEARRAY`） |

> 🔑 **记住这条生命周期，排查问题时会反复用到**：
> `部署(RE) → 启动实例(RU) → 产生任务(RU) → 办理 → 流程结束(RU 清空, HI 留痕)`

### 3.3 本流程用到的两个关键机制

**① UserTask（用户任务）= 等人办的事**

```xml
<userTask id="firstApproval" name="院系初审" flowable:candidateGroups="ROLE_DEPT_ADMIN"/>
```

流程执行到这里会**停下来**，在 `ACT_RU_TASK` 里插入一行，直到有人调用 `taskService.complete()`。

**② ExclusiveGateway（排他网关）= 岔路口**

```xml
<exclusiveGateway id="gatewayFirst" default="flowFirstReject"/>
```

它按顺序评估所有出线的 `conditionExpression`，**只走第一条成立的**；都不成立就走 `default` 指定的那条。这就是「通过 / 驳回」分流的地方。

---

## 四、整体设计

### 4.1 核心思路：双路分流

判断依据只有一个 —— **`apply_order.process_instance_id` 是否为空**。

```
                    approve(orderId, ...)
                             │
                 ┌───────────┴────────────┐
                 │  process_instance_id   │
                 │      是否为空？         │
                 └───────────┬────────────┘
                   空 │              │ 非空
                      ▼              ▼
        ┌──────────────────┐  ┌──────────────────────┐
        │ 老状态机分支       │  │ Flowable 分支          │
        │ approveByStatus- │  │ approveByWorkflow    │
        │ Machine()        │  │  · 引擎决定"该谁办"     │
        │                  │  │  · 引擎决定"下一步去哪" │
        │ 历史存量数据 &    │  │  · 领用申请走这里       │
        │ 未启用的申请类型   │  │                      │
        └──────────────────┘  └──────────────────────┘
```

**为什么这样设计？**

- **存量数据免回填**：改造前已有的申请单 `process_instance_id` 是 `NULL`，继续走老逻辑，不会「半路卡死」；
- **新老共存**：报废模块（`ScrapServiceImpl`）暂时不动，两套机制互不干扰；
- **开启「归还」零代码改动**：见第七章。

### 4.2 一个关键取舍：`status` 字段要保留吗？

**保留。** 理由：

1. 前端 `ApplyList.vue` / `ApprovalList.vue` 有大量 `row.status === 'PENDING_FIRST'` 之类的判断，还有按状态分页查询的接口；
2. 列表页要按状态筛选、统计，如果每次都要去查引擎表，性能与复杂度都更差。

所以采用「**流程实例为真相来源，`status` 为投影**」：

```
每完成一次审批 → 办完引擎任务 → 再读一次「当前活动任务」 → 反推 status 写回 apply_order
```

```java
Task nextTask = applyWorkflowService.currentTask(order.getProcessInstanceId());
String newStatus = (nextTask != null)
        ? applyWorkflowService.statusOf(nextTask)    // → PENDING_SECOND
        : (approved ? "COMPLETED" : "FIRST_REJECT"); // 流程结束
```

这样 `status` 永远和引擎同步，但前端完全无感。

### 4.3 权限校验放在 Java 还是 BPMN？

**放在 Java。** 这是本次设计里最重要的一个决定。

| 校验项 | 放在哪 | 为什么 |
| --- | --- | --- |
| 普通师生（roleId=3）无权审批 | Java | 简单的角色判断 |
| 不能审批自己提交的申请 | Java | 需要比对 `applyUserId` |
| 学院管理员只能审**本学院**的单子 | Java | 需要比对 `deptId`，用 BPMN 表达式写极其别扭且难测试 |
| 学院管理员只能初审、超管只能复审 | Java | 同上 |
| 「通过 / 驳回」走哪条路 | **BPMN 网关** | 这才是流程图该干的事 |

一句话：**「谁能办」需要业务数据（院系、是否本人），交给 Java；「办完去哪」是流程语义，交给 BPMN。**

> BPMN 里的 `flowable:candidateGroups="ROLE_DEPT_ADMIN"` 是**声明式元数据**，配合 `ApplyWorkflowService.assignCandidates()` 写入真实的候选人（`ACT_RU_IDENTITYLINK`），用途是「谁能看到这条待办」，属于**辅助查询**，不是安全边界。安全边界始终在 `ApprovalServiceImpl`。

---

## 五、动手步骤（逐文件讲解）

### 5.1 第 1 步：确认依赖

`pom.xml` 里**原本就有** Flowable，不用改：

```xml
<properties>
    <java.version>17</java.version>
    <flowable.version>7.0.0</flowable.version>
</properties>

<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
    <version>${flowable.version}</version>
</dependency>
```

> ⚠️ 注意：`flowable-spring-boot-starter` 是**全家桶**（process + cmmn + dmn + app + idm 五个引擎），
> 首次启动会一口气创建 100 多张 `ACT_*` 表。
> **如果你只需要流程引擎**，可以换成更精简的：
> ```xml
> <dependency>
>     <groupId>org.flowable</groupId>
>     <artifactId>flowable-spring-boot-starter-process</artifactId>
>     <version>${flowable.version}</version>
> </dependency>
> ```
> 换完记得手动清掉数据库里多余的 `ACT_CMMN_* / ACT_DMN_* / ACT_APP_* / ACT_ID_*` 表。

**这个 starter 会自动提供以下 Bean，直接 `@Autowired` / 构造器注入即可：**

| Bean | 用途 |
| --- | --- |
| `ProcessEngine` | 引擎总入口 |
| `RepositoryService` | 管理流程定义（部署、查询） |
| `RuntimeService` | **启动流程实例、读写流程变量** |
| `TaskService` | **查询待办、办理任务、加评论、指派候选人** |
| `HistoryService` | 查询历史流程实例 / 历史任务 |

### 5.2 第 2 步：配置 `application.yml`

```yaml
# Flowable 工作流引擎
flowable:
  # 自动创建/升级 ACT_* 引擎表（生产环境建议改 false，改为人工执行 DDL）
  database-schema-update: true
  # 关闭异步执行器：本流程全是人工审批，没有定时器/异步任务，关掉省线程和轮询
  async-executor-activate: false
  # 流程定义自动部署位置（默认值就是它，这里显式写出来便于理解）
  process-definition-location-prefix: classpath*:/processes/
  # 启动时检查流程定义文件是否合法（写错 BPMN 会直接启动失败，而不是运行到一半才炸）
  check-process-definitions: true

# 审批工作流业务配置
approval:
  workflow:
    # BPMN 流程定义 Key，必须与 asset-apply-approval.bpmn20.xml 的 process id 一致
    process-key: assetApplyApproval
    # 已接入 Flowable 的申请类型：BORROW=领用，RETURN=归还
    enabled-apply-types: BORROW
```

**逐项解释：**

| 配置项 | 作用 | 不配会怎样 |
| --- | --- | --- |
| `database-schema-update` | 自动建/升级引擎表 | 报 `Table 'ACT_RE_PROCDEF' doesn't exist` |
| `async-executor-activate` | 是否开启异步执行器 | 默认 `true`，会起若干线程池做定时任务轮询，纯人工审批场景浪费资源 |
| `process-definition-location-prefix` | 去哪个目录找 `.bpmn20.xml` | 默认 `classpath*:/processes/`，只要文件放对位置就能自动部署 |
| `check-process-definitions` | 启动即校验 BPMN | 设为 `false` 时 BPMN 写错要等到真正发起流程才报错，很难排查 |
| `approval.workflow.enabled-apply-types` | **本项目自定义**：哪些申请类型走工作流 | 这是第七章「开启归还」唯一要改的地方 |

> 💡 `process-definition-location-prefix` 的默认值就是 `classpath*:/processes/`，
> 所以「把 BPMN 文件放到 `src/main/resources/processes/` 下」是约定俗成的做法。

### 5.3 第 3 步：编写 BPMN 流程定义

文件：`back-end/src/main/resources/processes/asset-apply-approval.bpmn20.xml`

流程图长这样：

```
  ○                ┌────────────┐            ◇                  ┌────────────┐            ◇              ⊙
 提交申请 ────────>│  院系初审   │──────────>│ 初审结果 │──通过────>│  校级复审   │──────────>│ 复审结果 │──通过──> 审批通过
                   └────────────┘            └─────────┘           └────────────┘            └─────────┘
                                                  │                                                │
                                                驳回                                              驳回
                                                  ▼                                                ▼
                                             ⊙ 初审驳回                                        ⊙ 复审驳回
```

**逐段讲解：**

#### ① 根元素 `definitions`

```xml
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI"
             targetNamespace="http://www.flowable.org/processdef">
```

- 默认命名空间 = BPMN 2.0 官方规范，所以 `<process>`、`<userTask>` 这类标签不用加前缀；
- `xmlns:flowable` = Flowable 对 BPMN 的**扩展属性**（如 `flowable:candidateGroups`、`flowable:assignee`、`flowable:initiator`）；
- `bpmndi / omgdc / omgdi` = 图形信息（见第 ⑥ 段）。

#### ② `<process>` —— 流程定义本体

```xml
<process id="assetApplyApproval" name="资产领用归还审批流程" isExecutable="true">
```

- `id` = **流程定义 Key**，Java 里用 `runtimeService.startProcessInstanceByKey("assetApplyApproval", ...)` 引用它。
  **这个值必须和 `application.yml` 里的 `approval.workflow.process-key` 完全一致**，写错会报 `FlowableObjectNotFoundException: no processes deployed with key 'xxx'`。
- `isExecutable="true"` —— **漏了这个流程无法启动**，这是最常见的低级错误。

#### ③ 开始事件

```xml
<startEvent id="startEvent" name="提交申请"/>
<sequenceFlow id="flowStartToFirst" sourceRef="startEvent" targetRef="firstApproval"/>
```

`sequenceFlow` 就是「箭头」，用 `sourceRef` / `targetRef` 连接两个节点。
**所有 `sourceRef` / `targetRef` 指向的 id 都必须真实存在**，否则部署时校验失败。

#### ④ 用户任务

```xml
<userTask id="firstApproval" name="院系初审" flowable:candidateGroups="ROLE_DEPT_ADMIN">
    <documentation>由申请人所属学院的资产管理员办理，仅可办理本学院申请单。</documentation>
</userTask>
```

- `id` = **任务定义 Key**。Java 里靠它判断「当前是初审还是复审」：

  ```java
  public static final String TASK_FIRST  = "firstApproval";
  public static final String TASK_SECOND = "secondApproval";
  ```
- `name` = 界面显示的中文名；
- `flowable:candidateGroups` = 候选组（这里写的是 `sys_role.role_code` 的语义值，作为声明式说明）。

#### ⑤ 排他网关（流程的灵魂）

```xml
<exclusiveGateway id="gatewayFirst" name="初审结果" default="flowFirstReject"/>

<sequenceFlow id="flowFirstPass" name="通过" sourceRef="gatewayFirst" targetRef="secondApproval">
    <conditionExpression xsi:type="tFormalExpression"><![CDATA[${firstApproved == true}]]></conditionExpression>
</sequenceFlow>

<sequenceFlow id="flowFirstReject" name="驳回" sourceRef="gatewayFirst" targetRef="endFirstRejected"/>
```

- 网关按顺序评估出线的条件表达式，**只走第一条为真的**；
- `${firstApproved == true}` 里的 `firstApproved` 就是 Java 侧办理任务时塞进去的流程变量：
  ```java
  variables.put("firstApproved", approved);   // Boolean
  taskService.complete(taskId, variables);
  ```
- `default="flowFirstReject"` 把「驳回」设为**兜底路径**：条件都不成立时走它。
  这样即使 `firstApproved` 变量因故缺失，流程也不会抛「No outgoing sequence flow found」而卡死。
- `<![CDATA[...]]>` 用来包裹表达式，避免 `<`、`&` 等字符需要转义。

#### ⑥ 结束事件与图形布局

```xml
<endEvent id="endApproved" name="审批通过"/>
<endEvent id="endFirstRejected" name="初审驳回"/>
<endEvent id="endSecondRejected" name="复审驳回"/>
```

**为什么用 3 个结束事件而不是 1 个？**
因为「通过结束」和「驳回结束」在业务上是两件事，分开后可以通过 `ACT_HI_ACTINST` 直接看出流程是从哪个口子出去的，追溯更清晰。

**`<bpmndi:BPMNDiagram>` 这一段是纯图形信息**（每个节点的坐标、每条线的拐点），
作用只是让 Flowable Designer / bpmn.js 打开时能看到漂亮的图，**对流程执行毫无影响**。
如果不想手写，可以用 [Flowable Designer 插件]（IDEA）或 [bpmn.io](https://demo.bpmn.io/) 拖拽生成。

#### 🚨 BPMN 手写第一坑：XML 注释里不能有 `--`

```xml
<!-- ❌ 错误：注释里出现连续两个短横线，XML 解析直接报 ParseError -->
<!-- ==================== 分隔线 ==================== -->

<!-- ✅ 正确：改成等号或星号 -->
<!-- ==================== 分隔线 ==================== -->
```

这个坑是本次改造中**真实踩到**的：最初用减号画分隔线，启动时报

```
XMLStreamException: ParseError at [row,col]:[15,9]
```

因为 `XML 1.0` 规范明确禁止注释内容中出现 `--`。**画分隔线请一律用 `=` 号。**

### 5.4 第 4 步：数据库改动

#### ① Flyway 迁移脚本

文件：`back-end/src/main/resources/db/migration/V7__add_process_instance_id.sql`

```sql
ALTER TABLE apply_order
    ADD COLUMN process_instance_id VARCHAR(64) NULL COMMENT 'Flowable 流程实例ID（NULL=未接入工作流）' AFTER status;

CREATE INDEX idx_apply_order_process_instance ON apply_order (process_instance_id);
```

要点：

- **必须可空**。存量数据、以及未接入工作流的申请类型保持 `NULL` → 自动走老逻辑，**无需数据回填**；
- 长度 64 足够（Flowable 默认 ID 是 UUID 字符串）；
- 加索引是为了「按流程实例反查业务单据」这种排查场景。

> 📌 **Flyway 与 Flowable 的分工**：
> - **业务表**（`apply_order` 等）由 **Flyway** 管理，版本号手工递增；
> - **引擎表**（`ACT_*`）由 **Flowable 自己**管理，靠 `database-schema-update: true` 在启动时创建。
> 两套东西不要混在一起，**千万不要手写 `ACT_*` 的 Flyway 脚本**。

#### ② 引擎自动建表

启动应用后，Flowable 会检查并创建所有 `ACT_*` 表，日志里能看到：

```
o.f.e.i.b.d.ParsedDeploymentBuilder      : Processing resource ...asset-apply-approval.bpmn20.xml
o.f.e.i.d.b.DeploymentManager            : Creating new deployment: ...
```

**怎么确认建成功了？**

```sql
-- 流程定义是否部署成功
SELECT ID_, KEY_, NAME_, VERSION_, DEPLOYMENT_ID_ FROM ACT_RE_PROCDEF;

-- 期望看到一行：assetApplyApproval
```

### 5.5 第 5 步：Flowable 编排组件

文件：`.../modules/approval/flow/ApplyWorkflowService.java`

**设计原则：这个类只跟引擎打交道，不写任何业务校验。**

| 方法 | 职责 |
| --- | --- |
| `supports(applyType)` | 该申请类型是否已接入工作流（读配置） |
| `start(order)` | 启动流程实例，返回 `processInstanceId` |
| `currentTask(pid)` | 查当前活动任务（没有则说明流程已结束） |
| `complete(task, order, approved, ...)` | 办理任务 + 写流程变量 + 加审批意见 |
| `levelOf(task)` / `statusOf(task)` | 任务 Key → 审批层级 / 业务状态 |
| `assignCandidates(task, order)` | 给待办任务指派候选人 |

**关键代码 1：启动流程实例**

```java
ProcessInstance instance = runtimeService.startProcessInstanceByKey(
        properties.getProcessKey(),          // "assetApplyApproval"
        String.valueOf(order.getId()),       // businessKey：反查业务单据的钥匙
        variables);                          // 流程变量
return instance.getId();
```

`businessKey` 是我们的**申请单ID**。这样 `ACT_RU_EXECUTION.BUSINESS_KEY_` 里存的就是 `apply_order.id`，
排查时可以直接 join 业务表。

**关键代码 2：办理任务**

```java
Map<String, Object> variables = new HashMap<>();
if (TASK_FIRST.equals(taskKey)) {
    variables.put(VAR_FIRST_APPROVED, approved);     // 驱动 gatewayFirst
} else if (TASK_SECOND.equals(taskKey)) {
    variables.put(VAR_SECOND_APPROVED, approved);    // 驱动 gatewaySecond
}
// 审批意见写入引擎历史表 ACT_HI_COMMENT
if (StringUtils.hasText(comment)) {
    taskService.addComment(task.getId(), task.getProcessInstanceId(), comment);
}
// 记录真实办理人 → ACT_HI_TASKINST.ASSIGNEE_
taskService.setAssignee(task.getId(), String.valueOf(approverId));

taskService.complete(task.getId(), variables);
```

**关键代码 3：候选人指派**

```java
// 初审 → 本学院的资产管理员（roleId=2 且 deptId 匹配）
wrapper.eq(User::getRoleId, ROLE_DEPT_ADMIN).eq(User::getDeptId, order.getDeptId());
// 复审 → 校级超级管理员（roleId=1）
wrapper.eq(User::getRoleId, ROLE_ADMIN);

for (User candidate : candidates) {
    taskService.addCandidateUser(task.getId(), String.valueOf(candidate.getId()));
}
```

候选人写入 `ACT_RU_IDENTITYLINK`。**流程每推进一个节点，都要给新产生的任务重新指派候选人**，
所以 `start()` 和 `complete()` 末尾都调了一次 `assignCandidates(currentTask(pid), order)`。

> 有了候选人，将来做「我的待办」列表只需要一行查询：
> ```java
> taskService.createTaskQuery().taskCandidateUser(String.valueOf(userId)).list();
> ```
> 这也是为什么候选人机制值得保留 —— 它是「待办中心」的地基。

**异常处理：把引擎异常翻译成业务异常**

```java
try {
    instance = runtimeService.startProcessInstanceByKey(...);
} catch (FlowableException e) {
    // 典型场景：流程定义没部署 / processKey 配错 / BPMN 语法错
    throw new BusinessException("流程发起失败：" + e.getMessage());
}
```

### 5.6 第 6 步：改造 `ApprovalServiceImpl`

#### ① `submitApply`：提交时启动流程实例

```java
order.setStatus("PENDING_FIRST");
applyOrderMapper.insert(order);          // 先落库，拿到自增 ID

if (applyWorkflowService.supports(order.getApplyType())) {   // 当前只对 BORROW 为 true
    String processInstanceId = applyWorkflowService.start(order);
    order.setProcessInstanceId(processInstanceId);
    applyOrderMapper.updateById(order);  // 回填 process_instance_id
}
```

**为什么要「先 insert 再 start」？**
因为 BPMN 的 `businessKey` 要用申请单ID，必须等自增主键生成。整个方法带 `@Transactional`，
insert 与启动流程在**同一个事务**里，要么都成功，要么都回滚。

#### ② `approve`：分流

```java
ApplyOrder order = applyOrderMapper.selectById(orderId);
if (order == null) throw new BusinessException("申请单不存在");

if (StringUtils.hasText(order.getProcessInstanceId())) {
    approveByWorkflow(...);        // 新：Flowable 分支
} else {
    approveByStatusMachine(...);   // 旧：原状态机逻辑，逐行保留
}
```

#### ③ `approveByWorkflow`：11 步走完一次审批

```java
// 1. 普通师生无权审批                    ← 沿用原规则
// 2. 不能审批自己提交的申请                ← 沿用原规则
// 3. 从引擎取"当前待办任务"
Task task = applyWorkflowService.currentTask(order.getProcessInstanceId());
if (task == null) throw new BusinessException("当前流程已结束，无可办理的审批任务");
String currentLevel = applyWorkflowService.levelOf(task);   // FIRST / SECOND

// 4. 前端传的 level 必须和引擎当前节点一致（防页面过期错审）
if (level != null && !level.equals(currentLevel)) {
    throw new BusinessException("审批层级与流程当前节点不一致，请刷新页面后重试");
}

// 5. 角色 + 院系隔离校验                   ← 沿用原规则
// 6. 业务 status 必须和流程节点一致（并发第一道闸）
String expectedStatus = applyWorkflowService.statusOf(task);
if (!expectedStatus.equals(order.getStatus())) throw new BusinessException("申请单状态与流程节点不一致...");

// 7. 写审批记录（前端"审批记录"表格依赖它）
// 8. 办理引擎任务 ← 流程在这里真正往前走
applyWorkflowService.complete(task, order, approved, approveUserId, approveUserName, comment);

// 9. 读引擎的新状态，反推业务 status
Task nextTask = applyWorkflowService.currentTask(order.getProcessInstanceId());
String newStatus = (nextTask != null)
        ? applyWorkflowService.statusOf(nextTask)
        : (approved ? "COMPLETED" : ("FIRST".equals(currentLevel) ? "FIRST_REJECT" : "SECOND_REJECT"));

// 10. 乐观锁写回（并发第二道闸）
int rows = applyOrderMapper.update(null, updateWrapper.eq(ApplyOrder::getStatus, order.getStatus())
                                                 .set(ApplyOrder::getStatus, newStatus));
if (rows == 0) throw new BusinessException("审批失败：订单状态已被他人修改，请刷新页面后重试");

// 11. 复审通过且流程结束 → 同步资产状态
if (approved && nextTask == null && "SECOND".equals(currentLevel)) {
    updateAssetOnFinalApproval(order);
}
```

#### ④ ⭐ 一个容易被忽略但极其重要的收益：事务一致性

```java
@Transactional
public void approve(...) {
    // ... 写 approval_record（MyBatis）
    // ... 办 Flowable 任务（引擎）
    // ... 写 apply_order.status（MyBatis）
}
```

MyBatis 和 Flowable **共用同一个 `DataSource` 和同一个事务管理器**（都由 Spring Boot 自动配置），
所以这三步在**同一个数据库事务**里。第 10 步如果抛异常，**流程引擎里刚办理完的任务也会一起回滚**，
不会出现「审批记录写了、流程也推进了、但 status 没改」的脏数据。

> 反过来说：**绝对不要**把 Flowable 配成另一个数据源，那会直接失去这个能力。

#### ⑤ 后端接口层完全没动

`ApprovalController` 一行未改：

```java
@PostMapping("/approve")
public R<?> approve(@RequestBody Map<String, Object> params) {
    // 从 SecurityContext 取登录用户，防止伪造审批人
    // 调用 approvalService.approve(...)
}
```

### 5.7 第 7 步：前端为什么不用改

| 前端关注点 | 改造后 | 结论 |
| --- | --- | --- |
| `row.status` 取值 | 仍是 `PENDING_FIRST / PENDING_SECOND / COMPLETED / FIRST_REJECT / SECOND_REJECT` | ✅ 无变化 |
| `POST /api/approval/approve` 入参 | 仍是 `{orderId, level, result, comment}` | ✅ 无变化 |
| `GET /api/approval/page` | 仍按 `status` / `applyType` 分页 | ✅ 无变化 |
| `GET /api/approval/{id}` | 仍返回 `{order, records}` | ✅ 无变化 |
| 详情页的 `el-steps` 审批流程条 | 由 `status` 计算，`status` 语义未变 | ✅ 无变化 |

**唯一的行为差异**：如果页面上停留过久、期间单据已被别人审批，现在会返回更明确的提示
「审批层级与流程当前节点不一致，请刷新页面后重试」，而不是原来的「当前订单状态不允许初审」。

---

## 六、一次完整审批的时序与数据变化

### 6.1 时序图

```
刘洋(普通师生)      后端Service         Flowable引擎        张伟(学院管理员)    系统管理员
     │                  │                    │                   │               │
     │ 提交领用申请       │                    │                   │               │
     ├─────────────────>│                    │                   │               │
     │                  │ insert apply_order │                   │               │
     │                  │  (status=PENDING_FIRST)                │               │
     │                  ├───────────────────>│ 启动流程实例        │               │
     │                  │                    │ 创建 firstApproval 任务            │
     │                  │<───────────────────┤                   │               │
     │                  │ 回填 process_instance_id               │               │
     │                  │ 指派候选人(本学院管理员)                 │               │
     │<─── 提交成功 ─────┤                    │                   │               │
     │                  │                    │                   │               │
     │                  │                    │     查询待办 ◄─────┤               │
     │                  │                    │                   │               │
     │                  │  初审通过(result=PASS, level=FIRST)     │               │
     │                  ├───────────────────>│ 写 firstApproved=true             │
     │                  │                    │ complete 任务       │               │
     │                  │                    │ 网关→通过→创建 secondApproval      │
     │                  │<───────────────────┤                   │               │
     │                  │ status=PENDING_SECOND                  │               │
     │                  │ 指派候选人(超管)                        │               │
     │                  │                    │                   │   查询待办 ◄──┤
     │                  │  复审通过(result=PASS, level=SECOND)    │               │
     │                  ├───────────────────>│ 写 secondApproved=true            │
     │                  │                    │ complete → 流程结束 │               │
     │                  │ status=COMPLETED   │                   │               │
     │                  │ 资产→在用,登记使用人 │                   │               │
     │<── 完成 ─────────┤                    │                   │               │
```

### 6.2 每一步的数据落库对照

| 步骤 | `apply_order` | `approval_record` | Flowable `ACT_RU_*` | Flowable `ACT_HI_*` |
| --- | --- | --- | --- | --- |
| 提交申请 | `status=PENDING_FIRST`<br>`process_instance_id=<pid>` | — | `EXECUTION`(1) | `PROCINST`(1)<br>`ACTINST`(startEvent, firstApproval) |
| 初审通过 | `status=PENDING_SECOND` | 1 条 `FIRST/PASS` | `TASK` 换成 secondApproval<br>`VARIABLE` 加 `firstApproved=true` | `TASKINST` 完成 1 条<br>`COMMENT` 1 条 |
| 复审通过 | `status=COMPLETED` | 2 条 | **运行时表清空** | `PROCINST` 置为结束<br>`TASKINST` 完成 2 条 |
| 初审驳回 | `status=FIRST_REJECT` | 1 条 `FIRST/REJECT` | **运行时表清空** | 结束于 `endFirstRejected` |
| 复审驳回 | `status=SECOND_REJECT` | 2 条 | **运行时表清空** | 结束于 `endSecondRejected` |

> 💡 **重点**：流程一结束，`ACT_RU_TASK`、`ACT_RU_EXECUTION`（有历史级别的配置下）会被清空，
> 想看轨迹必须查 `ACT_HI_*`。这正是「运行时表 vs 历史表」存在的意义。

---

## 七、【手动更改指南】把「归还」也接入 Flowable

> 这一章就是给你自己动手用的。**领用/归还的审批流转路径完全一样**（都是「学院初审 → 校级复审」），
> 差别只在**审批通过后资产状态的更新方向**（领用→在用，归还→闲置），
> 而这段逻辑 `updateAssetOnFinalApproval()` 已经是按 `applyType` 分支写的，**不用改**。

### 7.1 结论：只需要改一行配置

打开 `back-end/src/main/resources/application.yml`，把 `enabled-apply-types` 加上 `RETURN`：

```yaml
approval:
  workflow:
    process-key: assetApplyApproval
    # 改前
    enabled-apply-types: BORROW
    # 改后（注意是逗号分隔，YAML 也支持写成列表）
    enabled-apply-types: BORROW,RETURN
```

等价写法（YAML 列表）：

```yaml
approval:
  workflow:
    enabled-apply-types:
      - BORROW
      - RETURN
```

**重启应用 → 完成。** 不需要改任何 Java 代码、不需要改 BPMN 文件、不需要新增数据库迁移。

### 7.2 为什么只改一行就够了？

顺着调用链看一遍就清楚了：

| 环节 | 是否与 `applyType` 相关 | 说明 |
| --- | --- | --- |
| `submitApply` 里启动流程 | ✅ **唯一的分支点** | `if (applyWorkflowService.supports(order.getApplyType()))` |
| `supports()` | ✅ 读的就是 `enabled-apply-types` 配置 | 改配置即生效 |
| BPMN 流程定义 | ❌ 与类型无关 | 流程图里没有任何 `applyType` 的条件分支 |
| 审批权限校验 | ❌ 与类型无关 | 只判断角色与院系 |
| `status` 回写 | ❌ 与类型无关 | 只依赖当前任务节点 |
| 资产状态同步 | ❌ **已按类型分支实现** | 见 `updateAssetOnFinalApproval()` |

```java
private void updateAssetOnFinalApproval(ApplyOrder order) {
    ...
    if ("BORROW".equals(order.getApplyType())) {
        assetUpdate.set(Asset::getStatus, "在用")
                   .set(Asset::getUserId, order.getApplyUserId())
                   .set(Asset::getUserName, order.getApplyUserName());
    } else if ("RETURN".equals(order.getApplyType())) {   // ← 归还的路径早就写好了
        assetUpdate.set(Asset::getStatus, "闲置")
                   .set(Asset::getUserId, null)
                   .set(Asset::getUserName, null);
    }
    ...
}
```

### 7.3 验证清单（开启归还后请逐项确认）

| # | 验证项 | 预期结果 |
| --- | --- | --- |
| 1 | 用 `liuyang / 123456`（普通师生）提交一张**归还**申请 | 提交成功 |
| 2 | `SELECT status, process_instance_id FROM apply_order ORDER BY id DESC LIMIT 1;` | `PENDING_FIRST`，`process_instance_id` **非空** |
| 3 | `SELECT ID_, NAME_, TASK_DEF_KEY_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='<pid>';` | 一行，`TASK_DEF_KEY_ = firstApproval` |
| 4 | 用 `zhangwei / 123456`（本学院管理员）在审批列表里点「通过」 | 成功，状态变「待复审」 |
| 5 | `SELECT ID_, TASK_DEF_KEY_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='<pid>';` | `TASK_DEF_KEY_ = secondApproval` |
| 6 | 用 `admin / 123456`（超管）点「通过」 | 成功，状态变「已完成」 |
| 7 | `SELECT status FROM asset_info WHERE id=<资产ID>;` | 变回 `闲置`，`user_id` / `user_name` 为 `NULL` |
| 8 | `SELECT COUNT(*) FROM ACT_RU_TASK WHERE PROC_INST_ID_='<pid>';` | `0`（运行时任务已清空） |
| 9 | `SELECT * FROM ACT_HI_TASKINST WHERE PROC_INST_ID_='<pid>';` | 2 行，`ASSIGNEE_` 分别是张伟和 admin |

### 7.4 进阶：想让「归还」走**不同的**流程怎么办？

比如业务上要求：**归还只需要学院初审，不需要校级复审**。

**做法：新增一个 BPMN，按类型路由到不同流程 Key。**

**① 新建** `resources/processes/asset-return-approval.bpmn20.xml`

```xml
<process id="assetReturnApproval" name="资产归还审批流程" isExecutable="true">
    <startEvent id="startEvent"/>
    <sequenceFlow id="f1" sourceRef="startEvent" targetRef="firstApproval"/>
    <userTask id="firstApproval" name="院系初审"/>
    <sequenceFlow id="f2" sourceRef="firstApproval" targetRef="gatewayFirst"/>
    <exclusiveGateway id="gatewayFirst" default="fReject"/>
    <sequenceFlow id="fPass" sourceRef="gatewayFirst" targetRef="endApproved">
        <conditionExpression xsi:type="tFormalExpression"><![CDATA[${firstApproved == true}]]></conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="fReject" sourceRef="gatewayFirst" targetRef="endRejected"/>
    <endEvent id="endApproved"/>
    <endEvent id="endRejected"/>
</process>
```

**② 把配置从「一个 Key」改成「类型 → Key 的映射」**

`application.yml`：

```yaml
approval:
  workflow:
    # 类型 → 流程定义 Key
    process-key-by-type:
      BORROW: assetApplyApproval
      RETURN: assetReturnApproval
```

`ApplyWorkflowProperties`：

```java
private Map<String, String> processKeyByType = new LinkedHashMap<>();

public boolean supports(String applyType) {
    return applyType != null && processKeyByType.containsKey(applyType);
}
public String processKeyOf(String applyType) {
    return processKeyByType.get(applyType);
}
```

`ApplyWorkflowService.start()` 里改用 `properties.processKeyOf(order.getApplyType())`。

**③ 注意：`firstApproval` 这个任务 Key 必须在新流程里沿用同名**

因为 `ApplyWorkflowService.TASK_FIRST = "firstApproval"` 是靠**任务 Key** 判断层级的。
如果新流程里改名了，就要同步调整 `levelOf()` / `statusOf()` 里的映射，或者改成按「流程 Key + 任务 Key」二维判断。

---

## 八、扩展：新增审批节点 / 新增申请类型

### 8.1 想在两级之间插一级「资产管理处复核」

**只改 BPMN 和少数几个常量，业务表结构不用动：**

1. 在 `asset-apply-approval.bpmn20.xml` 里插入节点：

   ```xml
   <sequenceFlow id="flowFirstPass" sourceRef="gatewayFirst" targetRef="thirdApproval"/>
   <userTask id="thirdApproval" name="资产处复核" flowable:candidateGroups="ROLE_ADMIN"/>
   <sequenceFlow id="flowThirdToGateway" sourceRef="thirdApproval" targetRef="gatewayThird"/>
   <exclusiveGateway id="gatewayThird" default="flowThirdReject"/>
   <sequenceFlow id="flowThirdPass" sourceRef="gatewayThird" targetRef="secondApproval">
       <conditionExpression xsi:type="tFormalExpression"><![CDATA[${thirdApproved == true}]]></conditionExpression>
   </sequenceFlow>
   <sequenceFlow id="flowThirdReject" sourceRef="gatewayThird" targetRef="endThirdRejected"/>
   <endEvent id="endThirdRejected" name="复核驳回"/>
   ```

2. `ApplyWorkflowService` 加常量与映射：

   ```java
   public static final String TASK_THIRD = "thirdApproval";
   public static final String VAR_THIRD_APPROVED = "thirdApproved";
   public static final String LEVEL_THIRD = "THIRD";
   // levelOf() / statusOf() / complete() 里补上 thirdApproval 的分支
   ```

3. `apply_order.status` 新增取值（如 `PENDING_THIRD`），并同步前端 `statusText()` 的映射表。

> 这就是 Flowable 的价值所在：**加一级审批不用新建表、不用写状态机，加个节点即可。**

### 8.2 想给报废模块（`scrap_order`）也接入

两个选择：

| 方案 | 做法 | 适用 |
| --- | --- | --- |
| **复用同一流程** | `scrap_order` 也加 `process_instance_id` 列，审批时复用 `ApplyWorkflowService`（把 `ApplyOrder` 换成通用入参） | 审批层级完全一致时推荐 |
| **独立流程** | 新建 `scrap-approval.bpmn20.xml`，独立 Service | 报废需要额外的评估/公示节点时 |

推荐做法是把 `ApplyWorkflowService` 里 `ApplyOrder` 这个具体类型替换成一个简单的上下文对象
（如 `WorkflowContext { bizId, bizType, deptId, ... }`），这样它就能被所有审批模块复用。

---

## 九、自测清单

### 9.1 构建与启动

```bash
cd back-end
mvn -o -B -DskipTests clean compile      # 编译
mvn -o -B -DskipTests spring-boot:run    # 启动
```

**启动日志中必须出现的关键行：**

| 关键字 | 含义 |
| --- | --- |
| `Processing resource ... asset-apply-approval.bpmn20.xml` | 引擎正在解析你的 BPMN |
| `Creating new deployment` | 流程定义部署成功 |
| `Started AssetApplication in x seconds` | 应用启动成功 |

**如果看到下面这行就是 BPMN 有问题，必须修掉（否则启动直接失败）：**

```
ERROR ... ParsedDeploymentBuilder : Could not parse resource ...asset-apply-approval.bpmn20.xml
org.flowable.bpmn.exceptions.XMLException: ... ParseError at [row,col]:[15,9]
```

排查套路：**看 `[row,col]` 的行列号，去 BPMN 文件对应位置找语法错误**（最常见就是注释里的 `--`）。

### 9.2 数据库自查 SQL

```sql
-- ① 流程定义是否部署成功
SELECT ID_, KEY_, NAME_, VERSION_ FROM ACT_RE_PROCDEF WHERE KEY_ = 'assetApplyApproval';

-- ② 正在运行的流程实例有多少
SELECT COUNT(*) FROM ACT_RU_EXECUTION WHERE BUSINESS_KEY_ IS NOT NULL;

-- ③ 待办任务 + 对应业务单据（businessKey 就是 apply_order.id）
SELECT t.ID_ AS task_id, t.NAME_ AS task_name, t.TASK_DEF_KEY_, e.BUSINESS_KEY_ AS apply_order_id
FROM ACT_RU_TASK t
JOIN ACT_RU_EXECUTION e ON e.ID_ = t.PROC_INST_ID_;

-- ④ 某条任务的候选人（ACT_RU_IDENTITYLINK）
SELECT * FROM ACT_RU_IDENTITYLINK WHERE TASK_ID_ = '<task_id>' AND TYPE_ = 'candidate';

-- ⑤ 流程变量
SELECT NAME_, TEXT_, LONG_ FROM ACT_RU_VARIABLE WHERE PROC_INST_ID_ = '<pid>';

-- ⑥ 完整审批轨迹（流程结束后看这里）
SELECT TASK_DEF_KEY_, ASSIGNEE_, START_TIME_, END_TIME_, DURATION_
FROM ACT_HI_TASKINST WHERE PROC_INST_ID_ = '<pid>' ORDER BY START_TIME_;

-- ⑦ 审批意见
SELECT TYPE_, MESSAGE_, TIME_, USER_ID_ FROM ACT_HI_COMMENT WHERE PROC_INST_ID_ = '<pid>';
```

### 9.3 接口自测

```bash
# 1) 登录拿 token（领用申请人：计算机学院 刘洋）
curl -X POST http://localhost:9090/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"liuyang","password":"123456"}'

# 2) 提交领用申请（把 <TOKEN> 换成上一步返回的 token，<ASSET_ID> 换成一件"闲置"资产）
curl -X POST http://localhost:9090/api/approval/apply \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <TOKEN>" \
     -d '{"applyType":"BORROW","assetId":2,"assetCode":"AST-2024-002","purpose":"课程演示使用"}'

# 3) 学院管理员初审通过（张伟 / 123456）
curl -X POST http://localhost:9090/api/approval/approve \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <TOKEN_ZHANGWEI>" \
     -d '{"orderId":1,"level":"FIRST","result":"PASS","comment":"同意领用"}'

# 4) 超级管理员复审通过（admin / 123456）
curl -X POST http://localhost:9090/api/approval/approve \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <TOKEN_ADMIN>" \
     -d '{"orderId":1,"level":"SECOND","result":"PASS","comment":"批准"}'
```

**期望结果**：第 4 步之后 `apply_order.status='COMPLETED'`，且对应资产变为「在用」并登记到刘洋名下。

### 9.4 实测验证结果（本次改造已跑通）

下面的结果来自一次真实的端到端跑测：启动应用（Spring Boot 3.2 + Flowable 7.0.0 + MySQL 8）→
依次调用登录/提交/审批接口 → 再用 JDBC 直查引擎表核对。**全部符合预期。**

**测试账号**（密码均为 `123456`）：

| 账号 | id | roleId | deptId | 角色 |
| --- | --- | --- | --- | --- |
| `liuyang` 刘洋 | 3 | 3 | 1 | 普通师生（计算机学院） |
| `wanghao` 王浩 | 5 | 3 | 1 | 普通师生（计算机学院） |
| `zhangwei` 张伟 | 2 | 2 | 1 | 学院管理员（计算机学院） |
| `zhaoming` 赵明 | 6 | 2 | 2 | 学院管理员（电子信息工程学院） |
| `admin` 系统管理员 | 1 | 1 | 6 | 超级管理员 |

#### 场景 A：正常通过（提交 → 初审通过 → 复审通过）

| 步骤 | 调用 | 实测结果 | 判定 |
| --- | --- | --- | --- |
| A1 | 刘洋提交领用申请（BORROW） | `code=200` | ✅ |
| A2 | 查询申请单 | `status=PENDING_FIRST`，`process_instance_id=3dba947f-…` | ✅ **流程实例已创建** |
| A3 | 刘洋（普通师生）自行审批 | `权限不足：普通师生无权进行审批操作` | ✅ 被拒 |
| A4 | admin（超管）执行初审 | `权限不足：超级管理员仅可进行复审操作` | ✅ 被拒 |
| A5 | 赵明（**外学院**管理员）审批 | `权限不足：学院管理员仅可审批本学院的申请单` | ✅ 被拒 |
| A6 | 张伟（本学院管理员）初审通过 | `code=200` | ✅ |
| A7 | 查询申请单 | `status=PENDING_SECOND` | ✅ 推进到复审 |
| A8 | admin 复审通过 | `code=200` | ✅ |
| A9 | 查询申请单 | `status=COMPLETED`，审批记录 **2 条**（FIRST/PASS 张伟、SECOND/PASS 系统管理员） | ✅ |
| A10 | 查询资产 | 资产变为「**在用**」，使用人 = **刘洋** | ✅ 资产状态已同步 |

#### 场景 B：初审驳回

| 步骤 | 调用 | 实测结果 | 判定 |
| --- | --- | --- | --- |
| B1 | 王浩提交领用申请 | `code=200` | ✅ |
| B2 | 查询申请单 | `PENDING_FIRST`，`process_instance_id=3df1d151-…` | ✅ |
| B3 | 张伟初审驳回 | `code=200` | ✅ |
| B4 | 查询申请单 | `status=FIRST_REJECT` | ✅ 流程结束于初审驳回 |
| B5 | 对已结束的单子再审批 | `当前流程已结束，无可办理的审批任务` | ✅ 新守卫生效 |
| B6 | 查询资产 | 仍为「**闲置**」，未被改动 | ✅ 驳回不影响资产 |

#### 场景 C：层级不一致守卫

| 步骤 | 调用 | 实测结果 | 判定 |
| --- | --- | --- | --- |
| C1 | 刘洋提交领用申请 | `code=200` | ✅ |
| C2 | 查询申请单 | `PENDING_FIRST`，`process_instance_id=3e0e3315-…` | ✅ |
| C3 | 张伟传 `level=SECOND`（流程实际停在初审） | `审批层级与流程当前节点不一致，请刷新页面后重试` | ✅ 被拒 |
| C4 | 查询申请单 | 仍为 `PENDING_FIRST`，未被误推进 | ✅ |

#### 引擎侧数据核对（JDBC 直查）

**① 流程定义已自动部署**（`ACT_RE_PROCDEF`）

```
ID_ = assetApplyApproval:1:87f42886-…
KEY_ = assetApplyApproval   NAME_ = 资产领用归还审批流程   VERSION_ = 1
```

**② 历史活动轨迹证明了 BPMN 真的在驱动流转**（`ACT_HI_ACTINST`）

场景 A 的实例（`3dba947f-…`）完整轨迹：

```
startEvent(提交申请) → flowStartToFirst → firstApproval(院系初审)
  → flowFirstToGateway → gatewayFirst(初审结果) → flowFirstPass(通过)
  → secondApproval(校级复审) → flowSecondToGateway → gatewaySecond(复审结果)
  → flowSecondPass(通过) → endApproved(审批通过)
```

场景 B 的实例（`3df1d151-…`）轨迹：

```
startEvent → flowStartToFirst → firstApproval → flowFirstToGateway
  → gatewayFirst(初审结果) → flowFirstReject(驳回) → endFirstRejected(初审驳回)
```

> `flowFirstPass` / `flowFirstReject` 是排他网关选中的那条连线 —— **这正是「条件表达式驱动流程分支」的直接证据**。

**③ 审批人真实落库**（`ACT_HI_TASKINST.ASSIGNEE_`）

```
firstApproval  ASSIGNEE_=2  (张伟)   -> 已结束
secondApproval ASSIGNEE_=1  (admin)  -> 已结束
```

**④ 审批意见进入引擎历史**（`ACT_HI_COMMENT`）

```
TYPE_=comment  MESSAGE_='agree borrow'     USER_ID_=2
TYPE_=comment  MESSAGE_='approved'         USER_ID_=1
TYPE_=comment  MESSAGE_='purpose unclear'  USER_ID_=2
```

**⑤ 初审候选人按院系正确指派**（`ACT_RU_IDENTITYLINK`）

初审任务（如 `18f9dc2f-…`）的候选人为 **2（张伟）与 4（陈静）** ——
正是计算机学院（deptId=1）的**两名**资产管理员，跨院系隔离在引擎侧也成立。

**⑥ 流程结束后运行时表清空**

场景 A/B 结束后 `ACT_RU_TASK`、`ACT_RU_EXECUTION` 中对应实例的行数为 **0**，
轨迹只留在 `ACT_HI_*` —— 与第三章讲的「运行时表 vs 历史表」生命周期完全一致。

#### 两点范围说明

1. **存量数据零影响**：库里改造前已有的申请单 `process_instance_id` 全为 `NULL`，验证后仍为 `NULL`，
   说明双路分流没有误伤历史数据，它们会继续走原状态机分支。
2. **测试数据已清理**：验证产生的 9 张测试申请单、对应审批记录、以及引擎里的 9 个流程实例
   已按 `purpose LIKE 'flowable e2e - %'` 精确定位并删除，被测试领用的资产也恢复为「闲置」。
   数据库已回到测试前的状态（`apply_order` 13 条、引擎运行时/历史表为空、`ACT_RE_PROCDEF` 保留 1 条流程定义）。

---

## 十、踩坑与排查手册

### 坑 1｜XML 注释里出现 `--` → 启动直接崩

> **本次真实踩到**

```
org.flowable.bpmn.exceptions.XMLException:
  javax.xml.stream.XMLStreamException: ParseError at [row,col]:[15,9]
```

**原因**：XML 规范禁止注释内出现连续两个短横线。用减号画的分隔线 `---------` 正好命中。
**解法**：分隔线一律用 `=` 号。

### 坑 2｜`<process>` 忘了 `isExecutable="true"`

流程部署成功但**启动时报找不到流程定义**。
**解法**：所有要跑的 `<process>` 都必须带 `isExecutable="true"`。

### 坑 3｜`process-key` 与 BPMN 的 `process id` 不一致

```
FlowableObjectNotFoundException: no processes deployed with key 'xxx'
```

**解法**：`application.yml` 的 `approval.workflow.process-key` 必须与 `<process id="...">` **逐字符相同**（区分大小写）。

### 坑 4｜BPMN 放错目录 → 不自动部署

**解法**：必须在 `src/main/resources/processes/` 下，且文件名以 `.bpmn20.xml` 结尾。
（`target/classes/processes/` 下的副本是构建产物，改源文件后要重新构建。）

### 坑 5｜`ACT_*` 表不存在

```
Table 'asset_management.ACT_RE_PROCDEF' doesn't exist
```

**解法**：确认 `flowable.database-schema-update: true`；确认数据源能连通；确认数据库账号有 `CREATE TABLE` 权限。

### 坑 6｜Flyway 和 Flowable 互相打架

**不要手写 `ACT_*` 表的 Flyway 迁移脚本**，也不要让 Flyway 去 `baseline` 引擎表。
两者维护各自的表：业务表归 Flyway，引擎表归 Flowable。

### 坑 7｜流程卡死：`No outgoing sequence flow found`

排他网关的所有条件都不成立、又没配 `default` 时，流程会抛异常。
**解法**：**永远给排他网关配一个 `default` 兜底出线**（本项目已把「驳回」设为 default）。

### 坑 8｜重复审批 / 并发审批

两个管理员同时点「通过」，可能造成同一任务被办理两次。
本项目做了**两道闸**：

1. 审批前校验 `apply_order.status` 必须等于引擎当前节点对应的状态；
2. 写回时用 `WHERE status = <旧值>` 的乐观锁；
3. 且由于共用事务，任一步失败会连**引擎的任务办理**一起回滚。

### 坑 9｜`async-executor-activate` 忘了关

纯人工审批场景开着异步执行器，会白白起线程池轮询 `ACT_RU_JOB`。
**解法**：`flowable.async-executor-activate: false`（本项目已配）。

### 坑 10｜历史数据「半路卡死」

改造前已在审批中的单据 `process_instance_id` 为 `NULL`，如果强行让它们走 Flowable 分支会报「无待办任务」。
**解法**：本项目的双路分流就是为它设计的 —— `process_instance_id IS NULL` 自动回落老逻辑，**无需数据回填**。

### 坑 11｜前端页面状态过期

用户开着页面，单据已被别人审批了。
**解法**：后端返回明确提示「审批层级与流程当前节点不一致，请刷新页面后重试」；
体验要求高的话，可以在前端审批前先调一次详情接口刷新状态。

### 坑 12｜申请人身份来自请求体（**改造中发现的实际问题，建议修复**）

`ApprovalController.apply()` 只从 `SecurityContext` 回填了 `deptId` / `deptName`，
**`applyUserId` / `applyUserName` 是直接取请求体里的值**：

```java
// 现状：申请人ID 由前端传入，服务端未校验
order.setDeptId(toLong(userDetails.get("deptId")));
order.setDeptName((String) userDetails.get("deptName"));
// order.setApplyUserId(...)  <- 没有这一行
```

这带来两个问题，且**都直接影响 Flowable 分支的正确性**：

| 问题 | 影响 |
| --- | --- |
| 可伪造为他人的名义提交申请 | `不能审批自己提交的申请` 这条规则被绕过 |
| 前端漏传时 `apply_user_id` 为 NULL | 审批通过后资产登记不到使用人；`不能审批自己提交的申请` 校验失效 |

**建议修复**（与 `deptId` 的处理方式保持一致，改 2 行）：

```java
// ApprovalController.apply()
order.setApplyUserId(toLong(userDetails.get("userId")));   // 以登录态为准，忽略请求体
order.setApplyUserName((String) userDetails.get("realName"));
```

> 本次改造**未擅自改动**这段逻辑（超出「接入 Flowable」的范围），
> 但因为它会直接影响申请人身份的可信度，特此记录。前端 `ApplyList.vue` 目前会正常传这两个字段，
> 所以正常使用不受影响。
>
> 排查提示：如果你的测试脚本用程序直接调 `/api/approval/apply` 而**没有传 `applyUserId`**，
> 新申请单的 `apply_user_id` 会是 `NULL`，此时按 `applyUserId` 分页查询是查不到这张单子的。

---

## 附录 A：`ACT_` 引擎表速查

### 最常用的 10 张表

| 表名 | 作用 | 生命周期 |
| --- | --- | --- |
| `ACT_RE_DEPLOYMENT` | 部署记录 | 永久 |
| `ACT_RE_PROCDEF` | **流程定义**（每个 Key 每次部署一个新 version） | 永久 |
| `ACT_RU_EXECUTION` | **流程实例 / 执行流**（`BUSINESS_KEY_` 关联业务单） | 运行时 |
| `ACT_RU_TASK` | **待办任务** | 运行时 |
| `ACT_RU_VARIABLE` | **流程变量** | 运行时 |
| `ACT_RU_IDENTITYLINK` | 候选人 / 候选组 / 办理人关联 | 运行时 |
| `ACT_HI_PROCINST` | 历史流程实例 | 永久 |
| `ACT_HI_ACTINST` | 历史活动节点（精细到每个连线） | 永久 |
| `ACT_HI_TASKINST` | **历史任务**（谁办的、耗时多久） | 永久 |
| `ACT_HI_COMMENT` | **审批意见 / 评论** | 永久 |

### 排查问题的通用套路

```
① 业务单拿到 apply_order.id
        ↓
② SELECT * FROM ACT_RU_EXECUTION WHERE BUSINESS_KEY_ = '<apply_order.id>'
        ↓  拿到 PROC_INST_ID_
③ SELECT * FROM ACT_RU_TASK WHERE PROC_INST_ID_ = '<pid>'      → 卡在哪个节点
   SELECT * FROM ACT_RU_VARIABLE WHERE PROC_INST_ID_ = '<pid>' → 网关条件算对不对
   SELECT * FROM ACT_RU_IDENTITYLINK WHERE TASK_ID_ = '<taskId>'→ 谁能办
        ↓  如果运行时表查不到（流程已结束）
④ SELECT * FROM ACT_HI_ACTINST WHERE PROC_INST_ID_ = '<pid>' ORDER BY START_TIME_
   SELECT * FROM ACT_HI_TASKINST WHERE PROC_INST_ID_ = '<pid>'
   SELECT * FROM ACT_HI_COMMENT WHERE PROC_INST_ID_ = '<pid>'
```

---

## 附录 B：常用 Flowable API 速查

```java
// ── 流程定义 ─────────────────────────────────────────────
repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey("assetApplyApproval").list();

// ── 启动流程实例 ──────────────────────────────────────────
runtimeService.startProcessInstanceByKey(key, businessKey, variables);

// ── 查询流程实例 ──────────────────────────────────────────
runtimeService.createProcessInstanceQuery()
        .processInstanceBusinessKey(orderId).singleResult();

// ── 查询待办任务 ──────────────────────────────────────────
taskService.createTaskQuery().processInstanceId(pid).singleResult();     // 按流程实例
taskService.createTaskQuery().taskCandidateUser(userId).list();          // 按候选人
taskService.createTaskQuery().taskAssignee(userId).list();               // 按办理人

// ── 办理任务 ─────────────────────────────────────────────
taskService.complete(taskId, variables);

// ── 流程变量 ─────────────────────────────────────────────
runtimeService.setVariable(pid, "key", value);
runtimeService.getVariable(pid, "key");

// ── 审批意见 / 办理人 ─────────────────────────────────────
taskService.addComment(taskId, pid, "同意");
taskService.setAssignee(taskId, userId);

// ── 历史查询 ─────────────────────────────────────────────
historyService.createHistoricProcessInstanceQuery().processInstanceBusinessKey(orderId).singleResult();
historyService.createHistoricTaskInstanceQuery().processInstanceId(pid).list();
historyService.createHistoricActivityInstanceQuery().processInstanceId(pid).orderByHistoricActivityInstanceStartTime().asc().list();
```

---

## 结语

一句话回顾这次改造：

> **流程定义外置成 BPMN 文件，业务校验留在 Java，`status` 降级为给人看的投影，存量数据靠「双路分流」平滑过渡。**

这套结构带来的直接好处是：**「归还」上线只需改一行配置**，**「加一级审批」只需改流程图**，
而前端与接口契约完全不动。
