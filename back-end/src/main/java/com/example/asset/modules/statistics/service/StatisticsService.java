package com.example.asset.modules.statistics.service;

import java.util.Map;

public interface StatisticsService {

    /** 各学院资产数量统计 */
    Map<String, Object> deptAssetStats();

    /** 资产类型分布统计 */
    Map<String, Object> categoryStats();

    /** 资产状态统计 */
    Map<String, Object> statusStats();

    /** 盘点差异统计 */
    Map<String, Object> checkDiffStats(Long taskId);
}
