<template>
  <div class="page-container">
    <h3 style="margin-bottom: 20px;">数据统计与报表</h3>

    <!-- 统计卡片 -->
    <div class="stat-card-row">
      <el-card shadow="hover">
        <el-statistic title="资产总数" :value="overview.total" />
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="在用资产" :value="overview.inUse" />
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="闲置资产" :value="overview.idle" />
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="维修中" :value="overview.repairing" />
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="资产总金额(元)" :value="overview.totalValue" />
      </el-card>
    </div>

    <!-- 图表区 -->
    <div class="chart-row">
      <div class="chart-card">
        <h3>各学院资产数量统计</h3>
        <v-chart :option="deptChartOption" style="height: 350px" autoresize />
      </div>
      <div class="chart-card">
        <h3>资产类型分布</h3>
        <v-chart :option="categoryChartOption" style="height: 350px" autoresize />
      </div>
      <div class="chart-card">
        <h3>资产状态分布</h3>
        <v-chart :option="statusChartOption" style="height: 350px" autoresize />
      </div>
      <div class="chart-card">
        <h3>盘点差异统计</h3>
        <v-chart :option="diffChartOption" style="height: 350px" autoresize />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { getDeptAssetStats, getCategoryStats, getStatusStats, getCheckDiffStats } from '@/api/statistics'

use([CanvasRenderer, BarChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const overview = reactive({ total: 0, inUse: 0, idle: 0, repairing: 0, totalValue: 0 })

const deptData = ref<any>({ deptNames: [], counts: [] })
const categoryData = ref<any>({ categories: [], counts: [] })
const statusData = ref<any>({ statuses: [], counts: [] })
const diffData = ref<any>({ total: 0, match: 0, surplus: 0, shortage: 0 })

const deptChartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: deptData.value.deptNames || [] },
  yAxis: { type: 'value' },
  series: [{ data: deptData.value.counts || [], type: 'bar', itemStyle: { color: '#409EFF' } }]
}))

const categoryChartOption = computed(() => ({
  tooltip: { trigger: 'item' },
  series: [{
    type: 'pie', radius: ['40%', '70%'],
    data: (categoryData.value.categories || []).map((c: string, i: number) => ({
      name: c, value: categoryData.value.counts[i]
    }))
  }]
}))

const statusChartOption = computed(() => ({
  tooltip: { trigger: 'item' },
  series: [{
    type: 'pie', radius: '60%',
    data: (statusData.value.statuses || []).map((s: string, i: number) => ({
      name: s, value: statusData.value.counts[i]
    }))
  }]
}))

const diffChartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: ['相符', '盘盈', '盘亏'] },
  yAxis: { type: 'value' },
  series: [{
    data: [diffData.value.match || 0, diffData.value.surplus || 0, diffData.value.shortage || 0],
    type: 'bar',
    itemStyle: { color: (params: any) => ['#67c23a', '#e6a23c', '#f56c6c'][params.dataIndex] }
  }]
}))

onMounted(async () => {
  try {
    const [dept, cat, status, diff] = await Promise.all([
      getDeptAssetStats(), getCategoryStats(), getStatusStats(), getCheckDiffStats()
    ])
    deptData.value = dept.data
    categoryData.value = cat.data
    statusData.value = status.data
    diffData.value = diff.data

    // 计算概览数据
    const statusArr = status.data.statuses || []
    const counts = status.data.counts || []
    overview.total = counts.reduce((a: number, b: number) => a + b, 0)
    overview.inUse = counts[statusArr.indexOf('在用')] || 0
    overview.idle = counts[statusArr.indexOf('闲置')] || 0
    overview.repairing = counts[statusArr.indexOf('维修中')] || 0
    overview.totalValue = status.data.totalValue || 0
  } catch (e) {
    console.error('数据统计加载失败:', e)
  }
})
</script>
