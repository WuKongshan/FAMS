package com.example.asset.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.asset.modules.admin.entity.OperationLog;
import com.example.asset.modules.admin.mapper.OperationLogMapper;
import com.example.asset.modules.admin.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @Override
    public IPage<OperationLog> pageLogs(int current, int size,
                                         String module, String operation,
                                         String operatorName, Integer status,
                                         LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(module), OperationLog::getModule, module)
               .eq(StringUtils.hasText(operation), OperationLog::getOperation, operation)
               .like(StringUtils.hasText(operatorName), OperationLog::getOperatorName, operatorName)
               .eq(status != null, OperationLog::getStatus, status)
               .ge(startTime != null, OperationLog::getOperationTime, startTime)
               .le(endTime != null, OperationLog::getOperationTime, endTime)
               .orderByDesc(OperationLog::getOperationTime);

        return operationLogMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @Override
    public OperationLog getLogDetail(Long id) {
        return operationLogMapper.selectById(id);
    }
}
