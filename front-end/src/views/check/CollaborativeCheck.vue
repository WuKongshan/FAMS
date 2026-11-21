<template>
  <div class="page-container">
    <div class="table-actions">
      <h3>协同盘点 - {{ taskName }}</h3>
      <div>
        <el-button type="primary" @click="addRow">新增盘点记录</el-button>
        <el-button type="success" :loading="analyzing" @click="handleAnalyze">LLM智能分析差异</el-button>
        <el-button type="warning" @click="handleConfirm">确认差异更新台账</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getCheckRecords, saveCheckRecord, analyzeDiff, confirmDiff } from '@/api/check'

const route = useRoute()
const userStore = useUserStore()
const taskId = Number(route.params.id)
const taskName = ref('盘点任务')
const records = ref<any[]>([])
const dialogVisible = ref(false)
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

async function handleAnalyze() {
  analyzing.value = true
  try {
    const res = await analyzeDiff(taskId)
    diffResults.value = res.data || []
    dialogVisible.value = true
    ElMessage.success('LLM 智能分析完成')
  } catch {
    ElMessage.error('分析失败，请重试')
  } finally {
    analyzing.value = false
  }
}

async function handleConfirm() {
  const ids = records.value.filter(r => r.id).map(r => r.id)
  if (ids.length === 0) {
    ElMessage.warning('请先保存盘点记录')
    return
  }
  try {
    await confirmDiff(taskId, ids)
    ElMessage.success('差异已确认，台账已更新')
  } catch {
    ElMessage.error('确认失败，请重试')
  }
}

onMounted(fetchRecords)
</script>
