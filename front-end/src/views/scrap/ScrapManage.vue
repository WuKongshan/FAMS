<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-card-row">
      <el-card shadow="hover">
        <el-statistic title="报废申请总数" :value="stats.total" />
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="待初审" :value="stats.pendingFirst">
          <template #suffix><el-tag type="warning" size="small">初审</el-tag></template>
        </el-statistic>
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="待复审" :value="stats.pendingSecond">
          <template #suffix><el-tag type="primary" size="small">复审</el-tag></template>
        </el-statistic>
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="已完成" :value="stats.completed">
          <template #suffix><el-tag type="success" size="small">完成</el-tag></template>
        </el-statistic>
      </el-card>
    </div>

    <div class="search-bar">
      <el-select v-model="search.status" placeholder="审批状态" clearable style="width: 160px">
        <el-option label="待初审" value="PENDING_FIRST" />
        <el-option label="待复审" value="PENDING_SECOND" />
        <el-option label="已完成" value="COMPLETED" />
      </el-select>
      <el-button type="primary" @click="fetchData">查询</el-button>
      <el-button type="danger" @click="openScrapDialog" style="margin-left: auto">提交报废申请</el-button>
    </div>

    <el-table :data="tableData" border stripe v-loading="loading">
      <el-table-column prop="scrapNo" label="申请编号" width="140" />
      <el-table-column prop="assetCode" label="资产编号" width="130" />
      <el-table-column prop="assetName" label="资产名称" min-width="160" />
      <el-table-column prop="applyUserName" label="申请人" width="100" />
      <el-table-column prop="reason" label="报废原因" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ statusText(row.status) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button size="small" @click="showDetail(row)">详情</el-button>
          <template v-if="(row.status === 'PENDING_FIRST' || row.status === 'PENDING_SECOND') && (userStore.isAdmin || userStore.isDeptAdmin)">
            <el-button size="small" type="success" @click="openApproveDialog(row, 'PASS')">通过</el-button>
            <el-button size="small" type="danger" @click="openApproveDialog(row, 'REJECT')">驳回</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="search.current" v-model:page-size="search.size"
      :total="total" layout="total, prev, pager, next" style="margin-top: 16px; justify-content: flex-end"
      @change="fetchData"
    />

    <!-- 报废申请弹窗 -->
    <el-dialog title="提交报废申请" v-model="dialogVisible" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="资产编号" required>
          <el-input v-model="form.assetCode" @blur="lookupAsset" placeholder="输入编号后自动填充资产名称" />
        </el-form-item>
        <el-form-item label="资产名称">
          <el-input v-model="form.assetName" :readonly="true" placeholder="自动填充" />
        </el-form-item>
        <el-form-item label="报废原因" required>
          <el-input v-model="form.reason" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 审批弹窗 -->
    <el-dialog title="审批意见" v-model="approveVisible" width="450px">
      <el-form label-width="80px">
        <el-form-item label="审批结果">
          <el-tag :type="approveResult === 'PASS' ? 'success' : 'danger'">
            {{ approveResult === 'PASS' ? '通过' : '驳回' }}
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

    <!-- 详情弹窗 -->
    <el-dialog title="报废详情" v-model="detailVisible" width="650px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 20px">
        <el-descriptions-item label="申请编号">{{ detailOrder.scrapNo }}</el-descriptions-item>
        <el-descriptions-item label="资产名称">{{ detailOrder.assetName }}</el-descriptions-item>
        <el-descriptions-item label="资产编号">{{ detailOrder.assetCode }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detailOrder.applyUserName }}</el-descriptions-item>
        <el-descriptions-item label="报废原因" :span="2">{{ detailOrder.reason || '-' }}</el-descriptions-item>
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
import { getScrapPage, submitScrap, approveScrap, getScrapDetail } from '@/api/scrap'
import { getAssetPage } from '@/api/asset'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const approveVisible = ref(false)
const approveResult = ref('PASS')
const approveComment = ref('')
const currentOrder = ref<any>({})
const detailVisible = ref(false)
const detailOrder = ref<any>({})
const detailRecords = ref<any[]>([])

const stats = reactive({ total: 0, pendingFirst: 0, pendingSecond: 0, completed: 0 })

const search = reactive({ current: 1, size: 10, status: '' })

const form = reactive({
  assetId: null, assetCode: '', assetName: '', reason: '',
  applyUserId: userStore.userId, applyUserName: userStore.realName
})

function statusText(s: string) {
  const map: Record<string, string> = {
    PENDING_FIRST: '待初审', FIRST_PASS: '初审通过', FIRST_REJECT: '初审驳回',
    PENDING_SECOND: '待复审', SECOND_PASS: '复审通过', SECOND_REJECT: '复审驳回', COMPLETED: '已完成'
  }
  return map[s] || s
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getScrapPage(search)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openScrapDialog() {
  Object.assign(form, { assetId: null, assetCode: '', assetName: '', reason: '', applyUserId: userStore.userId, applyUserName: userStore.realName })
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
  } catch { /* ignore */ }
}

async function handleSubmit() {
  await submitScrap(form)
  ElMessage.success('报废申请提交成功')
  dialogVisible.value = false
  fetchData()
}

function openApproveDialog(row: any, result: string) {
  currentOrder.value = row
  approveResult.value = result
  approveComment.value = ''
  approveVisible.value = true
}

async function handleApprove() {
  if (approveResult.value === 'REJECT' && !approveComment.value) {
    ElMessage.warning('驳回时请填写审批意见')
    return
  }
  const level = currentOrder.value.status === 'PENDING_FIRST' ? 'FIRST' : 'SECOND'
  await approveScrap({
    orderId: currentOrder.value.id,
    level,
    result: approveResult.value,
    comment: approveComment.value
  })
  ElMessage.success('审批完成')
  approveVisible.value = false
  fetchData()
  fetchStats()
}

async function fetchStats() {
  try {
    const [all, pendingFirst, pendingSecond, completed] = await Promise.all([
      getScrapPage({ size: 1 }),
      getScrapPage({ size: 1, status: 'PENDING_FIRST' }),
      getScrapPage({ size: 1, status: 'PENDING_SECOND' }),
      getScrapPage({ size: 1, status: 'COMPLETED' })
    ])
    stats.total = all.data.total
    stats.pendingFirst = pendingFirst.data.total
    stats.pendingSecond = pendingSecond.data.total
    stats.completed = completed.data.total
  } catch { /* stats are non-critical */ }
}

function showDetail(row: any) {
  detailOrder.value = row
  detailRecords.value = []
  detailVisible.value = true
  getScrapDetail(row.id).then(res => {
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
