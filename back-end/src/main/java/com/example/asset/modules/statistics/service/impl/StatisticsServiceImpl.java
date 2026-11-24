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

        // 按部门汇总「数量」与「金额」（金额 = 原值 × 数量，与 statusStats 口径一致）
        // 用 LinkedHashMap 保证 deptNames / counts / amounts 三个数组下标严格对齐
        Map<String, Long> deptCount = new LinkedHashMap<>();
        Map<String, java.math.BigDecimal> deptAmount = new LinkedHashMap<>();
        for (Asset asset : assets) {
            // deptName 可能为空（历史数据未回填）。原实现直接 groupingBy(Asset::getDeptName)，
            // 一旦出现 null 键会抛 NPE 导致整个统计接口 500，这里单独归为「未分配」。
            String dept = (asset.getDeptName() == null || asset.getDeptName().isEmpty())
                    ? "未分配" : asset.getDeptName();
            deptCount.merge(dept, 1L, Long::sum);

            java.math.BigDecimal amount = java.math.BigDecimal.ZERO;
            if (asset.getOriginalValue() != null) {
                int qty = asset.getQuantity() == null ? 1 : asset.getQuantity();
                amount = asset.getOriginalValue().multiply(java.math.BigDecimal.valueOf(qty));
            }
            deptAmount.merge(dept, amount, java.math.BigDecimal::add);
        }

        // 按数量降序，柱状图更易读
        List<String> names = new ArrayList<>(deptCount.keySet());
        names.sort((a, b) -> Long.compare(deptCount.get(b), deptCount.get(a)));

        List<Long> counts = new ArrayList<>(names.size());
        List<java.math.BigDecimal> amounts = new ArrayList<>(names.size());
        for (String name : names) {
            counts.add(deptCount.get(name));
            amounts.add(deptAmount.get(name));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("deptNames", names);
        result.put("counts", counts);
        result.put("amounts", amounts);
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
