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

    <!-- 学院维度：独占一整行，长学院名才有足够横向空间 -->
    <div class="chart-row chart-row--full">
      <div class="chart-card">
        <h3>各学院资产数量与金额</h3>
        <v-chart :option="deptChartOption" style="height: 400px" autoresize />
      </div>
    </div>

    <!-- 其余分布图：并排一行 -->
    <div class="chart-row">
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

const deptData = ref<any>({ deptNames: [], counts: [], amounts: [] })
const categoryData = ref<any>({ categories: [], counts: [] })
const statusData = ref<any>({ statuses: [], counts: [] })
const diffData = ref<any>({ total: 0, match: 0, surplus: 0, shortage: 0 })

const deptChartOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  legend: {
    data: ['资产数量', '资产金额（元）'],
    top: 0,
    left: 'center',
    itemWidth: 10,
    itemHeight: 10,
    itemGap: 18,
    textStyle: { color: '#656d76', fontSize: 12 }
  },
  // top 留出图例的高度；不再给 Y 轴设 name —— 否则轴名会被图例压住
  // left 加大：斜排标签的左端会越过第一个刻度向左延伸，留白不够会被裁掉
  grid: { left: 32, right: 16, top: 44, bottom: 4, containLabel: true },
  xAxis: {
    type: 'category',
    data: deptData.value.deptNames || [],
    axisTick: { alignWithLabel: true },
    axisLine: { lineStyle: { color: '#d0d7de' } },
    axisLabel: {
      // interval: 0 —— 强制每个学院名都显示；默认 ECharts 会自动隐藏拥挤的标签，
      // 这才是之前"学院名称显示不全"的直接原因
      interval: 0,
      rotate: 35,          // 斜排，长学院名不再互相挤压
      fontSize: 11,
      color: '#656d76',
      margin: 12
    }
  },
  yAxis: [
    {
      type: 'value',
      axisLabel: { color: '#8c959f', fontSize: 11 },
      splitLine: { lineStyle: { color: '#eaeef2' } }
    },
    {
      type: 'value',
      axisLabel: {
        color: '#8c959f',
        fontSize: 11,
        formatter: (v: number) => (v >= 10000 ? (v / 10000).toFixed(1) + '万' : String(v))
      },
      splitLine: { show: false }
    }
  ],
  series: [
    {
      name: '资产数量',
      type: 'bar',
      data: deptData.value.counts || [],
      barMaxWidth: 26,
      itemStyle: { color: '#2f81f7', borderRadius: [4, 4, 0, 0] }
    },
    {
      name: '资产金额（元）',
      type: 'bar',
      yAxisIndex: 1,
      data: deptData.value.amounts || [],
      barMaxWidth: 26,
      itemStyle: { color: '#8250df', borderRadius: [4, 4, 0, 0] }
    }
  ]
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
