package com.example.asset.modules.scrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.asset.modules.scrap.entity.ScrapOrder;

import java.util.Map;

public interface ScrapService {

    void submitScrap(ScrapOrder order);

    void approve(Long orderId, Long approveUserId, String approveUserName, Integer roleId, Long approveUserDeptId, String level, String result, String comment);

    /** 分页查询报废单（数据范围按角色推导：1=全部、2=本院系、3=仅本人） */
    IPage<ScrapOrder> pageOrders(int current, int size, String status, Long deptId,
                                 Long currentUserId, Long currentUserDeptId, Integer currentUserRoleId);

    /** 查询报废单详情（含审批记录，按角色做数据范围校验） */
    Map<String, Object> getScrapDetail(Long id, Long currentUserId, Long currentUserDeptId, Integer currentUserRoleId);
}
