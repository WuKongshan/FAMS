<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-card-row">
      <el-card shadow="hover">
        <el-statistic title="资产总数" :value="stats.total" />
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="在用资产" :value="stats.inUse">
          <template #suffix><el-tag type="success" size="small">在用</el-tag></template>
        </el-statistic>
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="闲置资产" :value="stats.idle">
          <template #suffix><el-tag type="info" size="small">闲置</el-tag></template>
        </el-statistic>
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="维修中" :value="stats.repairing">
          <template #suffix><el-tag type="warning" size="small">维修</el-tag></template>
        </el-statistic>
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="已报废" :value="stats.scrapped">
          <template #suffix><el-tag type="danger" size="small">报废</el-tag></template>
        </el-statistic>
      </el-card>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="search.keyword" placeholder="资产编号/名称/使用人" clearable style="width: 220px" />
      <el-select v-model="search.categoryId" placeholder="资产分类" clearable style="width: 160px">
        <el-option label="计算机设备" :value="5" />
        <el-option label="打印设备" :value="6" />
        <el-option label="办公桌椅" :value="7" />
        <el-option label="实验仪器" :value="8" />
      </el-select>
      <el-select v-model="search.status" placeholder="资产状态" clearable style="width: 140px">
        <el-option label="在用" value="在用" />
        <el-option label="闲置" value="闲置" />
        <el-option label="维修中" value="维修中" />
        <el-option label="已报废" value="已报废" />
      </el-select>
      <el-button type="primary" @click="fetchData">
        <el-icon><Search /></el-icon>
        查询
      </el-button>
      <el-button @click="resetSearch">
        <el-icon><Refresh /></el-icon>
        重置
      </el-button>
    </div>

    <!-- 操作栏 -->
    <div class="table-actions">
      <el-button type="primary" @click="openDialog()">新增资产</el-button>
      <el-button @click="handleExport">导出Excel</el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="assetCode" label="资产编号" width="140" />
      <el-table-column prop="assetName" label="资产名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="quantity" label="数量" width="70" />
      <el-table-column prop="categoryName" label="分类" width="120" />
      <el-table-column prop="model" label="型号" width="160" show-overflow-tooltip />
      <el-table-column prop="originalValue" label="原值(元)" width="110" />
      <el-table-column prop="deptName" label="归属部门" width="180" />
      <el-table-column prop="userName" label="使用人" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      v-model:current-page="search.current"
      v-model:page-size="search.size"
      :total="total"
      layout="total, prev, pager, next, sizes"
      :page-sizes="[10, 20, 50]"
      style="margin-top: 16px; justify-content: flex-end"
      @change="fetchData"
    />

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="650px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="资产编号" prop="assetCode">
              <el-input v-model="form.assetCode" placeholder="唯一编号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="资产名称" prop="assetName">
              <el-input v-model="form.assetName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数量" prop="quantity">
              <el-input-number v-model="form.quantity" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" prop="categoryId">
              <el-select v-model="form.categoryId" style="width: 100%" @change="onCategoryChange">
                <el-option label="计算机设备" :value="5" />
                <el-option label="打印设备" :value="6" />
                <el-option label="办公桌椅" :value="7" />
                <el-option label="实验仪器" :value="8" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="型号" prop="model">
              <el-input v-model="form.model" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原值(元)" prop="originalValue">
              <el-input-number v-model="form.originalValue" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="购置时间" prop="purchaseDate">
              <el-date-picker v-model="form.purchaseDate" type="date" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="存放地点" prop="location">
              <el-input v-model="form.location" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="归属部门" prop="deptId">
              <el-select v-model="form.deptId" style="width: 100%" @change="onDeptChange">
                <el-option label="计算机学院" :value="1" />
                <el-option label="电子信息学院" :value="2" />
                <el-option label="机械学院" :value="3" />
                <el-option label="经管学院" :value="4" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="资产状态" prop="status">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="在用" value="在用" />
                <el-option label="闲置" value="闲置" />
                <el-option label="维修中" value="维修中" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAssetPage, addAsset, updateAsset, deleteAsset, exportAssets, type AssetQuery } from '@/api/asset'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()

