package com.example.asset.modules.scrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.asset.modules.scrap.entity.ScrapOrder;

import java.util.Map;

public interface ScrapService {

    void submitScrap(ScrapOrder order);

    void approve(Long orderId, Long approveUserId, String approveUserName, Integer roleId, Long approveUserDeptId, String level, String result, String comment);

    IPage<ScrapOrder> pageOrders(int current, int size, String status, Long deptId, Long currentUserDeptId, Integer currentUserRoleId);

    /** 查询报废单详情（含审批记录，自动根据角色进行院系隔离） */
    Map<String, Object> getScrapDetail(Long id, Long currentUserDeptId, Integer currentUserRoleId);
}
