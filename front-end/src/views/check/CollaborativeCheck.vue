<template>
  <div class="page-container">
    <div class="table-actions">
      <h3>协同盘点 - {{ taskName }}</h3>
      <div>
        <el-button type="primary" @click="addRow">新增盘点记录</el-button>
        <el-button type="success" :loading="analyzing" @click="handleAnalyze">LLM智能分析差异</el-button>
        <el-button type="warning" @click="handleConfirm">确认盘点结果</el-button>
        <el-button type="danger" plain @click="openPendingDiffs">待处置差异</el-button>
      </div>
    </div>

    <!-- 协同编辑表格 -->
    <el-table :data="records" border stripe style="width: 100%">
      <el-table-column prop="assetCode" label="资产编号" width="140">
        <template #default="{ row, $index }">
          <el-input v-model="row.assetCode" size="small" @change="onCellChange($index)" />
        </template>
      </el-table-column>
      <el-table-column prop="assetName" label="资产名称" min-width="160">
        <template #default="{ row, $index }">
          <el-input v-model="row.assetName" size="small" @change="onCellChange($index)" />
        </template>
      </el-table-column>
      <el-table-column prop="bookQuantity" label="账面数量" width="100">
        <template #default="{ row }">
          <span>{{ row.bookQuantity }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="actualQuantity" label="实盘数量" width="140">
        <template #default="{ row, $index }">
          <el-input-number v-model="row.actualQuantity" :min="0" @change="onCellChange($index)" />
        </template>
      </el-table-column>
      <el-table-column prop="diffQuantity" label="差异" width="80">
        <template #default="{ row }">
          <span :style="{ color: row.diffQuantity < 0 ? '#f56c6c' : row.diffQuantity > 0 ? '#67c23a' : '' }">
            {{ row.diffQuantity > 0 ? '+' : '' }}{{ row.diffQuantity }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="100">
        <template #default="{ row }">
          <el-tag :type="row.result === 'MATCH' ? 'success' : row.result === 'SURPLUS' ? 'warning' : 'danger'" size="small">
            {{ row.result === 'MATCH' ? '相符' : row.result === 'SURPLUS' ? '盘盈' : '盘亏' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="150">
        <template #default="{ row, $index }">
          <el-input v-model="row.remark" size="small" placeholder="备注" @change="onCellChange($index)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ $index }">
          <el-button size="small" type="danger" @click="removeRow($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 差异分析结果 -->
    <el-dialog title="LLM 智能分析结果" v-model="dialogVisible" width="750px">
      <!-- 静默降级提示：分析服务不可用时后端回退模板，必须让用户知道结论不是 AI 给的 -->
      <el-alert
        v-if="analysisDegraded"
        type="warning"
        :closable="false"
        show-icon
        title="分析服务当前不可用，以下结论由规则模板生成，未经 AI 分析"
        style="margin-bottom: 12px"
      />
      <el-table :data="diffResults" border stripe max-height="450">
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
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 待处置差异：只标记，不改台账 -->
    <el-dialog title="待处置差异" v-model="pendingVisible" width="860px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="此处只做标记，不会修改资产台账。盘亏请到「报废管理」提交报废单，盘盈到「资产台账」新增登记，由审批流生效。"
      />
      <el-table :data="pendingDiffs" border stripe max-height="420" style="margin-top: 12px">
        <el-table-column prop="assetCode" label="资产编号" width="130" />
        <el-table-column prop="assetName" label="资产名称" min-width="150" />
        <el-table-column label="差异" width="90">
          <template #default="{ row }">
            <span :style="{ color: row.diffQuantity < 0 ? '#f56c6c' : '#67c23a' }">
              {{ row.diffQuantity > 0 ? '+' : '' }}{{ row.diffQuantity }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SURPLUS' ? 'success' : 'danger'" size="small">
              {{ row.result === 'SURPLUS' ? '盘盈' : '盘亏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处置方式" width="170">
          <template #default="{ row }">
            <el-select v-model="handleTypes[row.id]" size="small" placeholder="选择方式">
              <el-option label="转报废单" value="SCRAP" />
              <el-option label="补登记新增资产" value="ASSET_ADD" />
              <el-option label="人工核实确认" value="MANUAL" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              :loading="handlingId === row.id"
              @click="doHandle(row)"
            >标记已处置</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span>该任务没有待处置差异</span>
        </template>
      </el-table>
      <template #footer>
        <el-button @click="pendingVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getCheckRecords, saveCheckRecord, analyzeDiff, getAnalyzeJob, confirmDiff, listPendingDiffs, handleDiff } from '@/api/check'

const route = useRoute()
const userStore = useUserStore()
const taskId = Number(route.params.id)
const taskName = ref('盘点任务')
const records = ref<any[]>([])
const dialogVisible = ref(false)
// 本次分析是否走了模板兜底（后端 engine=rule）
const analysisDegraded = ref(false)
// 待处置差异
const pendingVisible = ref(false)
const pendingDiffs = ref<any[]>([])
const handleTypes = ref<Record<number, string>>({})
const handlingId = ref<number | null>(null)
const analyzing = ref(false)
const diffResults = ref<any[]>([])

async function fetchRecords() {
  try {
    const res = await getCheckRecords(taskId)
    records.value = res.data || []
  } catch { /* */ }
}

function addRow() {
  records.value.push({
    taskId,
    assetCode: '', assetName: '', bookQuantity: 1, actualQuantity: 0,
    diffQuantity: -1, result: 'SHORTAGE', remark: '',
    checkerId: userStore.userId, checkerName: userStore.realName
  })
}

function removeRow(index: number) {
  records.value.splice(index, 1)
}

function onCellChange(index: number) {
  const row = records.value[index]
  row.diffQuantity = row.actualQuantity - row.bookQuantity
  row.result = row.diffQuantity === 0 ? 'MATCH' : row.diffQuantity > 0 ? 'SURPLUS' : 'SHORTAGE'
  // 自动保存，并同步后端返回的资产信息（如根据资产编号自动填充的名称/账面数量等）
  saveCheckRecord(row).then(res => {
    if (res.data && res.data.id) {
      row.id = res.data.id
      // 后端可能根据 assetCode 自动填充了资产信息，同步到前端行
      if (res.data.assetId) row.assetId = res.data.assetId
      if (res.data.assetName) row.assetName = res.data.assetName
      if (res.data.bookQuantity != null) {
        row.bookQuantity = res.data.bookQuantity
        // 账面数量变更后重新计算差异
        row.diffQuantity = row.actualQuantity - row.bookQuantity
        row.result = row.diffQuantity === 0 ? 'MATCH' : row.diffQuantity > 0 ? 'SURPLUS' : 'SHORTAGE'
      }
    }
  }).catch(() => {})
}

// 轮询间隔与分析总等待上限。上限要略大于后端最坏耗时：
// Python 侧单片 60s x 3 次尝试 + 退避约 183s，Java 读取超时 200s。
const POLL_INTERVAL_MS = 1500
const POLL_TIMEOUT_MS = 200000

async function waitForAnalysis(jobId: number): Promise<any> {
  const startedAt = Date.now()
  while (Date.now() - startedAt < POLL_TIMEOUT_MS) {
    await new Promise(resolve => setTimeout(resolve, POLL_INTERVAL_MS))
    const res = await getAnalyzeJob(jobId)
    const job = res.data || {}
    if (job.status === 'SUCCESS' || job.status === 'FAILED') {
      return job
    }
  }
  // 后端进程若在 RUNNING 期间被杀，任务会一直不回终态，这里兜住，不让按钮永远转圈
  throw new Error('分析超时，请稍后重试')
}

async function handleAnalyze() {
  analyzing.value = true
  try {
    const res = await analyzeDiff(taskId)
    const jobId = res.data?.jobId
    if (!jobId) {
      throw new Error('提交分析失败')
    }
    const job = await waitForAnalysis(jobId)
    if (job.status === 'FAILED') {
      throw new Error(job.error || '分析失败')
    }
    diffResults.value = job.result || []
    analysisDegraded.value = !!job.degraded
    dialogVisible.value = true
    if (job.degraded) {
      ElMessage.warning('分析服务不可用，本次为规则模板生成')
    } else {
      ElMessage.success('LLM 智能分析完成')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '分析失败，请重试')
  } finally {
    analyzing.value = false
  }
}

async function openPendingDiffs() {
  try {
    const res = await listPendingDiffs(taskId)
    pendingDiffs.value = res.data || []
    handleTypes.value = {}
    pendingVisible.value = true
  } catch {
    ElMessage.error('加载待处置差异失败')
  }
}

async function doHandle(row: any) {
  const type = handleTypes.value[row.id]
  if (!type) {
    ElMessage.warning('请先选择处置方式')
    return
  }
  handlingId.value = row.id
  try {
    await handleDiff(row.id, type)
    ElMessage.success('已标记处置')
    pendingDiffs.value = pendingDiffs.value.filter(d => d.id !== row.id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '标记失败，请重试')
  } finally {
    handlingId.value = null
  }
}

async function handleConfirm() {
  const ids = records.value.filter(r => r.id).map(r => r.id)
  if (ids.length === 0) {
    ElMessage.warning('请先保存盘点记录')
    return
  }
  try {
    const res = await confirmDiff(taskId, ids)
    const d: any = res.data || {}
    const parts: string[] = []
    if (d.shortage) parts.push(`${d.shortage} 条盘亏`)
    if (d.surplus) parts.push(`${d.surplus} 条盘盈`)

    if (parts.length) {
      // 确认动作不再直接改资产台账：盘亏要走报废单、盘盈要走新增登记，
      // 否则「盘亏 → 闲置」会让已丢失的资产重新变成可领用状态。
      ElMessage({
        type: 'warning',
        duration: 8000,
        message: `盘点已确认：${parts.join('、')}。资产台账未自动变更，请到「报废管理」提交报废单或到「资产台账」新增登记，由审批流生效。`
      })
    } else {
      ElMessage.success('盘点已确认，本次无差异')
    }
  } catch {
    ElMessage.error('确认失败，请重试')
  }
}

onMounted(fetchRecords)
</script>