const stats = reactive({ total: 0, inUse: 0, idle: 0, repairing: 0, scrapped: 0 })

const search = reactive<AssetQuery>({
  current: 1, size: 10, keyword: '', categoryId: undefined, status: undefined, deptId: undefined
})

const form = reactive<any>({
  id: null, assetCode: '', assetName: '', quantity: 1, categoryId: null, categoryName: '',
  model: '', originalValue: 0, purchaseDate: '', location: '', deptId: null, deptName: '', status: '闲置'
})

const formRules = {
  assetCode: [{ required: true, message: '请输入资产编号', trigger: 'blur' }],
  assetName: [{ required: true, message: '请输入资产名称', trigger: 'blur' }]
}

const dialogTitle = ref('新增资产')

function statusTag(status: string) {
  const map: Record<string, string> = { '在用': 'success', '闲置': 'info', '维修中': 'warning', '已报废': 'danger' }
  return map[status] || 'info'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getAssetPage(search)
    tableData.value = res.data.records
    total.value = res.data.total
  } catch (e: any) {
    console.error('资产数据加载失败:', e)
    ElMessage.error('资产数据加载失败: ' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function fetchStats() {
  try {
    const [all, inUse, idle, repairing, scrapped] = await Promise.all([
      getAssetPage({ size: 1 }),
      getAssetPage({ size: 1, status: '在用' }),
      getAssetPage({ size: 1, status: '闲置' }),
      getAssetPage({ size: 1, status: '维修中' }),
      getAssetPage({ size: 1, status: '已报废' })
    ])
    stats.total = all.data.total
    stats.inUse = inUse.data.total
    stats.idle = idle.data.total
    stats.repairing = repairing.data.total
    stats.scrapped = scrapped.data.total
  } catch (e) {
    console.error('资产统计数据加载失败:', e)
  }
}

function resetSearch() {
  search.keyword = ''
  search.categoryId = undefined
  search.status = undefined
  search.current = 1
  fetchData()
}

function openDialog(row?: any) {
  if (row) {
    isEdit.value = true
    dialogTitle.value = '编辑资产'
    Object.assign(form, row)
  } else {
    isEdit.value = false
    dialogTitle.value = '新增资产'
    resetForm()
  }
  dialogVisible.value = true
}

function resetForm() {
  Object.assign(form, {
    id: null, assetCode: '', assetName: '', quantity: 1, categoryId: null, categoryName: '',
    model: '', originalValue: 0, purchaseDate: '', location: '', deptId: null, deptName: '', status: '闲置'
  })
}

const categoryMap: Record<number, string> = { 5: '计算机设备', 6: '打印设备', 7: '办公桌椅', 8: '实验仪器' }
const deptMap: Record<number, string> = { 1: '计算机学院', 2: '电子信息学院', 3: '机械学院', 4: '经管学院' }

function onCategoryChange(val: number) {
  form.categoryName = categoryMap[val] || ''
}

function onDeptChange(val: number) {
  form.deptName = deptMap[val] || ''
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (isEdit.value) {
    await updateAsset(form)
    ElMessage.success('资产更新成功')
  } else {
    await addAsset(form)
    ElMessage.success('资产录入成功')
  }
  dialogVisible.value = false
  fetchData()
}

async function handleDelete(id: number) {
  await deleteAsset(id)
  ElMessage.success('删除成功')
  fetchData()
}

async function handleExport() {
  try {
    ElMessage.info('正在导出，请稍候...')
    await exportAssets({
      keyword: search.keyword || undefined,
      categoryId: search.categoryId,
      status: search.status,
      deptId: search.deptId
    })
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(() => {
  fetchData()
  fetchStats()
})
</script>
