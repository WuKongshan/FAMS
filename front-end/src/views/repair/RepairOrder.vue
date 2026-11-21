<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-card-row">
      <el-card shadow="hover">
        <el-statistic title="报修总数" :value="stats.total" />
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="待初审" :value="stats.pendingFirst">
          <template #suffix><el-tag type="warning" size="small">待初审</el-tag></template>
        </el-statistic>
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="维修中" :value="stats.inProgress">
          <template #suffix><el-tag type="primary" size="small">维修中</el-tag></template>
        </el-statistic>
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="已完成" :value="stats.completed">
          <template #suffix><el-tag type="success" size="small">完成</el-tag></template>
        </el-statistic>
      </el-card>
    </div>

    <div class="search-bar">
      <el-select v-model="search.status" placeholder="工单状态" clearable style="width: 160px">
        <el-option label="待初审" value="PENDING_FIRST" />
        <el-option label="维修中" value="IN_PROGRESS" />
        <el-option label="初审驳回" value="FIRST_REJECT" />
        <el-option label="已完成" value="COMPLETED" />
      </el-select>
      <el-button type="primary" @click="fetchData">查询</el-button>
      <el-button type="danger" @click="openReportDialog" style="margin-left: auto">提交报修</el-button>
    </div>

    <el-table :data="tableData" border stripe v-loading="loading">
      <el-table-column prop="orderNo" label="工单编号" width="140" />
      <el-table-column prop="assetName" label="资产名称" min-width="150" />
      <el-table-column prop="reporterName" label="报修人" width="100" />
      <el-table-column prop="faultDesc" label="故障描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="repairerName" label="维修师傅" width="110">
        <template #default="{ row }">{{ row.repairerName || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="报修时间" width="170" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showDetail(row)">详情</el-button>
          <template v-if="row.status === 'PENDING_FIRST' && (userStore.isAdmin || userStore.isDeptAdmin)">
            <el-button size="small" type="success" @click="openApproveDialog(row, 'PASS')">通过</el-button>
            <el-button size="small" type="danger" @click="openApproveDialog(row, 'REJECT')">驳回</el-button>
          </template>
          <el-button v-if="row.status === 'IN_PROGRESS' && (userStore.isAdmin || userStore.isDeptAdmin)"
            size="small" type="success" @click="openCompleteDialog(row)">完成维修</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="search.current" v-model:page-size="search.size"
      :total="total" layout="total, prev, pager, next" style="margin-top: 16px; justify-content: flex-end"
      @change="fetchData"
    />

    <!-- 报修弹窗 -->
    <el-dialog title="提交报修" v-model="reportVisible" width="500px">
      <el-form :model="reportForm" label-width="80px">
        <el-form-item label="资产编号" required>
          <el-input v-model="reportForm.assetCode" @blur="lookupAsset" placeholder="输入编号后自动填充资产名称" />
        </el-form-item>
        <el-form-item label="资产名称">
          <el-input v-model="reportForm.assetName" :readonly="true" placeholder="自动填充" />
        </el-form-item>
        <el-form-item label="故障描述" required>
          <el-input v-model="reportForm.faultDesc" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" @click="handleReport">提交</el-button>
      </template>
    </el-dialog>

    <!-- 初审审批弹窗 -->
    <el-dialog title="初审审批" v-model="approveVisible" width="450px">
      <el-form label-width="80px">
        <el-form-item label="审批结果">
          <el-tag :type="approveResult === 'PASS' ? 'success' : 'danger'">
            {{ approveResult === 'PASS' ? '通过（确认需要维修）' : '驳回（无需维修）' }}
          </el-tag>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input v-model="approveComment" type="textarea" :rows="3" placeholder="请输入审批意见（驳回时必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" @click="handleApprove">确认</el-button>
      </template>
    </el-dialog>

    <!-- 完成维修弹窗 -->
    <el-dialog title="完成维修" v-model="completeVisible" width="480px">
      <el-form :model="completeForm" label-width="80px">
        <el-form-item label="维修师傅">
          <el-tag type="primary">{{ completeOrder.repairerName || '-' }}</el-tag>
        </el-form-item>
        <el-form-item label="维修结果" required>
          <el-input v-model="completeForm.repairResult" type="textarea" :rows="3" placeholder="描述维修过程和结果" />
        </el-form-item>
        <el-form-item label="维修费用">
          <el-input-number v-model="completeForm.repairCost" :min="0" :precision="2" style="width: 100%" placeholder="元（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" @click="handleComplete">确认完成</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog title="报修工单详情" v-model="detailVisible" width="650px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 20px">
        <el-descriptions-item label="工单编号">{{ detailOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag :type="statusTag(detailOrder.status)">{{ statusText(detailOrder.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="资产名称">{{ detailOrder.assetName }}</el-descriptions-item>
        <el-descriptions-item label="资产编号">{{ detailOrder.assetCode }}</el-descriptions-item>
        <el-descriptions-item label="报修人">{{ detailOrder.reporterName }}</el-descriptions-item>
        <el-descriptions-item label="报修时间">{{ detailOrder.createTime }}</el-descriptions-item>
        <el-descriptions-item label="故障描述" :span="2">{{ detailOrder.faultDesc || '-' }}</el-descriptions-item>
        <el-descriptions-item label="维修师傅">{{ detailOrder.repairerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="接单时间">{{ detailOrder.acceptTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="维修结果" :span="2">{{ detailOrder.repairResult || '-' }}</el-descriptions-item>
        <el-descriptions-item label="维修费用">{{ detailOrder.repairCost != null ? detailOrder.repairCost + ' 元' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ detailOrder.completeTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <h4 style="margin-bottom: 16px">工单流程</h4>
      <el-steps :active="detailActiveStep" finish-status="success" process-status="process" align-center>
        <el-step title="提交报修" :description="detailOrder.reporterName || detailOrder.reporterId" />
        <el-step title="初审确认" :status="firstStepStatus" :description="firstStepDesc" />
        <el-step title="维修处理" :status="repairStepStatus" :description="repairStepDesc" />
        <el-step title="完成" :status="completeStepStatus" />
      </el-steps>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getRepairPage, submitRepair, updateRepair, getRepairDetail } from '@/api/repair'
import { getAssetPage } from '@/api/asset'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const reportVisible = ref(false)
const approveVisible = ref(false)
const approveResult = ref('PASS')
const approveComment = ref('')
const approveOrder = ref<any>({})
const completeVisible = ref(false)
const completeOrder = ref<any>({})
const detailVisible = ref(false)
const detailOrder = ref<any>({})

const stats = reactive({ total: 0, pendingFirst: 0, inProgress: 0, completed: 0 })

const search = reactive({ current: 1, size: 10, status: '' })
const reportForm = reactive({ assetId: null as number | null, assetCode: '', assetName: '', faultDesc: '' })
const completeForm = reactive({ repairResult: '', repairCost: null as number | null })

function statusText(s: string) {
  const map: Record<string, string> = {
    PENDING_FIRST: '待初审', FIRST_REJECT: '初审驳回', IN_PROGRESS: '维修中', COMPLETED: '已完成'
  }
  return map[s] || s
}

function statusTag(s: string) {
  const map: Record<string, string> = {
    PENDING_FIRST: 'warning', FIRST_REJECT: 'danger', IN_PROGRESS: 'primary', COMPLETED: 'success'
  }
  return map[s] || 'info'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getRepairPage(search)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openReportDialog() {
  Object.assign(reportForm, { assetId: null, assetCode: '', assetName: '', faultDesc: '' })
  reportVisible.value = true
}

async function lookupAsset() {
  if (!reportForm.assetCode) return
  try {
    const res = await getAssetPage({ keyword: reportForm.assetCode, size: 1 })
    if (res.data.records?.length > 0) {
      const asset = res.data.records[0]
      reportForm.assetId = asset.id
      reportForm.assetName = asset.assetName
    } else {
      reportForm.assetId = null
      reportForm.assetName = ''
      ElMessage.warning('未找到该资产编号')
    }
  } catch { /* ignore */ }
}

async function handleReport() {
  await submitRepair({
    ...reportForm,
    reporterId: userStore.userId,
    reporterName: userStore.realName
  })
  ElMessage.success('报修提交成功')
  reportVisible.value = false
  fetchData()
  fetchStats()
}

// -- 初审审批 --
function openApproveDialog(row: any, result: string) {
  approveOrder.value = row
  approveResult.value = result
  approveComment.value = ''
  approveVisible.value = true
}

async function handleApprove() {
  if (approveResult.value === 'REJECT' && !approveComment.value) {
    ElMessage.warning('驳回时请填写审批意见')
    return
  }
  const payload: any = { id: approveOrder.value.id }
  if (approveResult.value === 'PASS') {
    payload.status = 'IN_PROGRESS'
    payload.repairerId = userStore.userId
    payload.repairerName = userStore.realName
  } else {
    payload.status = 'FIRST_REJECT'
    payload.repairerId = userStore.userId
    payload.repairerName = userStore.realName
  }
  await updateRepair(payload)
  ElMessage.success('审批完成')
  approveVisible.value = false
  fetchData()
  fetchStats()
}

// -- 完成维修 --
function openCompleteDialog(row: any) {
  completeOrder.value = row
  Object.assign(completeForm, { repairResult: '', repairCost: null })
  completeVisible.value = true
}

async function handleComplete() {
  if (!completeForm.repairResult) { ElMessage.warning('请填写维修结果'); return }
  await updateRepair({
    id: completeOrder.value.id,
    status: 'COMPLETED',
    repairResult: completeForm.repairResult,
    repairCost: completeForm.repairCost
  })
  ElMessage.success('维修完成')
  completeVisible.value = false
  fetchData()
  fetchStats()
}

// -- 详情 --
function showDetail(row: any) {
  detailOrder.value = row
  detailVisible.value = true
  getRepairDetail(row.id).then(res => {
    if (res.data) detailOrder.value = res.data
  }).catch(() => {})
}

// -- 详情步骤计算 --
const detailActiveStep = computed(() => {
  const s = detailOrder.value.status
  if (s === 'COMPLETED') return 4
  if (s === 'IN_PROGRESS') return 3
  if (s === 'FIRST_REJECT' || s === 'PENDING_FIRST') return 2
  return 1
})

const firstStepStatus = computed(() => {
  const s = detailOrder.value.status
  if (s === 'PENDING_FIRST') return ''
  if (s === 'FIRST_REJECT') return 'error'
  if (s === 'IN_PROGRESS' || s === 'COMPLETED') return 'success'
  return ''
})

const firstStepDesc = computed(() => {
  const s = detailOrder.value.status
  if (s === 'PENDING_FIRST') return '待初审'
  if (s === 'FIRST_REJECT') return '初审驳回'
  if (s === 'IN_PROGRESS') return `维修师傅：${detailOrder.value.repairerName || '-'}`
  if (s === 'COMPLETED') return '初审通过'
  return ''
})

const repairStepStatus = computed(() => {
  const s = detailOrder.value.status
  if (s === 'IN_PROGRESS') return ''
  if (s === 'COMPLETED') return 'success'
  return ''
})

const repairStepDesc = computed(() => {
  const s = detailOrder.value.status
  if (s === 'IN_PROGRESS') return `维修师傅：${detailOrder.value.repairerName || '-'}`
  if (s === 'COMPLETED') return '维修完成'
  return ''
})

const completeStepStatus = computed(() => {
  return detailOrder.value.status === 'COMPLETED' ? 'success' : ''
})

async function fetchStats() {
  try {
    const [all, pendingFirst, inProgress, completed] = await Promise.all([
      getRepairPage({ size: 1 }),
      getRepairPage({ size: 1, status: 'PENDING_FIRST' }),
      getRepairPage({ size: 1, status: 'IN_PROGRESS' }),
      getRepairPage({ size: 1, status: 'COMPLETED' })
    ])
    stats.total = all.data.total
    stats.pendingFirst = pendingFirst.data.total
    stats.inProgress = inProgress.data.total
    stats.completed = completed.data.total
  } catch { /* stats are non-critical */ }
}

onMounted(() => {
  fetchData()
  fetchStats()
})
</script>
