# 把「报废申请审批」接入 Flowable —— 改造教程

> 项目：高校固定资产协同管理系统（FAMS）
> 模块：资产报废审批（`scrap_order`）
> 前置阅读：`docs/Flowable审批流程接入教程.md`（领用/归还，已完成并跑通）
> 本文定位：**面向已经做完领用改造的开发者**，因此主线是「差异清单 + 逐项做法」，
> 而不是把领用那份教程重抄一遍。凡是写「同领用教程第 X 节」的地方，请直接翻回去照做。

---

## 目录

1. [结论先行：报废与领用的差异清单](#一结论先行报废与领用的差异清单)
2. [现状核查（代码事实）](#二现状核查代码事实)
3. [方案选择：复用还是独立](#三方案选择复用还是独立)
4. [动手步骤](#四动手步骤)
5. [防漏改对照检查表](#五防漏改对照检查表)
6. [验证清单](#六验证清单)
7. [坑与注意事项](#七坑与注意事项)
8. [附录：改动文件速查](#附录改动文件速查)

---

## 一、结论先行：报废与领用的差异清单

**最重要的结论：报废的两级审批流转规则与领用 100% 相同**（院里初审 → 校级复审，通过/驳回两条路），
所以**不需要新的 BPMN 流程定义**，可以复用 `assetApplyApproval`。

真正的差异只有下面 8 条，其中 **第 1 条是必须处理的阻塞项**，其余都是机械替换。

| # | 差异点 | 领用（现状） | 报废（现状） | 处理方式 |
| --- | --- | --- | --- | --- |
| 1 | **`submitScrap` 没有事务** | `submitApply` 有 `@Transactional` | **`submitScrap` 无 `@Transactional`** | ⚠️ **必须先补上**，否则落库与启动流程各自提交 |
| 2 | 业务表 / 实体 | `apply_order` / `ApplyOrder` | `scrap_order` / `ScrapOrder` | 换类型 |
| 3 | 单号字段名 | `applyNo` | `scrapNo` | 映射时换字段 |
| 4 | 「用途/原因」字段 | `purpose` | `reason` | 不上流程变量则无需处理 |
| 5 | **没有 `applyType` 字段** | `BORROW` / `RETURN` 二选一，配置文件据此放行 | 报废没有类型字段 | 用字面量 `"SCRAP"` 当业务类型标识 |
| 6 | 审批记录的 `applyType` | = 单据的 `applyType` | 硬编码 `"SCRAP"` | 写记录时固定 `"SCRAP"` |
| 7 | 提交时的资产预校验 | 校验「已报废 / 维修中 / 是否闲置 / 是否在用」 | **只校验「已报废」一项** | 保持原样，不改（见坑 2） |
| 8 | 终审通过后的资产动作 | 领用→`在用`+登记使用人；归还→`闲置`+清空使用人 | 一律 → `已报废` + 清空使用人 | 报废只需一条固定逻辑，**不需要按类型分支** |

> 换句话说：**领用改造里那套「双路分流 + 11 步审批 + 状态投影」的骨架可以原样照搬，
> 只有上面 8 个点需要替换。** 第 5 章有一张逐项打勾的对照表。

---

## 二、现状核查（代码事实）

下面是我实际读代码得到的事实，不是推测。**动手前请自行复核一遍**，因为文件可能已被改动。

### 2.1 已经具备的条件（无需额外准备）

| 事实 | 位置 |
| --- | --- |
| Flowable 7.0.0 依赖、`flowable.*` 配置、流程定义 `assetApplyApproval` 均已就绪 | `pom.xml`、`application.yml`、`processes/asset-apply-approval.bpmn20.xml` |
| 编排组件 `ApplyWorkflowService` 已存在，封装了启动/查任务/办理/候选人 | `modules/approval/flow/ApplyWorkflowService.java` |
| `approval_record` 表是**领用与报废共用**的，靠 `apply_type` 区分 | `V1__init_schema.sql` |
| 报废单的状态取值与领用**完全一致** | `ScrapOrder.status` 注释 |

报废单的状态机：

```
PENDING_FIRST → PENDING_SECOND → COMPLETED
      ↓                ↓
 FIRST_REJECT    SECOND_REJECT
```

### 2.2 需要补齐的缺口

| # | 缺口 | 证据（行号基于当前文件） |
| --- | --- | --- |
| 1 | `scrap_order` 表没有 `process_instance_id` 列 | `V1__init_schema.sql:222` 建表语句中无该列 |
| 2 | `ScrapOrder` 实体没有 `processInstanceId` 字段 | `ScrapOrder.java:12-43` |
| 3 | **`submitScrap` 没有 `@Transactional`** | `ScrapServiceImpl.java:31` 方法上无注解；而 `approve` 在第 46 行有 `@Transactional` |
| 4 | `ScrapServiceImpl` 未注入编排组件 | `ScrapServiceImpl.java:24-28` 构造器字段 |
| 5 | `ApplyWorkflowService` 的入参写死为 `ApplyOrder` | `start(ApplyOrder)` / `complete(Task, ApplyOrder, ...)` |

### 2.3 报废模块的自包含性（好消息）

我搜了整个后端与前端，报废的上下游只有这些：

```
后端：ScrapController → ScrapService → ScrapServiceImpl → ScrapOrderMapper
      （StatisticsService 里没有任何报废状态的引用）
前端：ScrapManage.vue → api/scrap.ts → /api/scrap/*
      （无其它页面读取 scrap_order.status）
```

**结论：改造只影响报废模块自身，不存在被其它模块依赖的状态约定**，
因此和领用一样，**前端零改动**（`ScrapManage.vue` 的 `statusText()`、`el-steps`、按钮显示条件全部依赖状态取值，而这些取值不变）。

### 2.4 报废版 `approve` 与领用旧版的差异

`ScrapServiceImpl.approve()` 与领用改造**之前**的 `ApprovalServiceImpl.approve()` 几乎逐行相同，
使用同一套校验顺序（角色 → 不能审自己 → 院系隔离 → 层级匹配 → 状态匹配）和同样的乐观锁写法。

唯一值得顺手修正的地方：

```java
// ScrapServiceImpl.java:57 —— roleId 为 null 时拆箱会抛 NPE
if (roleId == 3) { throw new BusinessException("权限不足：普通师生无权进行审批操作"); }
```

领用改造后的版本写成了空安全形式，建议报废版follow：

```java
if (roleId != null && roleId == 3) { ... }
```

`ScrapController` 从 `SecurityContext` 取 `roleId`，实践中非空，所以这不是必须项，但既然要重写这个方法，顺手对齐更省事。

---

## 三、方案选择：复用还是独立

两条路都可行，先把代价摊开：

| | **方案 A：复用同一条流程（推荐）** | 方案 B：独立 BPMN + 独立编排组件 |
| --- | --- | --- |
| BPMN 文件 | 复用 `asset-apply-approval.bpmn20.xml` | 新增 `scrap-approval.bpmn20.xml`（`process id=scrapApproval`） |
| 编排代码 | 把 `ApplyWorkflowService` 的入参通用化（改 3 个方法签名） | 新建 `ScrapWorkflowService`，**复制约 200 行** |
| 对领用代码的影响 | 需要回归验证领用（应做到行为不变） | **零影响** |
| 流程定义版本 | 两个业务共用同一个 Key，共用一个版本号 | 各自独立演进 |
| 后续「报废需要加一级评估」时 | 需要拆分成方案 B | 直接改自己的 BPMN |
| 维护成本 | 低（单一实现） | 高（两份近乎相同的实现，改一处要改两处） |

### 推荐：方案 A

理由：两条流程的流转规则**当前完全相同**，此时抽象**确实降低了复杂度**（而不是为了模式而模式）。
方案 A 的具体做法是**新增通用入口 + 保留原入口做委托**，
这样领用那条调用链的代码一行不用改，回归面可控：

```java
// 保留：领用/归还入口，对外行为不变
public String start(ApplyOrder order) {
    return start(WorkflowBizContext.from(order));
}

// 新增：通用入口（报废调用这个）
public String start(WorkflowBizContext ctx) { ... }
```

### 什么时候该升级到方案 B

出现下面任一信号，就应该把报废拆成独立流程：

- 报废需要**多一级**审批（例如增加「资产处技术鉴定」）或**会签**；
- 报废想按**金额阈值**走不同路径（如原值 > 5 万才需校级复审）；
- 报废的审批角色划分与领用不再一致（例如改成由资产处而非学院初审）；
- 两个业务的流程定义需要**独立回滚**（一个要回退版本，另一个不能动）。

> 升级路径：新建 `scrap-approval.bpmn20.xml` → 配置改为「业务类型 → 流程 Key」映射
> （写法见领用教程 §7.4）→ 把 `ScrapWorkflowService` 从通用组件里切出来。

---

## 四、动手步骤

按顺序做，共 7 步。第 1、2 步是准备，第 3 步是唯一有回归风险的一步，第 4~6 步是报废自己的事。

### 步骤 1｜数据库迁移 V8

新建 `back-end/src/main/resources/db/migration/V8__add_process_instance_id_to_scrap.sql`：

```sql
-- ============================================
-- Flyway V8: 报废申请单接入 Flowable 工作流
-- ============================================
-- 设计同 V7：该列可空，为空即表示该报废单未接入工作流，
-- 审批时自动回落到原有的硬编码状态机分支，存量数据无需回填。

ALTER TABLE scrap_order
    ADD COLUMN process_instance_id VARCHAR(64) NULL COMMENT 'Flowable 流程实例ID（NULL=未接入工作流）' AFTER status;

CREATE INDEX idx_scrap_order_process_instance ON scrap_order (process_instance_id);
```

> ⚠️ 版本号必须接着现有最大版本往下排（当前最大是 `V7`）。**不要修改已有的 V1~V7 文件**，
> Flyway 会校验历史脚本的 checksum，改动已执行过的脚本会导致启动失败。

### 步骤 2｜实体加字段

`ScrapOrder.java`，在 `status` 字段后追加：

```java
    /** 审批状态：PENDING_FIRST / FIRST_REJECT / PENDING_SECOND / SECOND_REJECT / COMPLETED */
    private String status;

    /**
     * Flowable 流程实例ID。
     * <p>非空 → 由 Flowable 流程实例驱动，审批按 BPMN 节点流转；</p>
     * <p>为空 → 未接入工作流，审批走 {@code ScrapServiceImpl} 原有的状态机分支。</p>
     */
    private String processInstanceId;
```

### 步骤 3｜把编排组件通用化（唯一有回归风险的一步）

目标：**新增 `WorkflowBizContext` 作为「流程 ←→ 业务单」的最小契约，原方法保留并委托**。
做完这一步，先**把领用流程回归验证一遍**，确认行为不变，再继续。

#### 3.1 新建 `WorkflowBizContext`

文件：`back-end/src/main/java/com/example/asset/modules/approval/flow/WorkflowBizContext.java`

```java
package com.example.asset.modules.approval.flow;

import com.example.asset.modules.approval.entity.ApplyOrder;

/**
 * 流程引擎与业务单据之间的最小契约。
 *
 * <p>引入它的目的：让 {@link ApplyWorkflowService} 不再依赖某个具体的单据实体，
 * 从而可以同时服务领用/归还（{@code apply_order}）与报废（{@code scrap_order}）。</p>
 *
 * <p>注意：这里**只放流程需要的信息**，不要塞业务字段。业务校验依然留在各自的 Service 层。</p>
 */
public record WorkflowBizContext(
        Long bizId,            // 业务单ID，作为 Flowable businessKey
        String bizNo,          // 单号，仅作流程变量留痕
        String bizType,        // 业务类型：BORROW / RETURN / SCRAP
        Long assetId,
        String assetCode,
        String assetName,
        Long deptId,           // 申请人所属部门，用于初审候选人指派
        String deptName,
        Long applyUserId,
        String applyUserName) {

    /** 领用/归还申请 → 流程上下文 */
    public static WorkflowBizContext from(ApplyOrder o) {
        return new WorkflowBizContext(o.getId(), o.getApplyNo(), o.getApplyType(),
                o.getAssetId(), o.getAssetCode(), o.getAssetName(),
                o.getDeptId(), o.getDeptName(),
                o.getApplyUserId(), o.getApplyUserName());
    }
}
```

> 报废单的工厂方法**故意不放这里**：`modules.approval` 不应该反向依赖 `modules.scrap`。
> 由 `ScrapServiceImpl` 自己拼装（见步骤 5）。

#### 3.2 改造 `ApplyWorkflowService`

改动只有 3 组方法签名，**逻辑一行不动**：

```java
// ① start：新增通用入口，原入口改为委托
public String start(ApplyOrder order) {
    return start(WorkflowBizContext.from(order));
}

public String start(WorkflowBizContext ctx) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("applyId", ctx.bizId());
    variables.put("applyNo", ctx.bizNo());
    variables.put("applyType", ctx.bizType());
    variables.put("assetId", ctx.assetId());
    variables.put("assetCode", ctx.assetCode());
    variables.put("assetName", ctx.assetName());
    variables.put("deptId", ctx.deptId());
    variables.put("deptName", ctx.deptName());
    variables.put("applyUserId", ctx.applyUserId());
    variables.put("applyUserName", ctx.applyUserName());

    ProcessInstance instance;
    try {
        instance = runtimeService.startProcessInstanceByKey(
                properties.getProcessKey(), String.valueOf(ctx.bizId()), variables);
    } catch (FlowableException e) {
        throw new BusinessException("流程发起失败：" + e.getMessage());
    }
    String pid = instance.getId();
    assignCandidates(currentTask(pid), ctx);
    return pid;
}

// ② complete：新增通用入口，原入口改为委托
public void complete(Task task, ApplyOrder order, boolean approved,
                     Long approverId, String approverName, String comment) {
    complete(task, WorkflowBizContext.from(order), approved, approverId, approverName, comment);
}

public void complete(Task task, WorkflowBizContext ctx, boolean approved,
                     Long approverId, String approverName, String comment) {
    // ...原有实现完全不变，只把末尾的 assignCandidates(currentTask(...), order)
    //    改成 assignCandidates(currentTask(...), ctx)
}

// ③ assignCandidates：入参由 ApplyOrder 换成 WorkflowBizContext
private void assignCandidates(Task task, WorkflowBizContext ctx) {
    if (task == null) return;
    // ...原有逻辑不变，把 order.getDeptId() 换成 ctx.deptId()
}
```

**保持不变的部分**（这些是设计的一部分，不要顺手改）：

- `supports(String bizType)` —— 参数语义从「申请类型」放宽为「业务类型」，实现不用动；
- `currentTask` / `levelOf` / `statusOf` —— 报废的状态取值与领用一致，直接复用；
- 流程变量名继续叫 `applyId` / `applyNo` —— BPMN 里并不引用它们，改名只是 `ACT_RU_VARIABLE` 里的观感问题。
  **想改就整套一起改，风险很小；不想改就保持原样，功能完全等价。**

#### 3.3 立刻回归领用

改完这一步先跑一遍领用流程（可参考领用教程 §9.3 的接口步骤），确认：
提交后 `process_instance_id` 非空 → 初审通过变 `PENDING_SECOND` → 复审通过变 `COMPLETED`。
**这一遍通过后再往下做**，这样一旦出问题，你能确定是通用化引入的，而不是报废代码引入的。

### 步骤 4｜配置放行 SCRAP

`application.yml`：

```yaml
approval:
  workflow:
    process-key: assetApplyApproval
    # 已接入 Flowable 的业务类型：领用 + 报废
    # （归还由你自己按领用教程第七章决定何时加入）
    enabled-apply-types: BORROW,SCRAP
```

`scrap_order` 没有 `apply_type` 字段，所以 `supports()` 的入参由你在 `ScrapServiceImpl` 里传字面量 `"SCRAP"`。

**可选但建议**：把 BPMN 里 `<process name="资产领用归还审批流程">` 改成更中性的名字
（如 `资产申请审批流程`），免得以后看到报废单挂在一个叫「领用归还」的流程上产生困惑。

> 📌 注意：**只要 BPMN 文件内容有变化（哪怕只改 `name`），Flowable 部署时就会生成新版本**，
> `ACT_RE_PROCDEF.VERSION_` 从 1 变成 2。这是正常行为：
> 新发起的实例用最新版本，**已经在跑的实例继续跑它们启动时的那个版本**。
> 不需要清理旧版本，也不影响存量数据。

### 步骤 5｜改造 `ScrapServiceImpl`

#### 5.1 注入编排组件与类型常量

```java
private static final String BIZ_TYPE = "SCRAP";

private final ScrapOrderMapper scrapOrderMapper;
private final AssetMapper assetMapper;
private final ApprovalRecordMapper approvalRecordMapper;
private final ApplyWorkflowService applyWorkflowService;   // ← 新增
```

#### 5.2 `submitScrap`：补事务 + 启动流程

```java
    @Override
    @Transactional                                     // ★ 必须新增（原代码没有事务）
    public void submitScrap(ScrapOrder order) {
        // 校验资产状态：已报废的资产不允许重复报废（原有校验，原样保留）
        Asset asset = assetMapper.selectById(order.getAssetId());
        if (asset == null) {
            throw new BusinessException("资产不存在");
        }
        if ("已报废".equals(asset.getStatus())) {
            throw new BusinessException("该资产已报废，请勿重复操作");
        }

        order.setStatus("PENDING_FIRST");
        scrapOrderMapper.insert(order);

        // ===== 新增：接入 Flowable =====
        if (applyWorkflowService.supports(BIZ_TYPE)) {
            String processInstanceId = applyWorkflowService.start(toContext(order));
            order.setProcessInstanceId(processInstanceId);
            scrapOrderMapper.updateById(order);
        }
    }

    /** 报废单 → 流程上下文 */
    private WorkflowBizContext toContext(ScrapOrder order) {
        return new WorkflowBizContext(
                order.getId(), order.getScrapNo(), BIZ_TYPE,
                order.getAssetId(), order.getAssetCode(), order.getAssetName(),
                order.getDeptId(), order.getDeptName(),
                order.getApplyUserId(), order.getApplyUserName());
    }
```

新增 import：

```java
import com.example.asset.modules.approval.flow.ApplyWorkflowService;
import com.example.asset.modules.approval.flow.WorkflowBizContext;
import org.flowable.task.api.Task;
import org.springframework.util.StringUtils;
```

#### 5.3 `approve`：改为双路分流

把原来的方法体整体改名成 `approveByStatusMachine`（**一个字都不要改**），外层只做分流：

```java
    @Override
    @Transactional
    public void approve(Long orderId, Long approveUserId, String approveUserName, Integer roleId,
                        Long approveUserDeptId, String level, String result, String comment) {
        ScrapOrder order = scrapOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("报废申请单不存在");
        }

        if (StringUtils.hasText(order.getProcessInstanceId())) {
            approveByWorkflow(order, approveUserId, approveUserName, roleId,
                              approveUserDeptId, level, result, comment);
        } else {
            approveByStatusMachine(order, approveUserId, approveUserName, roleId,
                                   approveUserDeptId, level, result, comment);
        }
    }
```

#### 5.4 `approveByWorkflow`：照搬领用版，只改 4 处

直接取 **领用教程 §5.6** 里的 `approveByWorkflow` 全文，然后只做下面 4 处替换：

| 处 | 领用版 | 报废版要改成 |
| --- | --- | --- |
| 1 | `ApplyOrder order`、`applyOrderMapper` | `ScrapOrder order`、`scrapOrderMapper` |
| 2 | `record.setApplyType(order.getApplyType())` | `record.setApplyType("SCRAP")`（或复用 `BIZ_TYPE` 常量） |
| 3 | 校验消息里的「申请单」 | 建议改成「报废单」，便于前端定位问题 |
| 4 | `updateAssetOnFinalApproval(order)` 按 applyType 分支 | 换成下面的 `scrapAsset(order)`：报废**无需分支** |

其余部分**逐一对应、不要改动**，包括：

- 引擎取当前任务 → `currentLevel`；
- 前端传入的 `level` 必须与引擎节点一致（防页面过期）；
- `roleId != null && roleId == 3` 拒绝普通师生、`roleId != null && roleId == 2` 的院系隔离与「只能初审」、
  `roleId != null && roleId == 1` 的「只能复审」；
- `expectedStatus` 与流程节点一致性校验（并发第一道闸）；
- 写 `approval_record`（`approveLevel` 用 `currentLevel`）；
- 调用 `applyWorkflowService.complete(task, toContext(order), approved, approveUserId, approveUserName, comment)`；
- 依引擎新状态回写 `status`（`PENDING_SECOND` / `COMPLETED` / `FIRST_REJECT` / `SECOND_REJECT`）；
- 乐观锁 `eq(ScrapOrder::getStatus, order.getStatus())`（并发第二道闸）。

#### 5.5 终审通过的资产收尾

```java
    /** 报废终审通过：资产置为已报废并清空使用人 */
    private void scrapAsset(ScrapOrder order) {
        Asset asset = assetMapper.selectById(order.getAssetId());
        if (asset == null) {
            return;
        }
        assetMapper.update(null, new LambdaUpdateWrapper<Asset>()
                .eq(Asset::getId, asset.getId())
                .set(Asset::getStatus, "已报废")
                .set(Asset::getUserId, null)
                .set(Asset::getUserName, null));
    }
```

调用条件与领用版一致：`approved && nextTask == null && LEVEL_SECOND.equals(currentLevel)`。

### 步骤 6｜`getScrapDetail` / `pageOrders` 不动

这两个方法**不需要任何改动**：

- `pageOrders` 只按 `status` / `deptId` 过滤，与是否接入流程无关；
- `getScrapDetail` 已经带了 `eq(ApprovalRecord::getApplyType, "SCRAP")` 过滤 ——
  这条**千万别删**，`approval_record` 是领用和报废共用的表，去掉它会把领用的审批记录混进来。

### 步骤 7｜前端不动

`ScrapManage.vue` 的状态取值、`el-steps` 计算、按钮显示条件全部依赖 `status`，
而 `status` 的语义没有变化，因此**零改动**。

唯一的行为差异（与领用一致）：页面停留过久、单据已被他人审批时，会返回更明确的
「审批层级与流程当前节点不一致，请刷新页面后重试」，以及流程已结束时的
「当前流程已结束，无可办理的审批任务」。

---

## 五、防漏改对照检查表

如果你已经做完领用改造，请逐项打勾。**打不了勾的就是漏改的地方。**

| # | 检查项 | 领用 | 报废 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Flyway 迁移脚本已建 | V7 | **V8** | 加列 + 索引 |
| 2 | 实体加了 `processInstanceId` | ✅ | ☐ | |
| 3 | `submitXxx` 有 `@Transactional` | 原本就有 | **☐ 需要新增** | 最易漏，后果最重 |
| 4 | `submitXxx` 落库后启动流程 | ✅ | ☐ | 先 insert 拿 ID，再 start |
| 5 | 配置放行业务类型 | `BORROW` | `BORROW,SCRAP` | |
| 6 | `approve` 按 `processInstanceId` 分流 | ✅ | ☐ | |
| 7 | 原状态机逻辑完整保留为 `approveByStatusMachine` | ✅ | ☐ | 存量数据要能继续审 |
| 8 | 审批记录写入 `approveLevel` = 引擎的 `currentLevel` | ✅ | ☐ | 不是前端传的 `level` |
| 9 | `apply_type` 写对 | 单据的 applyType | `"SCRAP"` | |
| 10 | 状态回写覆盖 4 个终态 + 1 个中间态 | ✅ | ☐ | `PENDING_SECOND`/`COMPLETED`/`FIRST_REJECT`/`SECOND_REJECT` |
| 11 | 乐观锁条件仍用「旧 status」 | ✅ | ☐ | |
| 12 | 终审通过后同步资产状态 | 按 applyType 分支 | 固定「已报废」 | |
| 13 | 分页与详情查询未受影响 | ✅ | ☐ | 详情里 `apply_type='SCRAP'` 过滤要保留 |
| 14 | 前端未改动 | ✅ | ☐ | |

---

## 六、验证清单

> 说明：下面的步骤**尚未执行**（本次只交付教程）。请按顺序跑，并对照「预期结果」逐项确认。

### 6.1 构建与启动

```bash
cd back-end
mvn -o -B -DskipTests clean compile
mvn -o -B -DskipTests spring-boot:run "-Dspring-boot.run.arguments=--server.port=18888"
```

> 端口覆盖的原因见领用教程：环境会注入 `SERVER_PORT=50265`，而该端口被 WorkBuddy 自身占用。

启动后先确认迁移生效：

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;
-- 预期：V8 / add process instance id to scrap / 1

SHOW COLUMNS FROM scrap_order LIKE 'process_instance_id';
-- 预期：一行，类型 varchar(64)，Null = YES
```

### 6.2 数据库自查 SQL

```sql
-- ① 报废单是否已挂上流程实例
SELECT ID, scrap_no, status, process_instance_id FROM scrap_order ORDER BY id DESC LIMIT 5;

-- ② 两个业务是否共用同一个流程定义（预期只有 1 行，VERSION_ 可能已升到 2）
SELECT ID_, KEY_, NAME_, VERSION_ FROM ACT_RE_PROCDEF;

-- ③ 报废单的流程实例（businessKey 就是 scrap_order.id）
SELECT t.ID_ AS task_id, t.TASK_DEF_KEY_, e.BUSINESS_KEY_ AS scrap_order_id
FROM ACT_RU_TASK t
JOIN ACT_RU_EXECUTION e ON e.ID_ = t.PROC_INST_ID_;

-- ④ 报废单的审批轨迹（流程结束后看这里）
SELECT TASK_DEF_KEY_, ASSIGNEE_, START_TIME_, END_TIME_
FROM ACT_HI_TASKINST WHERE PROC_INST_ID_ = '<pid>' ORDER BY START_TIME_;

-- ⑤ 审批记录没被污染：领用与报废各自独立
SELECT apply_type, COUNT(*) FROM approval_record GROUP BY apply_type;
```

### 6.3 接口自测

```bash
# 1) 用普通师生登录（计算机学院：liuyang / 123456）
curl -X POST http://localhost:18888/api/auth/login -H "Content-Type: application/json" \
     -d '{"username":"liuyang","password":"123456"}'

# 2) 提交报废申请（<TOKEN> 换成上一步的 token；<ASSET_ID> 换成一件未报废资产）
#    ⚠️ 必须带 applyUserId / applyUserName，否则 apply_user_id 会是 NULL（见坑 3）
curl -X POST http://localhost:18888/api/scrap/apply -H "Content-Type: application/json" \
     -H "Authorization: Bearer <TOKEN>" \
     -d '{"assetId":4,"assetCode":"AST-2023-010","assetName":"投影仪","reason":"设备老化","applyUserId":3,"applyUserName":"刘洋"}'

# 3) 学院管理员初审通过（zhangwei / 123456）
curl -X POST http://localhost:18888/api/scrap/approve -H "Content-Type: application/json" \
     -H "Authorization: Bearer <TOKEN_ZHANGWEI>" \
     -d '{"orderId":<ID>,"level":"FIRST","result":"PASS","comment":"同意报废"}'

# 4) 超级管理员复审通过（admin / 123456）→ 流程结束、资产变「已报废」
curl -X POST http://localhost:18888/api/scrap/approve -H "Content-Type: application/json" \
     -H "Authorization: Bearer <TOKEN_ADMIN>" \
     -d '{"orderId":<ID>,"level":"SECOND","result":"PASS","comment":"批准"}'
```

### 6.4 必测用例（含负向）

| # | 用例 | 预期结果 |
| --- | --- | --- |
| 1 | 提交报废 → 查 `scrap_order` | `status=PENDING_FIRST`，`process_instance_id` **非空** |
| 2 | 普通师生（roleId=3）自己审批 | 拒绝：`权限不足：普通师生无权进行审批操作` |
| 3 | 超管直接做初审 | 拒绝：`权限不足：超级管理员仅可进行复审操作` |
| 4 | 外学院管理员审批本院报废单 | 拒绝：`权限不足：学院管理员仅可审批本学院的报废单` |
| 5 | 初审通过 | `status=PENDING_SECOND` |
| 6 | 复审通过 | `status=COMPLETED`；资产 → `已报废`，`user_id`/`user_name` 清空 |
| 7 | 初审驳回 | `status=FIRST_REJECT`；资产状态**不变** |
| 8 | 对已结束的单子再审批 | 拒绝：`当前流程已结束，无可办理的审批任务` |
| 9 | 传错 `level`（流程在初审却传 `SECOND`） | 拒绝：`审批层级与流程当前节点不一致，请刷新页面后重试` |
| 10 | **存量报废单**（改造前提交、`process_instance_id` 为 NULL）继续审批 | 仍能正常走完，状态正确 —— 验证双路分流没误伤历史数据 |
| 11 | 报废成功后确认领用的审批记录未被混入 | `approval_record` 里 `apply_type` 区分正确 |

> 第 10 条尤其重要：它验证的是**兼容性**，不是新功能。这是「双路分流」设计的核心价值，别跳过。

---

## 七、坑与注意事项

### 坑 1｜`submitScrap` 没有事务 —— 本次改造最容易踩的坑

**现状**：`ScrapServiceImpl.java:31` 的 `submitScrap` 没有 `@Transactional`（`approve` 有）。
领用模块的 `submitApply` 是有事务的，所以直接照搬领用的写法时很容易忽略这一点。

**后果**（两步各自独立提交）：

```
insert scrap_order（事务1，已提交）
        ↓
startProcessInstance（事务2）
        ↓ 如果这一步抛异常
申请单已经落库，但 process_instance_id 为 NULL
```

此时这张单子**看起来完全正常** —— 前端能显示、能点审批、也能审批成功，
但它是走**老状态机**分支走完的，`ACT_*` 表里**没有任何流程实例**。
这种"静默降级"在测试时很难发现，上线后才发现「流程追溯查不到」。

**解法**：给 `submitScrap` 加上 `@Transactional`，让「落库 + 启动流程」在同一事务里，
任一步失败整体回滚。

（`approve` 已经有 `@Transactional`，且 MyBatis 与 Flowable 共用同一个 `DataSource` 和事务管理器，
所以审批链路上的事务一致性是有保障的，这一点和领用相同。）

### 坑 2｜报废的资产预校验比领用宽松

`submitScrap` 只校验「已报废」，**不校验「维修中」**；而 `submitApply` 会拦掉「已报废」和「维修中」。
也就是说**维修中的资产目前可以提交报废申请**。

这是**既有行为，接入 Flowable 不会改变它**。
本教程**不擅自改**（超出范围），但请确认这是否符合业务预期；
若需要收紧，应在 `submitScrap` 里补上校验，**并与 Flowable 的接入分开提交**，便于独立回滚。

### 坑 3｜申请人身份同样来自请求体

`ScrapController.apply()`（第 27~34 行）只从 `SecurityContext` 回填了 `deptId` / `deptName`，
**`applyUserId` / `applyUserName` 直接取请求体**，与领用模块的问题完全一致
（详见领用教程「坑 12」）。

影响在报废场景同样存在：`apply_user_id` 为 NULL 时，「不能审批自己提交的申请」校验失效。
**前端 `ScrapManage.vue` 会正常传这两个字段**（第 167 行），所以页面操作不受影响；
但如果你用脚本直接调接口，务必带上，否则按 `applyUserId` 查询会查不到这张单子。

### 坑 4｜改动 BPMN 会生成新版本，这是正常的

只要 `asset-apply-approval.bpmn20.xml` 内容有变化，下次启动就会部署成新版本
（`ACT_RE_PROCDEF.VERSION_` 递增）。

- 新发起的实例使用最新版本；
- **已经在跑的实例仍然运行它们启动时的那个版本**，不受影响；
- 不需要手工清理旧版本（历史表里保留着，追溯反而更有用）。

如果不希望产生新版本，就**不要改 BPMN 文件**（连 `name` 都不要动）。流程变量名不是必须改的。

### 坑 5｜`approval_record` 是共用表，`apply_type` 不能漏

`getScrapDetail` 里已有 `eq(ApprovalRecord::getApplyType, "SCRAP")`，
而 `getOrderDetail`（领用）**没有**这个条件（因为领用表里有 `apply_type` 可以关联）。

写审批记录时，报废必须固定写 `"SCRAP"`，否则：
- 报废详情里查不到自己的审批记录；
- 或者领用详情里混进报废的记录。

### 坑 6｜别把 `pageOrders` 的院系隔离改坏

`pageOrders` 里「学院管理员强制按自己 deptId 过滤、忽略前端传入 deptId」这段逻辑与是否接入 Flowable 无关，
**不要在这次改造中动它**。

---

## 附录：改动文件速查

| 动作 | 文件 | 改动量 |
| --- | --- | --- |
| 🆕 新增 | `db/migration/V8__add_process_instance_id_to_scrap.sql` | ~10 行 |
| 🆕 新增 | `modules/approval/flow/WorkflowBizContext.java` | ~40 行 |
| ✏️ 修改 | `modules/approval/flow/ApplyWorkflowService.java` | 3 组方法签名（逻辑不变） |
| ✏️ 修改 | `modules/scrap/entity/ScrapOrder.java` | +1 字段 |
| ✏️ 修改 | `modules/scrap/service/impl/ScrapServiceImpl.java` | 补事务、注入组件、分流、新增 `approveByWorkflow` / `scrapAsset` / `toContext` |
| ✏️ 修改 | `resources/application.yml` | `enabled-apply-types: BORROW,SCRAP` |
| ⭕ 可选 | `resources/processes/asset-apply-approval.bpmn20.xml` | 仅改 `name`（会触发新版本部署） |
| ❌ 不改 | `ScrapController` / `ScrapService` 接口 / 前端全部文件 | — |

**没有新增 BPMN 文件**（方案 A）。这是本次改造相比领用最省事的地方。
