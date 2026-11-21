package com.example.asset.modules.repair.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.asset.common.exception.BusinessException;
import com.example.asset.modules.asset.entity.Asset;
import com.example.asset.modules.asset.mapper.AssetMapper;
import com.example.asset.modules.repair.entity.RepairOrder;
import com.example.asset.modules.repair.mapper.RepairOrderMapper;
import com.example.asset.modules.repair.service.RepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepairServiceImpl implements RepairService {

    private final RepairOrderMapper repairOrderMapper;
    private final AssetMapper assetMapper;

    @Override
    public void submitOrder(RepairOrder order) {
        // 校验资产状态：已报废或维修中的资产不允许报修
        Asset asset = assetMapper.selectById(order.getAssetId());
        if (asset == null) {
            throw new BusinessException("资产不存在");
        }
        if ("已报废".equals(asset.getStatus())) {
            throw new BusinessException("该资产已报废，无法报修");
        }
        if ("维修中".equals(asset.getStatus())) {
            throw new BusinessException("该资产正在维修中，请勿重复报修");
        }

        order.setStatus("PENDING_FIRST");
        repairOrderMapper.insert(order);
    }

    @Override
    @Transactional
    public void updateOrder(RepairOrder order, Long currentUserId, Long currentUserDeptId, Integer currentUserRoleId) {
        RepairOrder existing = repairOrderMapper.selectById(order.getId());
        if (existing == null) {
            throw new BusinessException("工单不存在");
        }

        // 院系隔离与权限校验
        // 1. 普通师生无权进行工单操作（审批/维修完成）
        if (currentUserRoleId != null && currentUserRoleId == 3) {
            throw new BusinessException("权限不足：普通师生无权进行工单操作");
        }

        // 2. 学院管理员只能操作本学院的报修工单
        if (currentUserRoleId != null && currentUserRoleId == 2) {
            if (existing.getDeptId() == null || !existing.getDeptId().equals(currentUserDeptId)) {
                throw new BusinessException("权限不足：学院管理员仅可操作本学院的报修工单");
            }
        }

        // 3. 不能操作自己提交的报修单
        if (currentUserId.equals(existing.getReporterId())) {
            throw new BusinessException("不能操作自己提交的报修单");
        }

        String newStatus = order.getStatus();
        if (newStatus != null) {
            String originalStatus = existing.getStatus();

            // 初审通过 → 开始维修（审批人即为维修师傅）
            if ("IN_PROGRESS".equals(newStatus) && "PENDING_FIRST".equals(existing.getStatus())) {
                existing.setRepairerId(order.getRepairerId());
                existing.setRepairerName(order.getRepairerName());
                existing.setAcceptTime(LocalDateTime.now());
                // 同步更新资产状态为"维修中"
                Asset asset = assetMapper.selectById(existing.getAssetId());
                if (asset != null) {
                    log.info("报修工单[{}]初审通过, 资产[{}]状态由[{}]更新为维修中",
                            existing.getOrderNo(), asset.getAssetCode(), asset.getStatus());
                    asset.setStatus("维修中");
                    assetMapper.updateById(asset);
                } else {
                    log.warn("报修工单[{}]初审通过, 但资产ID[{}]未找到",
                            existing.getOrderNo(), existing.getAssetId());
                }
            }
            // 初审驳回
            if ("FIRST_REJECT".equals(newStatus) && "PENDING_FIRST".equals(existing.getStatus())) {
                existing.setRepairerId(order.getRepairerId());
                existing.setRepairerName(order.getRepairerName());
                existing.setAcceptTime(LocalDateTime.now());
            }
            // 维修完成
            if ("COMPLETED".equals(newStatus) && "IN_PROGRESS".equals(existing.getStatus())) {
                existing.setRepairResult(order.getRepairResult());
                if (order.getRepairCost() != null) {
                    existing.setRepairCost(order.getRepairCost());
                }
                existing.setCompleteTime(LocalDateTime.now());
                // 更新资产状态回"在用"
                Asset asset = assetMapper.selectById(existing.getAssetId());
                if (asset != null && "维修中".equals(asset.getStatus())) {
                    log.info("报修工单[{}]维修完成, 资产[{}]状态由维修中恢复为在用",
                            existing.getOrderNo(), asset.getAssetCode());
                    asset.setStatus("在用");
                    assetMapper.updateById(asset);
                } else if (asset != null) {
                    log.warn("报修工单[{}]维修完成, 但资产[{}]状态为[{}]非维修中, 跳过恢复",
                            existing.getOrderNo(), asset.getAssetCode(), asset.getStatus());
                }
            }
            existing.setStatus(newStatus);

            // 乐观锁更新：仅当状态未被他人修改时才写入
            LambdaUpdateWrapper<RepairOrder> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(RepairOrder::getId, existing.getId())
                         .eq(RepairOrder::getStatus, originalStatus);

            if (existing.getRepairerId() != null)
                updateWrapper.set(RepairOrder::getRepairerId, existing.getRepairerId());
            if (existing.getRepairerName() != null)
                updateWrapper.set(RepairOrder::getRepairerName, existing.getRepairerName());
            if (existing.getAcceptTime() != null)
                updateWrapper.set(RepairOrder::getAcceptTime, existing.getAcceptTime());
            if (existing.getRepairResult() != null)
                updateWrapper.set(RepairOrder::getRepairResult, existing.getRepairResult());
            if (existing.getRepairCost() != null)
                updateWrapper.set(RepairOrder::getRepairCost, existing.getRepairCost());
            if (existing.getCompleteTime() != null)
                updateWrapper.set(RepairOrder::getCompleteTime, existing.getCompleteTime());

            updateWrapper.set(RepairOrder::getStatus, newStatus);

            int rows = repairOrderMapper.update(null, updateWrapper);
            if (rows == 0) {
                throw new BusinessException("工单状态已被他人修改，请刷新页面后重试");
            }
        }
    }

    @Override
    public RepairOrder getOrderDetail(Long id, Long currentUserDeptId, Integer currentUserRoleId) {
        RepairOrder order = repairOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }

        // 院系隔离：学院管理员只能查看本学院的报修工单详情
        if (currentUserRoleId != null && currentUserRoleId == 2) {
            if (order.getDeptId() == null || !order.getDeptId().equals(currentUserDeptId)) {
                throw new BusinessException("权限不足：学院管理员仅可查看本学院的报修工单");
            }
        }
        return order;
    }

    @Override
    public IPage<RepairOrder> pageOrders(int current, int size, String status, Long deptId,
                                         Long currentUserDeptId, Integer currentUserRoleId) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(RepairOrder::getStatus, status);
        }

        // 院系隔离：学院管理员只能查看本学院的报修单，超级管理员可查看全部
        if (currentUserRoleId != null && currentUserRoleId == 2) {
            // 学院管理员：强制按自己的院系过滤，忽略前端传入的 deptId
            wrapper.eq(RepairOrder::getDeptId, currentUserDeptId);
        } else if (currentUserRoleId != null && currentUserRoleId == 1 && deptId != null) {
            // 超级管理员：可选按院系过滤
            wrapper.eq(RepairOrder::getDeptId, deptId);
        }

        wrapper.orderByDesc(RepairOrder::getCreateTime);
        return repairOrderMapper.selectPage(new Page<>(current, size), wrapper);
    }
}
