package com.example.asset.modules.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.asset.common.exception.BusinessException;
import com.example.asset.common.utils.BizNoGenerator;
import com.example.asset.modules.approval.entity.ApplyOrder;
import com.example.asset.modules.approval.entity.ApprovalRecord;
import com.example.asset.modules.approval.flow.ApplyWorkflowService;
import com.example.asset.modules.approval.mapper.ApplyOrderMapper;
import com.example.asset.modules.approval.mapper.ApprovalRecordMapper;
import com.example.asset.modules.approval.service.ApprovalService;
import com.example.asset.modules.asset.entity.Asset;
import com.example.asset.modules.asset.mapper.AssetMapper;
import com.example.asset.modules.asset.service.AssetPendingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 领用/归还审批服务。
 *
 * <p><b>双路分流设计</b>：申请单是否由 Flowable 驱动，完全取决于 {@code apply_order.process_instance_id} 是否为空。</p>
 * <ul>
 *   <li><b>非空</b> → 该单已发起 BPMN 流程实例，审批走 {@link #approveByWorkflow}；
 *       流程“现在该谁办”“通过后去哪”由 Flowable 的 BPMN 定义决定。</li>
 *   <li><b>为空</b> → 未接入工作流（历史数据、以及暂未启用的申请类型），
 *       审批走 {@link #approveByStatusMachine}，即改造前的硬编码状态机逻辑。</li>
 * </ul>
 *
 * <p>这种“以数据判定走哪条路”的写法，使得存量数据无需回填、且新老逻辑可长期共存，
 * 后续把「归还」接入时也不必改动 Service 层。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor  //隐式注入 省略@Autowired
public class ApprovalServiceImpl implements ApprovalService {

    private final ApplyOrderMapper applyOrderMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final AssetMapper assetMapper;
    private final ApplyWorkflowService applyWorkflowService;
    private final AssetPendingService assetPendingService;

    @Override
    @Transactional
    public void submitApply(ApplyOrder order) {
        // 校验资产状态
        Asset asset = assetMapper.selectById(order.getAssetId());
        if (asset == null) {
            throw new BusinessException("资产不存在");
        }
        if ("已报废".equals(asset.getStatus())) {
            throw new BusinessException("该资产已报废，无法操作");
        }
        if ("维修中".equals(asset.getStatus())) {
            throw new BusinessException("该资产正在维修中，无法操作");
        }
        if ("BORROW".equals(order.getApplyType()) && !"闲置".equals(asset.getStatus())) {
            throw new BusinessException("该资产当前状态为【" + asset.getStatus() + "】，仅闲置资产可领用");
        }
        if ("RETURN".equals(order.getApplyType()) && !"在用".equals(asset.getStatus())) {
            throw new BusinessException("该资产当前状态为【" + asset.getStatus() + "】，仅在用资产可归还");
        }

        order.setStatus("PENDING_FIRST");
        applyOrderMapper.insert(order);

        // 生成申请编号。必须在 start() 之前：流程变量 applyNo 会取这个值。
        // 单号依赖自增主键，因此要先 insert 再回写。
        order.setApplyNo(BizNoGenerator.applyNo(order.getApplyType(), order.getId()));
        applyOrderMapper.updateById(order);

        // 占位：抢占该资产，防止同一资产被重复申请。
        // 必须在 insert 之后（需要自增主键），且在启动流程之前。
        // 占用失败时抛异常，由 @Transactional 回滚上面这条 insert，不会留下孤儿单据。
        if (!assetPendingService.claim(order.getAssetId(), order.getId())) {
            throw new BusinessException("该资产已有进行中的申请，请等待处理完成后再试");
        }

        // ===================== Flowable 分支 =====================
        // 仅对已接入工作流的申请类型发起流程实例（当前为 BORROW，见 application.yml）
        if (applyWorkflowService.supports(order.getApplyType())) {
            String processInstanceId = applyWorkflowService.start(order);
            order.setProcessInstanceId(processInstanceId);
            applyOrderMapper.updateById(order);
            log.info("申请单 {} 已发起 Flowable 流程实例 {}", order.getApplyNo(), processInstanceId);
        }
    }

    @Override
    @Transactional
    public void approve(Long orderId, Long approveUserId, String approveUserName, Integer roleId,
                        Long approveUserDeptId, String level, String result, String comment) {
        ApplyOrder order = applyOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("申请单不存在");
        }

        // 分流：已发起流程实例的申请单交给 Flowable，其余保持原有状态机逻辑
        if (StringUtils.hasText(order.getProcessInstanceId())) {
            approveByWorkflow(order, approveUserId, approveUserName, roleId, approveUserDeptId, level, result, comment);
        } else {
            approveByStatusMachine(order, approveUserId, approveUserName, roleId, approveUserDeptId, level, result, comment);
        }
    }

    /* ====================================================================================
     *  路径一：Flowable 驱动（apply_order.process_instance_id 非空）
     * ==================================================================================== */

    private void approveByWorkflow(ApplyOrder order, Long approveUserId, String approveUserName, Integer roleId,
                                   Long approveUserDeptId, String level, String result, String comment) {
        // 1. 普通师生无权审批
        if (roleId != null && roleId == 3) {
            throw new BusinessException("权限不足：普通师生无权进行审批操作");
        }

        // 2. 不能审批自己提交的申请
        if (approveUserId.equals(order.getApplyUserId())) {
            throw new BusinessException("不能审批自己提交的申请");
        }

        // 3. 从引擎取出“当前待办任务” —— 现在该谁办、处于哪一级，全部以流程实例为准，
        //    不再依赖前端传入的 level，从根上避免前端状态过期导致的错审。
        Task task = applyWorkflowService.currentTask(order.getProcessInstanceId());
        if (task == null) {
            throw new BusinessException("当前流程已结束，无可办理的审批任务");
        }
        String currentLevel = applyWorkflowService.levelOf(task);
        if (currentLevel == null) {
            throw new BusinessException("流程当前节点不支持人工审批：" + task.getTaskDefinitionKey());
        }

        // 4. 前端传入的层级必须与流程当前节点一致（防止页面未刷新导致重复审批）
        if (level != null && !level.equals(currentLevel)) {
            throw new BusinessException("审批层级与流程当前节点不一致，请刷新页面后重试");
        }

        // 5. 角色与院系隔离规则（与改造前完全一致）
        if (roleId != null && roleId == 2) {
            if (ApplyWorkflowService.LEVEL_SECOND.equals(currentLevel)) {
                throw new BusinessException("权限不足：学院管理员仅可进行初审操作");
            }
            if (order.getDeptId() == null || !order.getDeptId().equals(approveUserDeptId)) {
                throw new BusinessException("权限不足：学院管理员仅可审批本学院的申请单");
            }
        } else if (roleId != null && roleId == 1 && !ApplyWorkflowService.LEVEL_SECOND.equals(currentLevel)) {
            throw new BusinessException("权限不足：超级管理员仅可进行复审操作");
        }

        // 6. 业务状态与流程节点必须一致（并发保护第一道闸）
        String expectedStatus = applyWorkflowService.statusOf(task);
        if (!expectedStatus.equals(order.getStatus())) {
            throw new BusinessException("申请单状态与流程节点不一致，请刷新页面后重试");
        }

        boolean approved = "PASS".equals(result);

        // 7. 写审批记录（前端详情页的“审批记录”表格依赖该表）
        ApprovalRecord record = new ApprovalRecord();
        record.setApplyId(order.getId());
        record.setApplyType(order.getApplyType());
        record.setApproveLevel(currentLevel);
        record.setApproveUserId(approveUserId);
        record.setApproveUserName(approveUserName);
        record.setResult(result);
        record.setComment(comment);
        record.setApproveTime(LocalDateTime.now());
        approvalRecordMapper.insert(record);

        // 8. 办理任务：写入 firstApproved/secondApproved 流程变量，由排他网关决定下一步
        applyWorkflowService.complete(task, order, approved, approveUserId, approveUserName, comment);

        // 9. 依据流程实例的真实状态回写 apply_order.status，保持前端既有的状态常量契约不变
        Task nextTask = applyWorkflowService.currentTask(order.getProcessInstanceId());
        String newStatus;
        if (nextTask != null) {
            newStatus = applyWorkflowService.statusOf(nextTask);
            if (newStatus == null) {
                throw new BusinessException("流程进入未知节点：" + nextTask.getTaskDefinitionKey());
            }
        } else {
            // 流程已结束：通过 → COMPLETED；驳回 → 按层级区分
            newStatus = approved
                    ? "COMPLETED"
                    : (ApplyWorkflowService.LEVEL_FIRST.equals(currentLevel) ? "FIRST_REJECT" : "SECOND_REJECT");
        }

        // 10. 原子更新（乐观锁，并发保护第二道闸）：
        //     若此处抛异常，整个事务回滚，第 8 步的 Flowable 任务办理也会一并回滚，
        //     因为 MyBatis 与 Flowable 共享同一个 DataSource 与事务管理器，不会被拆成两次提交。
        LambdaUpdateWrapper<ApplyOrder> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ApplyOrder::getId, order.getId())
                     .eq(ApplyOrder::getStatus, order.getStatus())   // 当前状态必须等于旧值
                     .set(ApplyOrder::getStatus, newStatus);
        int rows = applyOrderMapper.update(null, updateWrapper);
        if (rows == 0) {
            throw new BusinessException("审批失败：订单状态已被他人修改，请刷新页面后重试");
        }

        // 11. 流程已结束（无论通过还是驳回）→ 释放资产占位。
        //     必须在 CAS 成功之后：CAS 失败会抛异常回滚，占位应保留给重新审批。
        //     注意这里用 nextTask == null 判断，而不是枚举终态 ——
        //     流程实例结束即代表单据不会再流转，语义上等价且不会漏掉新增的终结节点。
        if (nextTask == null) {
            assetPendingService.release(order.getAssetId(), order.getId());
        }

        // 12. 复审通过且流程正常结束 → 更新资产状态
        if (approved && nextTask == null && ApplyWorkflowService.LEVEL_SECOND.equals(currentLevel)) {
            updateAssetOnFinalApproval(order);
        }
    }

    /* ====================================================================================
     *  路径二：原有硬编码状态机（apply_order.process_instance_id 为空）
     *  保留作为兜底：既有存量数据、以及尚未接入 Flowable 的申请类型仍按原逻辑审批。
     * ==================================================================================== */

    private void approveByStatusMachine(ApplyOrder order, Long approveUserId, String approveUserName, Integer roleId,
                                        Long approveUserDeptId, String level, String result, String comment) {
        Long orderId = order.getId();
        String currentStatus = order.getStatus();

        // 1. 普通师生无权审批
        if (roleId == 3) {
            throw new BusinessException("权限不足：普通师生无权进行审批操作");
        }

        // 2. 不能审批自己提交的申请
        if (approveUserId.equals(order.getApplyUserId())) {
            throw new BusinessException("不能审批自己提交的申请");
        }

        // 3. 院系隔离：学院管理员只能审批本学院（部门）的申请单
        if (roleId == 2) {
            if (order.getDeptId() == null || !order.getDeptId().equals(approveUserDeptId)) {
                throw new BusinessException("权限不足：学院管理员仅可审批本学院的申请单");
            }
        }

        // 4. 学院管理员只能做初审，超级管理员做复审
        if (roleId == 2 && !"FIRST".equals(level)) {
            throw new BusinessException("权限不足：学院管理员仅可进行初审操作");
        }
        if (roleId == 1 && !"SECOND".equals(level)) {
            throw new BusinessException("权限不足：超级管理员仅可进行复审操作");
        }

        // 5. 校验审批层级与订单当前状态匹配
        if ("FIRST".equals(level) && !"PENDING_FIRST".equals(currentStatus)) {
            throw new BusinessException("当前订单状态不允许初审");
        }
        if ("SECOND".equals(level) && !"PENDING_SECOND".equals(currentStatus)) {
            throw new BusinessException("当前订单状态不允许复审");
        }

        // 保存审批记录
        ApprovalRecord record = new ApprovalRecord();
        record.setApplyId(orderId);
        record.setApplyType(order.getApplyType());
        record.setApproveLevel(level);
        record.setApproveUserId(approveUserId);
        record.setApproveUserName(approveUserName);
        record.setResult(result);
        record.setComment(comment);
        record.setApproveTime(LocalDateTime.now());
        approvalRecordMapper.insert(record);

        // 原子更新：仅当状态未变更时才写入，防止并发重复审批
        String newStatus;
        if ("FIRST".equals(level)) {
            newStatus = "PASS".equals(result) ? "PENDING_SECOND" : "FIRST_REJECT";
        } else {
            newStatus = "PASS".equals(result) ? "COMPLETED" : "SECOND_REJECT";
        }

        //乐观锁
        LambdaUpdateWrapper<ApplyOrder> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ApplyOrder::getId, orderId)
                     .eq(ApplyOrder::getStatus, currentStatus) //当前状态必须等于旧值
                     .set(ApplyOrder::getStatus, newStatus);
        int rows = applyOrderMapper.update(null, updateWrapper);
        if (rows == 0) {
            throw new BusinessException("审批失败：订单状态已被他人修改，请刷新页面后重试");
        }

        // 终态 → 释放资产占位（CAS 成功之后，避免影响失败重试）
        if (isTerminalStatus(newStatus)) {
            assetPendingService.release(order.getAssetId(), order.getId());
        }

        // 复审通过时更新资产状态（也使用原子条件更新）
        if ("SECOND".equals(level) && "PASS".equals(result)) {
            updateAssetOnFinalApproval(order);
        }
    }

    /**
     * 是否为终态：单据不再流转，资产占位应当释放。
     * <p>仅用于硬编码状态机分支；Flowable 分支以「流程实例是否结束」判断，无需枚举状态。</p>
     */
    private static boolean isTerminalStatus(String status) {
        return "COMPLETED".equals(status)
                || "FIRST_REJECT".equals(status)
                || "SECOND_REJECT".equals(status);
    }

    /**
     * 审批最终通过后同步资产状态（领用 → 在用并登记使用人；归还 → 闲置并清空使用人）。
     * <p>按申请类型分支，因此「归还」后续接入 Flowable 时无需修改本方法。</p>
     */
    private void updateAssetOnFinalApproval(ApplyOrder order) {
        LambdaUpdateWrapper<Asset> assetUpdate = new LambdaUpdateWrapper<>();
        assetUpdate.eq(Asset::getId, order.getAssetId());
        final String expectedStatus;
        if ("BORROW".equals(order.getApplyType())) {
            expectedStatus = "闲置";
            assetUpdate.eq(Asset::getStatus, expectedStatus)
                       .set(Asset::getStatus, "在用")
                       .set(Asset::getUserId, order.getApplyUserId())
                       .set(Asset::getUserName, order.getApplyUserName());
        } else if ("RETURN".equals(order.getApplyType())) {
            expectedStatus = "在用";
            assetUpdate.eq(Asset::getStatus, expectedStatus);
            if(order.getApplyUserId() != null) {
                assetUpdate.eq(Asset::getUserId, order.getApplyUserId());
            } else {
                log.warn("归还终审：申请单 {} 缺少 applyUserId，仅按状态校验资产", order.getApplyNo());
            }
            assetUpdate.set(Asset::getStatus, "闲置")
                       .set(Asset::getUserId, null)
                       .set(Asset::getUserName, null);
        } else {
            return;
        }
        int row = assetMapper.update(null, assetUpdate);
        if (row == 0) {
            Asset latest = assetMapper.selectById(order.getAssetId());
            if(latest == null) {
                throw new BusinessException("资产不存在或已被删除，无法完成审批");
            }
            if(!expectedStatus.equals(latest.getStatus())) {
                throw new BusinessException(String.format(
                        "资产【%s】当前状态为【%s】，与本次审批预期【%s】不一致，请核实后重新审批",
                        order.getAssetCode(), latest.getStatus(), expectedStatus));
            }
            throw new BusinessException(String.format(
                    "资产【%s】当前使用人为【%s】，与申请人【%s】不一致，无法完成归还审批",
                    order.getAssetCode(), latest.getUserName(), order.getApplyUserName()));
        }
    }

    /* ====================================================================================
     *  查询（与改造前完全一致，前端无需改动）
     * ==================================================================================== */

    @Override
    public IPage<ApplyOrder> pageOrders(int current, int size, String applyType, String status, Long deptId,
                                         Long applyUserId, Long currentUserId, Long currentUserDeptId, Integer currentUserRoleId) {
        LambdaQueryWrapper<ApplyOrder> wrapper = new LambdaQueryWrapper<>();
        if (applyType != null) {
            wrapper.eq(ApplyOrder::getApplyType, applyType);
        }
        if (status != null) {
            wrapper.eq(ApplyOrder::getStatus, status);
        }
        // 「按申请人筛选」只对管理员开放；普通师生传入的 applyUserId 一律忽略，
        // 否则只要换个 id 就能查看他人的申请单（对象级越权）。
        if (applyUserId != null && (currentUserRoleId == null || currentUserRoleId != 3)) {
            wrapper.eq(ApplyOrder::getApplyUserId, applyUserId);
        }

        // 数据范围（以登录态为准，不采纳前端参数）：
        //   1 超级管理员 → 全部，可选按院系筛选
        //   2 学院管理员 → 强制本院系
        //   3 普通师生   → 强制仅本人
        if (currentUserRoleId != null && currentUserRoleId == 3) {
            wrapper.eq(ApplyOrder::getApplyUserId, currentUserId);
        } else if (currentUserRoleId != null && currentUserRoleId == 2) {
            wrapper.eq(ApplyOrder::getDeptId, currentUserDeptId);
        } else if (currentUserRoleId != null && currentUserRoleId == 1 && deptId != null) {
            wrapper.eq(ApplyOrder::getDeptId, deptId);
        }

        wrapper.orderByDesc(ApplyOrder::getCreateTime);
        return applyOrderMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @Override
    public Map<String, Object> getOrderDetail(Long id, Long currentUserId, Long currentUserDeptId, Integer currentUserRoleId) {
        ApplyOrder order = applyOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("申请单不存在");
        }

        // 数据范围校验（与列表口径保持一致）
        if (currentUserRoleId != null && currentUserRoleId == 3) {
            // 普通师生：只能看自己的单
            if (!java.util.Objects.equals(order.getApplyUserId(), currentUserId)) {
                throw new BusinessException("无权查看他人的申请单");
            }
        } else if (currentUserRoleId != null && currentUserRoleId == 2) {
            // 学院管理员：只能看本学院的单
            if (order.getDeptId() == null || !order.getDeptId().equals(currentUserDeptId)) {
                throw new BusinessException("权限不足：学院管理员仅可查看本学院的申请单");
            }
        }

        LambdaQueryWrapper<ApprovalRecord> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.eq(ApprovalRecord::getApplyId, id)
                     .orderByAsc(ApprovalRecord::getApproveLevel);
        java.util.List<ApprovalRecord> records = approvalRecordMapper.selectList(recordWrapper);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("order", order);
        result.put("records", records);
        return result;
    }
}
