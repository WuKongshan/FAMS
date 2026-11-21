<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="queryForm" size="default">
        <el-form-item label="操作模块">
          <el-select v-model="queryForm.module" placeholder="全部模块" clearable style="width: 140px">
            <el-option label="用户管理" value="用户管理" />
            <el-option label="资产管理" value="资产管理" />
            <el-option label="审批管理" value="审批管理" />
            <el-option label="协同盘点" value="协同盘点" />
            <el-option label="报修管理" value="报修管理" />
            <el-option label="报废管理" value="报废管理" />
            <el-option label="认证管理" value="认证管理" />
            <el-option label="数据统计" value="数据统计" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="queryForm.operation" placeholder="全部类型" clearable style="width: 120px">
            <el-option label="新增" value="新增" />
            <el-option label="修改" value="修改" />
            <el-option label="删除" value="删除" />
            <el-option label="查询" value="查询" />
            <el-option label="审批操作" value="审批操作" />
            <el-option label="提交申请" value="提交申请" />
            <el-option label="用户登录" value="用户登录" />
            <el-option label="导出" value="导出" />
            <el-option label="发布任务" value="发布任务" />
            <el-option label="录入数据" value="录入数据" />
            <el-option label="确认差异" value="确认差异" />
            <el-option label="智能分析" value="智能分析" />
            <el-option label="派单" value="派单" />
            <el-option label="更新进度" value="更新进度" />
            <el-option label="重置密码" value="重置密码" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="queryForm.operatorName" placeholder="输入操作人姓名" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="操作状态">
          <el-select v-model="queryForm.status" placeholder="全部状态" clearable style="width: 100px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" :icon="Search">查询</el-button>
          <el-button @click="handleReset" :icon="RefreshLeft">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 审计特点说明 -->
    <el-alert
      title="操作审计：所有关键操作均已实名留痕，记录操作人员、操作时间、IP地址、请求参数与执行结果，满足协同工作审计与追溯要求。"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <!-- 数据表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="module" label="操作模块" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.module }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="operation" label="操作类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="getOperationTagType(row.operation)">{{ row.operation }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="操作描述" min-width="160" show-overflow-tooltip />
      <el-table-column prop="operatorName" label="操作人" width="100" align="center" />
      <el-table-column prop="operatorIp" label="操作IP" width="140" align="center" />
      <el-table-column prop="operationTime" label="操作时间" width="170" align="center" sortable />
      <el-table-column prop="costTime" label="耗时(ms)" width="90" align="center">
        <template #default="{ row }">
          <span :style="{ color: row.costTime > 1000 ? '#F56C6C' : '#67C23A' }">
            {{ row.costTime }}ms
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" link @click="showDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="queryForm.current"
        v-model:page-size="queryForm.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="handleSearch"
        background
      />
    </div>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="操作日志详情" width="700px" destroy-on-close>
      <el-descriptions v-if="currentLog" :column="2" border size="default">
        <el-descriptions-item label="日志ID">{{ currentLog.id }}</el-descriptions-item>
        <el-descriptions-item label="操作模块">
          <el-tag size="small" type="info">{{ currentLog.module }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作类型">
          <el-tag size="small" :type="getOperationTagType(currentLog.operation)">{{ currentLog.operation }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作状态">
          <el-tag :type="currentLog.status === 1 ? 'success' : 'danger'" size="small">
            {{ currentLog.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作描述" :span="2">{{ currentLog.description }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ currentLog.operatorName }}（ID: {{ currentLog.operatorId }}）</el-descriptions-item>
        <el-descriptions-item label="操作IP">{{ currentLog.operatorIp }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ currentLog.operationTime }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentLog.costTime }}ms</el-descriptions-item>
        <el-descriptions-item label="请求方法">
          <el-tag size="small">{{ currentLog.requestMethod }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="请求URL" :span="1">{{ currentLog.requestUrl }}</el-descriptions-item>
        <el-descriptions-item v-if="currentLog.requestParams" label="请求参数" :span="2">
          <div class="json-block">{{ formatJson(currentLog.requestParams) }}</div>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog.responseResult" label="响应结果" :span="2">
          <div class="json-block">{{ formatJson(currentLog.responseResult) }}</div>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog.errorMsg" label="错误信息" :span="2">
          <span style="color: #F56C6C">{{ currentLog.errorMsg }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { pageAuditLogs, type AuditLog } from '@/api/audit'

const loading = ref(false)
const tableData = ref<AuditLog[]>([])
const total = ref(0)
const timeRange = ref<[string, string] | null>(null)

const queryForm = reactive({
  current: 1,
  size: 10,
  module: '',
  operation: '',
  operatorName: '',
  status: undefined as number | undefined
})

// 详情弹窗
const detailVisible = ref(false)
const currentLog = ref<AuditLog | null>(null)

/** 操作类型 Tag 颜色映射 */
function getOperationTagType(operation: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, string> = {
    '新增': 'success',
    '修改': 'warning',
    '删除': 'danger',
    '审批操作': 'warning',
    '提交申请': '',
    '用户登录': 'info',
    '导出': 'info',
    '查询': 'info',
    '发布任务': 'success',
    '录入数据': '',
    '确认差异': 'warning',
    '智能分析': 'info',
    '重置密码': 'danger',
    '派单': 'warning',
    '更新进度': '',
    '用户注册': 'success'
  }
  return (map[operation] || 'info') as '' | 'success' | 'warning' | 'info' | 'danger'
}

/** 格式化 JSON 字符串 */
function formatJson(str: string): string {
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

/** 查询 */
async function handleSearch() {
  loading.value = true
  try {
    const params: any = {
      current: queryForm.current,
      size: queryForm.size,
      module: queryForm.module || undefined,
      operation: queryForm.operation || undefined,
      operatorName: queryForm.operatorName || undefined,
      status: queryForm.status
    }
    if (timeRange.value && timeRange.value.length === 2) {
      params.startTime = timeRange.value[0]
      params.endTime = timeRange.value[1]
    }
    const res = await pageAuditLogs(params)
    tableData.value = res.records
    total.value = res.total
  } catch {
    // 错误已在 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 重置筛选条件 */
function handleReset() {
  queryForm.current = 1
  queryForm.module = ''
  queryForm.operation = ''
  queryForm.operatorName = ''
  queryForm.status = undefined
  timeRange.value = null
  handleSearch()
}

/** 查看详情 */
function showDetail(log: AuditLog) {
  currentLog.value = log
  detailVisible.value = true
}

onMounted(() => {
  handleSearch()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
  min-height: calc(100vh - 120px);
}
.search-bar {
  background: #fff;
  padding: 16px 20px 0;
  border-radius: 8px;
  margin-bottom: 16px;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  background: #fff;
  padding: 12px 20px;
  border-radius: 8px;
}
.json-block {
  max-height: 200px;
  overflow-y: auto;
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-family: 'Courier New', Consolas, monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  width: 100%;
}
</style>
