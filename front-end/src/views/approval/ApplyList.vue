<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-card-row">
      <el-card shadow="hover">
        <el-statistic title="申请总数" :value="stats.total" />
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="待审批" :value="stats.pendingCount">
          <template #suffix><el-tag type="warning" size="small">待审</el-tag></template>
        </el-statistic>
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="已通过" :value="stats.passedCount">
          <template #suffix><el-tag type="success" size="small">通过</el-tag></template>
        </el-statistic>
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="已驳回" :value="stats.rejectedCount">
          <template #suffix><el-tag type="danger" size="small">驳回</el-tag></template>
        </el-statistic>
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="已完成" :value="stats.completedCount">
          <template #suffix><el-tag type="info" size="small">完成</el-tag></template>
        </el-statistic>
      </el-card>
    </div>

    <div class="search-bar">
      <el-select v-model="search.applyType" placeholder="申请类型" clearable style="width: 140px">
        <el-option label="领用" value="BORROW" />
        <el-option label="归还" value="RETURN" />
      </el-select>
      <el-select v-model="search.status" placeholder="审批状态" clearable style="width: 160px">
        <el-option label="待初审" value="PENDING_FIRST" />
        <el-option label="初审通过" value="FIRST_PASS" />
        <el-option label="初审驳回" value="FIRST_REJECT" />
        <el-option label="待复审" value="PENDING_SECOND" />
        <el-option label="已完成" value="COMPLETED" />
      </el-select>
      <el-button type="primary" @click="fetchData">查询</el-button>
      <el-button type="success" @click="openApplyDialog" style="margin-left: auto">提交申请</el-button>
    </div>

    <el-table :data="tableData" border stripe v-loading="loading">
      <el-table-column prop="applyNo" label="申请编号" width="150" />
      <el-table-column label="申请类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.applyType === 'BORROW' ? 'primary' : 'success'">
            {{ row.applyType === 'BORROW' ? '领用' : '归还' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="assetCode" label="资产编号" width="130" />
      <el-table-column prop="assetName" label="资产名称" min-width="160" />
      <el-table-column prop="applyUserName" label="申请人" width="100" />
      <el-table-column prop="purpose" label="用途说明" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">{{ statusText(row.status) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button size="small" @click="showDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="search.current" v-model:page-size="search.size"
      :total="total" layout="total, prev, pager, next" style="margin-top: 16px; justify-content: flex-end"
      @change="fetchData"
    />

    <!-- 提交申请弹窗 -->
    <el-dialog title="提交申请" v-model="dialogVisible" width="550px">
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="申请类型" required>
          <el-radio-group v-model="form.applyType">
            <el-radio value="BORROW">领用</el-radio>
            <el-radio value="RETURN">归还</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="资产编号" required>
          <el-input v-model="form.assetCode" placeholder="输入资产编号（如 AST-2024-001）" @blur="lookupAsset" />
        </el-form-item>
        <el-form-item label="资产名称">
          <el-input v-model="form.assetName" />
        </el-form-item>
        <el-form-item label="使用期限">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至"
            start-placeholder="开始日期" end-placeholder="结束日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="用途说明">
          <el-input v-model="form.purpose" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleApply">提交</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog title="申请详情" v-model="detailVisible" width="650px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 20px">
        <el-descriptions-item label="申请编号">{{ detailOrder.applyNo }}</el-descriptions-item>
        <el-descriptions-item label="申请类型">
          <el-tag :type="detailOrder.applyType === 'BORROW' ? 'primary' : 'success'" size="small">
            {{ detailOrder.applyType === 'BORROW' ? '领用' : '归还' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="资产名称">{{ detailOrder.assetName }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detailOrder.applyUserName }}</el-descriptions-item>
        <el-descriptions-item label="用途说明" :span="2">{{ detailOrder.purpose || '-' }}</el-descriptions-item>
      </el-descriptions>

      <h4 style="margin-bottom: 16px">审批流程</h4>
      <el-steps :active="detailActiveStep" finish-status="success" process-status="process" align-center>
        <el-step title="提交申请" :description="detailOrder.applyUserName || detailOrder.applyUserId" />
        <el-step title="初审" :status="firstStepStatus" :description="firstStepDesc" />
        <el-step title="复审" :status="secondStepStatus" :description="secondStepDesc" />
        <el-step title="完成" :status="completeStepStatus" />
      </el-steps>

      <div v-if="detailRecords.length > 0" style="margin-top: 20px">
        <h4 style="margin-bottom: 12px">审批记录</h4>
        <el-table :data="detailRecords" border size="small" max-height="250">
          <el-table-column prop="approveLevel" label="审批层级" width="80">
            <template #default="{ row }">{{ row.approveLevel === 'FIRST' ? '初审' : '复审' }}</template>
          </el-table-column>
          <el-table-column prop="approveUserName" label="审批人" width="100" />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.result === 'PASS' ? 'success' : 'danger'" size="small">
                {{ row.result === 'PASS' ? '通过' : '驳回' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="comment" label="意见" min-width="140" show-overflow-tooltip />
          <el-table-column prop="approveTime" label="审批时间" width="160" />
        </el-table>
      </div>
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
import { getApprovalPage, submitApply, getApprovalDetail } from '@/api/approval'
import { getAssetPage } from '@/api/asset'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dateRange = ref<any[]>([])
const formRef = ref()
const detailVisible = ref(false)
const detailOrder = ref<any>({})
const detailRecords = ref<any[]>([])

const stats = reactive({ total: 0, pendingCount: 0, passedCount: 0, rejectedCount: 0, completedCount: 0 })

const search = reactive({ current: 1, size: 10, applyType: '', status: '', applyUserId: '' })

const form = reactive({
  applyType: 'BORROW', assetId: null as number | null, assetCode: '', assetName: '',
  applyUserId: userStore.userId, applyUserName: userStore.realName,
  startDate: '', endDate: '', purpose: ''
})

function statusText(s: string) {
  const map: Record<string, string> = {
    PENDING_FIRST: '待初审', FIRST_PASS: '初审通过', FIRST_REJECT: '初审驳回',
    PENDING_SECOND: '待复审', SECOND_PASS: '复审通过', SECOND_REJECT: '复审驳回', COMPLETED: '已完成'
  }
  return map[s] || s
}

async function fetchStats() {
  const baseParams = { size: 1, applyUserId: userStore.userId }
  try {
    const [all, pendingFirst, pendingSecond, passedFirst, passedSecond, rejectedFirst, rejectedSecond, completed] = await Promise.all([
      getApprovalPage({ ...baseParams }),
      getApprovalPage({ ...baseParams, status: 'PENDING_FIRST' }),
      getApprovalPage({ ...baseParams, status: 'PENDING_SECOND' }),
      getApprovalPage({ ...baseParams, status: 'FIRST_PASS' }),
      getApprovalPage({ ...baseParams, status: 'SECOND_PASS' }),
      getApprovalPage({ ...baseParams, status: 'FIRST_REJECT' }),
      getApprovalPage({ ...baseParams, status: 'SECOND_REJECT' }),
      getApprovalPage({ ...baseParams, status: 'COMPLETED' })
    ])
    stats.total = all.data.total
    stats.pendingCount = (pendingFirst.data.total || 0) + (pendingSecond.data.total || 0)
    stats.passedCount = (passedFirst.data.total || 0) + (passedSecond.data.total || 0)
    stats.rejectedCount = (rejectedFirst.data.total || 0) + (rejectedSecond.data.total || 0)
    stats.completedCount = completed.data.total
  } catch { /* stats are non-critical */ }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getApprovalPage({
      ...search,
      applyUserId: search.applyUserId || userStore.userId
    })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openApplyDialog() {
  Object.assign(form, {
    applyType: 'BORROW', assetCode: '', assetName: '', startDate: '', endDate: '', purpose: ''
  })
  dateRange.value = []
  dialogVisible.value = true
}

async function lookupAsset() {
  if (!form.assetCode) return
  try {
    const res = await getAssetPage({ keyword: form.assetCode, size: 1 })
    if (res.data.records?.length > 0) {
      const asset = res.data.records[0]
      form.assetId = asset.id
      form.assetName = asset.assetName
    } else {
      form.assetId = null
      form.assetName = ''
      ElMessage.warning('未找到该资产编号')
    }
  } catch { /* */ }
}

async function handleApply() {
  if (!form.assetId) {
    ElMessage.warning('请输入有效的资产编号')
    return
  }
  if (dateRange.value?.length === 2) {
    form.startDate = dateRange.value[0]
    form.endDate = dateRange.value[1]
  }
  await submitApply(form)
  ElMessage.success('申请提交成功')
  dialogVisible.value = false
  fetchData()
}

function showDetail(row: any) {
  detailOrder.value = row
  detailRecords.value = []
  detailVisible.value = true
  getApprovalDetail(row.id).then(res => {
    if (res.data) {
      detailOrder.value = res.data.order || row
      detailRecords.value = res.data.records || []
    }
  }).catch(() => {})
}

// --- 详情步骤计算 ---
const stepStatusMap: Record<string, { active: number; firstStatus: string; firstDesc: string; secondStatus: string; secondDesc: string; completeStatus: string }> = {
  PENDING_FIRST:  { active: 1, firstStatus: '', firstDesc: '待审批', secondStatus: '', secondDesc: '', completeStatus: '' },
  FIRST_PASS:     { active: 2, firstStatus: 'success', firstDesc: '初审通过', secondStatus: '', secondDesc: '待复审', completeStatus: '' },
  FIRST_REJECT:   { active: 1, firstStatus: 'error', firstDesc: '初审驳回', secondStatus: '', secondDesc: '', completeStatus: '' },
  PENDING_SECOND: { active: 2, firstStatus: 'success', firstDesc: '初审通过', secondStatus: '', secondDesc: '待复审', completeStatus: '' },
  SECOND_PASS:    { active: 3, firstStatus: 'success', firstDesc: '初审通过', secondStatus: 'success', secondDesc: '复审通过', completeStatus: '' },
  SECOND_REJECT:  { active: 3, firstStatus: 'success', firstDesc: '初审通过', secondStatus: 'error', secondDesc: '复审驳回', completeStatus: '' },
  COMPLETED:      { active: 4, firstStatus: 'success', firstDesc: '初审通过', secondStatus: 'success', secondDesc: '复审通过', completeStatus: 'success' },
}

const detailActiveStep = computed(() => stepStatusMap[detailOrder.value.status]?.active ?? 1)
const firstStepStatus = computed(() => stepStatusMap[detailOrder.value.status]?.firstStatus || '')
const firstStepDesc = computed(() => stepStatusMap[detailOrder.value.status]?.firstDesc || '')
const secondStepStatus = computed(() => stepStatusMap[detailOrder.value.status]?.secondStatus || '')
const secondStepDesc = computed(() => stepStatusMap[detailOrder.value.status]?.secondDesc || '')
const completeStepStatus = computed(() => stepStatusMap[detailOrder.value.status]?.completeStatus || '')

onMounted(() => {
  fetchData()
  fetchStats()
})
</script>
