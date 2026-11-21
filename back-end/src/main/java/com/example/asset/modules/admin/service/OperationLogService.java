package com.example.asset.modules.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.asset.modules.admin.entity.OperationLog;

import java.time.LocalDateTime;

/**
 * 操作审计日志服务接口
 */
public interface OperationLogService {

    /**
     * 分页查询操作日志（支持多条件筛选）
     *
     * @param current    当前页
     * @param size       每页条数
     * @param module     模块筛选（可选）
     * @param operation  操作类型筛选（可选）
     * @param operatorName 操作人姓名模糊搜索（可选）
     * @param status     操作状态筛选（可选：1=成功 0=失败）
     * @param startTime  操作时间起始（可选）
     * @param endTime    操作时间截止（可选）
     * @return 分页结果
     */
    IPage<OperationLog> pageLogs(int current, int size,
                                  String module, String operation,
                                  String operatorName, Integer status,
                                  LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询单条日志详情
     */
    OperationLog getLogDetail(Long id);
}
