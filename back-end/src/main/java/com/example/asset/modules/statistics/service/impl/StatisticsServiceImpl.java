package com.example.asset.modules.statistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.asset.modules.asset.entity.Asset;
import com.example.asset.modules.asset.mapper.AssetMapper;
import com.example.asset.modules.check.entity.CheckRecord;
import com.example.asset.modules.check.mapper.CheckRecordMapper;
import com.example.asset.modules.statistics.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final AssetMapper assetMapper;
    private final CheckRecordMapper checkRecordMapper;

    @Override
    public Map<String, Object> deptAssetStats() {
        List<Asset> assets = assetMapper.selectList(null);
        // 按部门分组统计
        Map<String, Long> deptCount = assets.stream()
                .collect(Collectors.groupingBy(Asset::getDeptName, Collectors.counting()));

        Map<String, Object> result = new HashMap<>();
        result.put("deptNames", new ArrayList<>(deptCount.keySet()));
        result.put("counts", new ArrayList<>(deptCount.values()));
        result.put("total", assets.size());
        return result;
    }

    @Override
    public Map<String, Object> categoryStats() {
        List<Asset> assets = assetMapper.selectList(null);
        Map<String, Long> categoryCount = assets.stream()
                .collect(Collectors.groupingBy(Asset::getCategoryName, Collectors.counting()));

        Map<String, Object> result = new HashMap<>();
        result.put("categories", new ArrayList<>(categoryCount.keySet()));
        result.put("counts", new ArrayList<>(categoryCount.values()));
        return result;
    }

    @Override
    public Map<String, Object> statusStats() {
        List<Asset> assets = assetMapper.selectList(null);
        Map<String, Long> statusCount = assets.stream()
                .collect(Collectors.groupingBy(Asset::getStatus, Collectors.counting()));

        // 计算总金额（原值 × 数量）
        java.math.BigDecimal totalValue = assets.stream()
                .map(a -> a.getOriginalValue() != null && a.getQuantity() != null
                        ? a.getOriginalValue().multiply(java.math.BigDecimal.valueOf(a.getQuantity()))
                        : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("statuses", new ArrayList<>(statusCount.keySet()));
        result.put("counts", new ArrayList<>(statusCount.values()));
        result.put("totalValue", totalValue);
        return result;
    }

    @Override
    public Map<String, Object> checkDiffStats(Long taskId) {
        LambdaQueryWrapper<CheckRecord> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(CheckRecord::getTaskId, taskId);
        }
        List<CheckRecord> records = checkRecordMapper.selectList(wrapper);

        long matchCount = records.stream().filter(r -> "MATCH".equals(r.getResult())).count();
        long surplusCount = records.stream().filter(r -> "SURPLUS".equals(r.getResult())).count();
        long shortageCount = records.stream().filter(r -> "SHORTAGE".equals(r.getResult())).count();

        Map<String, Object> result = new HashMap<>();
        result.put("total", records.size());
        result.put("match", matchCount);
        result.put("surplus", surplusCount);
        result.put("shortage", shortageCount);
        result.put("details", records);
        return result;
    }
}
