<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-card-row">
      <el-card shadow="hover">
        <el-statistic title="任务总数" :value="stats.total" />
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="待开始" :value="stats.pending">
          <template #suffix><el-tag type="info" size="small">待开始</el-tag></template>
        </el-statistic>
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="进行中" :value="stats.inProgress">
          <template #suffix><el-tag type="warning" size="small">进行中</el-tag></template>
        </el-statistic>
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="已完成" :value="stats.completed">
          <template #suffix><el-tag type="success" size="small">完成</el-tag></template>
        </el-statistic>
      </el-card>
    </div>

    <div class="table-actions">
      <h3>盘点任务管理</h3>
      <div>
        <el-button type="danger" plain @click="openGlobalDiffs">
          全系统待处置差异{{ globalPendingCount > 0 ? `（${globalPendingCount}）` : '' }}
        </el-button>
        <el-button type="primary" @click="openCreateDialog">发布盘点任务</el-button>
      </div>
    </div>

    <!-- 全局待处置视图：管理视角，只看不处置（处置要到具体任务里做） -->
    <el-dialog title="全系统待处置差异" v-model="globalDiffVisible" width="880px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="这里汇总所有盘点任务中尚未处置的差异。处置操作请进入对应任务页进行，本页只做全局清点。"
      />
      <el-table :data="globalDiffs" border stripe max-height="420" style="margin-top: 12px">
        <el-table-column prop="taskId" label="盘点任务ID" width="110" />
        <el-table-column prop="assetCode" label="资产编号" width="130" />
        <el-table-column prop="assetName" label="资产名称" min-width="160" />
        <el-table-column label="差异" width="80">
          <template #default="{ row }">
            <span :style="{ color: row.diffQuantity < 0 ? '#f56c6c' : '#67c23a' }">
              {{ row.diffQuantity > 0 ? '+' : '' }}{{ row.diffQuantity }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SURPLUS' ? 'success' : 'danger'" size="small">
              {{ row.result === 'SURPLUS' ? '盘盈' : '盘亏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkerName" label="盘点人" width="110" />
        <template #empty><span>所有差异都已处置</span></template>
      </el-table>
      <template #footer>
        <el-button @click="globalDiffVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-table :data="tableData" border stripe v-loading="loading">
      <el-table-column prop="taskNo" label="任务编号" width="140" />
      <el-table-column prop="taskName" label="任务名称" min-width="180" />
      <el-table-column prop="deptNames" label="盘点范围" min-width="180" />
      <el-table-column prop="participantNames" label="参与人员" min-width="150" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'IN_PROGRESS' ? 'warning' : 'info'">
            {{ row.status === 'PENDING' ? '待开始' : row.status === 'IN_PROGRESS' ? '进行中' : '已完成' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button size="small" @click="showDetail(row)">详情</el-button>
          <el-button size="small" type="primary" @click="enterCheck(row)">进入盘点</el-button>
          <el-button size="small" @click="analyzeDiffHandler(row.id)">智能分析</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="search.current" v-model:page-size="search.size"
      :total="total" layout="total, prev, pager, next" style="margin-top: 16px; justify-content: flex-end"
      @change="fetchData"
    />

    <!-- 新建盘点任务弹窗 -->
    <el-dialog title="发布盘点任务" v-model="dialogVisible" width="550px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.taskName" />
        </el-form-item>
        <el-form-item label="盘点部门">
          <el-select v-model="form.deptIds" multiple style="width: 100%" placeholder="选择部门">
            <el-option v-for="d in deptOptions" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="盘点时间">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至"
            style="width: 100%" />
        </el-form-item>
        <el-form-item label="参与人员">
          <el-select v-model="form.participantIds" multiple style="width: 100%" placeholder="选择参与人员">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">发布</el-button>
      </template>
    </el-dialog>

    <!-- 智能分析结果弹窗 -->
    <el-dialog title="LLM 智能分析结果" v-model="analysisVisible" width="750px">
      <el-table :data="analysisResults" border stripe max-height="450" v-loading="analyzing">
        <el-table-column prop="assetName" label="资产名称" width="160" />
        <el-table-column label="差异" width="100">
          <template #default="{ row }">
            <span :style="{ color: row.diffQuantity < 0 ? '#f56c6c' : row.diffQuantity > 0 ? '#67c23a' : '' }">
              {{ row.diffQuantity > 0 ? '+' : '' }}{{ row.diffQuantity }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="analysis" label="分析" min-width="250" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="建议" min-width="200" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="analysisVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog title="盘点任务详情" v-model="detailVisible" width="600px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 20px">
        <el-descriptions-item label="任务编号">{{ detailTask.taskNo }}</el-descriptions-item>
        <el-descriptions-item label="任务名称">{{ detailTask.taskName }}</el-descriptions-item>
        <el-descriptions-item label="盘点范围">{{ detailTask.deptNames || detailTask.deptIds || '-' }}</el-descriptions-item>
        <el-descriptions-item label="参与人员">{{ detailTask.participantNames || detailTask.participantIds || '-' }}</el-descriptions-item>
        <el-descriptions-item label="盘点时间" :span="2">
          {{ detailTask.startDate || '-' }} 至 {{ detailTask.endDate || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <h4 style="margin-bottom: 16px">任务流程</h4>
      <el-steps :active="taskActiveStep" finish-status="success" process-status="process" align-center>
        <el-step title="待开始" description="任务已发布" />
        <el-step title="进行中" description="盘点执行中" />
        <el-step title="已完成" description="盘点结束" />
      </el-steps>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCheckTaskPage, createCheckTask, analyzeDiff, listPendingDiffs } from '@/api/check'
import { getDepartments, getUsers } from '@/api/admin'

const router = useRouter()
const loading = ref(false)

// 全局待处置差异
const globalDiffVisible = ref(false)
const globalDiffs = ref<any[]>([])
const globalPendingCount = ref(0)

async function loadGlobalPending() {
  try {
    const res = await listPendingDiffs()
    globalDiffs.value = res.data || []
    globalPendingCount.value = globalDiffs.value.length
  } catch {
    // 顶部计数失败不该打断任务列表，静默即可
    globalPendingCount.value = 0
  }
}

async function openGlobalDiffs() {
  await loadGlobalPending()
  globalDiffVisible.value = true
}
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const analysisVisible = ref(false)
const analyzing = ref(false)
const analysisResults = ref<any[]>([])
const detailVisible = ref(false)
const detailTask = ref<any>({})
const dateRange = ref<any[]>([])

// 下拉选项（从后端动态加载）
const deptOptions = ref<any[]>([])
const userOptions = ref<any[]>([])

const stats = reactive({ total: 0, pending: 0, inProgress: 0, completed: 0 })

const search = reactive({ current: 1, size: 10, status: '' })

const form = reactive({
  taskName: '', deptIds: [] as number[], categoryIds: '',
  participantIds: [] as number[], startDate: '', endDate: ''
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getCheckTaskPage(search)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

async function handleCreate() {
  if (!form.taskName) {
    ElMessage.warning('请输入任务名称')
    return
  }
  if (dateRange.value && dateRange.value.length === 2) {
    form.startDate = dateRange.value[0]
    form.endDate = dateRange.value[1]
  }
  // 将数组转为逗号分隔字符串，匹配后端 CheckTask 实体字段类型
  const payload = {
    ...form,
    deptIds: form.deptIds.length > 0 ? form.deptIds.join(',') : '',
    participantIds: form.participantIds.length > 0 ? form.participantIds.join(',') : ''
  }
  await createCheckTask(payload)
  ElMessage.success('盘点任务发布成功')
  dialogVisible.value = false
  fetchData()
}

function openCreateDialog() {
  Object.assign(form, {
    taskName: '', deptIds: [], categoryIds: '', participantIds: [], startDate: '', endDate: ''
  })
  dateRange.value = []
  dialogVisible.value = true
}

function enterCheck(row: any) {
  router.push(`/check/collaborate/${row.id}`)
}

async function analyzeDiffHandler(taskId: number) {
  analyzing.value = true
  analysisVisible.value = true
  try {
    const res = await analyzeDiff(taskId)
    analysisResults.value = res.data || []
  } catch {
    analysisResults.value = []
    ElMessage.error('分析失败，请重试')
  } finally {
    analyzing.value = false
  }
}

async function fetchStats() {
  try {
    const [all, pending, inProgress, completed] = await Promise.all([
      getCheckTaskPage({ size: 1 }),
      getCheckTaskPage({ size: 1, status: 'PENDING' }),
      getCheckTaskPage({ size: 1, status: 'IN_PROGRESS' }),
      getCheckTaskPage({ size: 1, status: 'COMPLETED' })
    ])
    stats.total = all.data.total
    stats.pending = pending.data.total
    stats.inProgress = inProgress.data.total
    stats.completed = completed.data.total
  } catch { /* stats are non-critical */ }
}

/** 加载部门和用户下拉选项 */
async function fetchDropdownOptions() {
  try {
    const [deptRes, userRes] = await Promise.all([
      getDepartments(),
      getUsers()
    ])
    deptOptions.value = deptRes.data || []
    userOptions.value = userRes.data || []
  } catch { /* dropdown options are non-critical */ }
}

function showDetail(row: any) {
  detailTask.value = row
  detailVisible.value = true
}

const taskActiveStep = computed(() => {
  const s = detailTask.value.status
  if (s === 'COMPLETED') return 3
  if (s === 'IN_PROGRESS') return 2
  return 1
})

onMounted(() => {
  fetchData()
  fetchStats()
  fetchDropdownOptions()
  loadGlobalPending()
})
</script>
