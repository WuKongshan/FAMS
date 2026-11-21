package com.example.asset.modules.approval.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.asset.common.exception.BusinessException;
import com.example.asset.modules.admin.entity.User;
import com.example.asset.modules.admin.mapper.UserMapper;
import com.example.asset.modules.approval.entity.ApplyOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批流程的 Flowable 编排组件。
 *
 * <p>本类只负责「与流程引擎打交道」，不承载任何业务校验：</p>
 * <ul>
 *   <li>启动流程实例（{@link #start(WorkflowBizContext)}）</li>
 *   <li>查询当前活动任务（{@link #currentTask}）</li>
 *   <li>办理任务并推进流程（{@link #complete(Task, WorkflowBizContext, boolean, Long, String, String)}）</li>
 *   <li>给待办任务指派候选人（{@link #assignCandidates}）</li>
 * </ul>
 *
 * <p>角色权限与院系隔离等业务规则仍在各自的 Service 中校验。这样分工的理由是：
 * 「学院管理员只能审本学院的单子」这类规则依赖业务数据（deptId），
 * 用 Java 表达比写进 BPMN 表达式更直观、更好测试。</p>
 *
 * <p>入参使用 {@link WorkflowBizContext} 而非具体实体，因此同一套编排逻辑可以同时服务
 * 领用/归还（{@code apply_order}）与报废（{@code scrap_order}）。
 * 为保持领用侧调用链不变，原有的 {@code ApplyOrder} 重载被保留为纯委托。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplyWorkflowService {

    /* ==================== BPMN 节点ID（与 asset-apply-approval.bpmn20.xml 一一对应） ==================== */

    /** 院系初审节点 */
    public static final String TASK_FIRST = "firstApproval";
    /** 校级复审节点 */
    public static final String TASK_SECOND = "secondApproval";

    /* ==================== 流程变量名 ==================== */

    /** 初审是否通过（Boolean），用于 gatewayFirst 的条件判断 */
    public static final String VAR_FIRST_APPROVED = "firstApproved";
    /** 复审是否通过（Boolean），用于 gatewaySecond 的条件判断 */
    public static final String VAR_SECOND_APPROVED = "secondApproved";

    /* ==================== 审批层级 ==================== */

    public static final String LEVEL_FIRST = "FIRST";
    public static final String LEVEL_SECOND = "SECOND";

    /* ==================== 状态常量（与前端既有约定一致） ==================== */

    public static final String STATUS_PENDING_FIRST = "PENDING_FIRST";
    public static final String STATUS_PENDING_SECOND = "PENDING_SECOND";

    /* ==================== 角色ID / 用户状态（与 sys_role、sys_user 表一致） ==================== */

    private static final Long ROLE_ADMIN = 1L;
    private static final Long ROLE_DEPT_ADMIN = 2L;
    private static final Integer USER_ENABLED = 1;

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final UserMapper userMapper;
    private final ApplyWorkflowProperties properties;

    /**
     * 判断某种业务类型是否已接入 Flowable。
     * 未接入的类型仍走原有状态机逻辑，保证平滑过渡。
     *
     * @param bizType 业务类型：BORROW / RETURN / SCRAP（报废没有 applyType 字段，传字面量 "SCRAP"）
     */
    public boolean supports(String bizType) {
        return bizType != null && properties.getEnabledApplyTypes().contains(bizType);
    }

    /* ====================================================================================
     *  启动流程实例
     * ==================================================================================== */

    /** 领用/归还入口：委托给通用实现，保持既有调用链不变 */
    public String start(ApplyOrder order) {
        return start(WorkflowBizContext.from(order));
    }

    /**
     * 以业务单ID 作为 businessKey 启动流程实例。
     *
     * @return Flowable 流程实例ID
     */
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
                    properties.getProcessKey(),
                    String.valueOf(ctx.bizId()),   // businessKey：反查业务单据用
                    variables);
        } catch (FlowableException e) {
            // 典型场景：流程定义未部署 / processKey 配错 / BPMN 文件语法错误
            throw new BusinessException("流程发起失败：" + e.getMessage());
        }

        String processInstanceId = instance.getId();
        log.info("[Flowable] 业务单 {}（{}）发起流程实例 {}，流程定义Key={}",
                ctx.bizNo(), ctx.bizType(), processInstanceId, properties.getProcessKey());

        assignCandidates(currentTask(processInstanceId), ctx);
        return processInstanceId;
    }

    /**
     * 查询流程实例当前的活动任务。
     * 本流程为串行两级审批，任一时刻最多只有一个活动任务；流程已结束则返回 null。
     */
    public Task currentTask(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            return null;
        }
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .orderByTaskCreateTime().asc()
                .list();
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    /* ====================================================================================
     *  办理任务
     * ==================================================================================== */

    /** 领用/归还入口：委托给通用实现，保持既有调用链不变 */
    public void complete(Task task, ApplyOrder order, boolean approved,
                         Long approverId, String approverName, String comment) {
        complete(task, WorkflowBizContext.from(order), approved, approverId, approverName, comment);
    }

    /**
     * 办理任务，把审批结果写入流程变量，由排他网关决定下一步走向。
     *
     * @param task         要办理的任务
     * @param ctx          对应业务单据（用于给下一节点指派候选人）
     * @param approved     是否通过
     * @param approverId   审批人ID
     * @param approverName 审批人姓名
     * @param comment      审批意见（同时写入 Flowable 历史评论表 ACT_HI_COMMENT）
     */
    public void complete(Task task, WorkflowBizContext ctx, boolean approved,
                         Long approverId, String approverName, String comment) {
        String taskKey = task.getTaskDefinitionKey();
        Map<String, Object> variables = new HashMap<>();
        if (TASK_FIRST.equals(taskKey)) {
            variables.put(VAR_FIRST_APPROVED, approved);
        } else if (TASK_SECOND.equals(taskKey)) {
            variables.put(VAR_SECOND_APPROVED, approved);
        } else {
            throw new BusinessException("流程当前节点不支持审批：" + taskKey);
        }
        variables.put("lastApproverId", approverId);
        variables.put("lastApproverName", approverName);
        variables.put("lastComment", comment);

        // 引擎侧留痕：审批意见写入 ACT_HI_COMMENT，便于流程追溯
        if (StringUtils.hasText(comment)) {
            taskService.addComment(task.getId(), task.getProcessInstanceId(), comment);
        }
        // 记录真实办理人，历史表 ACT_HI_TASKINST.ASSIGNEE_ 即可反查「谁办的」
        taskService.setAssignee(task.getId(), String.valueOf(approverId));

        try {
            taskService.complete(task.getId(), variables);
        } catch (FlowableException e) {
            throw new BusinessException("流程办理失败：" + e.getMessage());
        }
        log.info("[Flowable] 任务 {}（{}）已由 {} 办理，通过={}", task.getId(), taskKey, approverName, approved);

        // 流程推进后，给新产生的待办任务重新指派候选人
        assignCandidates(currentTask(task.getProcessInstanceId()), ctx);
    }

    /* ====================================================================================
     *  节点 → 语义映射
     * ==================================================================================== */

    /** 任务定义Key → 审批层级；未知节点返回 null */
    public String levelOf(Task task) {
        if (task == null) {
            return null;
        }
        if (TASK_FIRST.equals(task.getTaskDefinitionKey())) {
            return LEVEL_FIRST;
        }
        if (TASK_SECOND.equals(task.getTaskDefinitionKey())) {
            return LEVEL_SECOND;
        }
        return null;
    }

    /** 任务定义Key → 业务状态值（保持与前端既有状态常量一致）；未知节点返回 null */
    public String statusOf(Task task) {
        if (task == null) {
            return null;
        }
        if (TASK_FIRST.equals(task.getTaskDefinitionKey())) {
            return STATUS_PENDING_FIRST;
        }
        if (TASK_SECOND.equals(task.getTaskDefinitionKey())) {
            return STATUS_PENDING_SECOND;
        }
        return null;
    }

    /* ====================================================================================
     *  候选人指派
     * ==================================================================================== */

    /**
     * 为待办任务指派候选人：
     * <ul>
     *   <li>初审 → 申请人所在学院的资产管理员</li>
     *   <li>复审 → 校级超级管理员</li>
     * </ul>
     *
     * <p>候选人只影响 Flowable 侧「谁能看到这条待办」（ACT_RU_IDENTITYLINK），
     * 真正的准入判定仍在 Service 层，二者互为补充。</p>
     */
    private void assignCandidates(Task task, WorkflowBizContext ctx) {
        if (task == null) {
            return;   // 流程已结束，无待办
        }
        String taskKey = task.getTaskDefinitionKey();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getStatus, USER_ENABLED);

        if (TASK_FIRST.equals(taskKey)) {
            if (ctx == null || ctx.deptId() == null) {
                log.warn("[Flowable] 初审任务 {} 无法指派候选人：业务单缺少 deptId", task.getId());
                return;
            }
            wrapper.eq(User::getRoleId, ROLE_DEPT_ADMIN)
                   .eq(User::getDeptId, ctx.deptId());
        } else if (TASK_SECOND.equals(taskKey)) {
            wrapper.eq(User::getRoleId, ROLE_ADMIN);
        } else {
            return;
        }

        List<User> candidates = userMapper.selectList(wrapper);
        for (User candidate : candidates) {
            taskService.addCandidateUser(task.getId(), String.valueOf(candidate.getId()));
        }
        log.info("[Flowable] 任务 {}（{}）指派候选人 {} 人", task.getId(), taskKey, candidates.size());
    }
}
