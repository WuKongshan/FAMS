package com.example.asset.modules.approval.flow;

import com.example.asset.modules.approval.entity.ApplyOrder;

/**
 * 流程引擎与业务单据之间的最小契约。
 *
 * <p>引入它的目的：让 {@link ApplyWorkflowService} 不再依赖某个具体的单据实体，
 * 从而可以同时服务领用/归还（{@code apply_order}）与报废（{@code scrap_order}）。</p>
 *
 * <p>只放「流程需要的信息」，不放业务字段；业务校验依然留在各自的 Service 层。</p>
 *
 * <p>注意：报废单的工厂方法故意不放在这里。本包位于 {@code modules.approval} 下，
 * 反向依赖 {@code modules.scrap} 会造成模块环依赖，因此报废单的上下文由
 * {@code ScrapServiceImpl} 自行拼装。</p>
 */
public record WorkflowBizContext(
        Long bizId,            // 业务单ID，作为 Flowable 的 businessKey
        String bizNo,          // 单号，仅作流程变量留痕
        String bizType,        // 业务类型：BORROW / RETURN / SCRAP
        Long assetId,
        String assetCode,
        String assetName,
        Long deptId,           // 申请人所属部门，用于初审候选人的院系隔离
        String deptName,
        Long applyUserId,
        String applyUserName) {

    /** 领用/归还申请 → 流程上下文 */
    public static WorkflowBizContext from(ApplyOrder order) {
        return new WorkflowBizContext(
                order.getId(),
                order.getApplyNo(),
                order.getApplyType(),
                order.getAssetId(),
                order.getAssetCode(),
                order.getAssetName(),
                order.getDeptId(),
                order.getDeptName(),
                order.getApplyUserId(),
                order.getApplyUserName());
    }
}
