<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-card-row">
      <el-card shadow="hover">
        <el-statistic title="用户总数" :value="stats.total" />
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="超级管理员" :value="stats.adminCount">
          <template #suffix><el-tag type="danger" size="small">管理员</el-tag></template>
        </el-statistic>
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="学院管理员" :value="stats.deptAdminCount">
          <template #suffix><el-tag type="warning" size="small">学院</el-tag></template>
        </el-statistic>
      </el-card>
      <el-card shadow="hover">
        <el-statistic title="普通师生" :value="stats.userCount">
          <template #suffix><el-tag type="info" size="small">师生</el-tag></template>
        </el-statistic>
      </el-card>
    </div>

    <div class="search-bar">
      <el-input v-model="search.keyword" placeholder="用户名/姓名/手机号" clearable style="width: 220px" />
      <el-select v-model="search.deptId" placeholder="部门" clearable style="width: 180px">
        <el-option label="计算机学院" :value="1" />
        <el-option label="电子信息学院" :value="2" />
        <el-option label="机械学院" :value="3" />
        <el-option label="经管学院" :value="4" />
      </el-select>
      <el-button type="primary" @click="fetchData">查询</el-button>
      <el-button type="success" @click="openDialog()" style="margin-left: auto">新增用户</el-button>
    </div>

    <el-table :data="tableData" border stripe v-loading="loading">
      <el-table-column prop="username" label="用户名" width="130" />
      <el-table-column prop="realName" label="真实姓名" width="100" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="deptName" label="所属部门" min-width="160" />
      <el-table-column label="角色" width="140">
        <template #default="{ row }">
          <el-tag :type="row.roleId === 1 ? 'danger' : row.roleId === 2 ? 'warning' : 'info'">
            {{ row.roleId === 1 ? '超级管理员' : row.roleId === 2 ? '学院管理员' : '普通师生' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" @click="handleResetPwd(row.id)">重置密码</el-button>
          <el-popconfirm title="确认删除？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="search.current" v-model:page-size="search.size"
      :total="total" layout="total, prev, pager, next" style="margin-top: 16px; justify-content: flex-end"
      @change="fetchData"
    />

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="500px">
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="密码" required>
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="form.deptId" style="width: 100%">
            <el-option label="计算机学院" :value="1" />
            <el-option label="电子信息学院" :value="2" />
            <el-option label="机械学院" :value="3" />
            <el-option label="经管学院" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleId" style="width: 100%">
            <el-option label="超级管理员" :value="1" />
            <el-option label="学院资产管理员" :value="2" />
            <el-option label="普通师生" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserPage, addUser, updateUser, deleteUser, resetPassword } from '@/api/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const formRef = ref()

const stats = reactive({ total: 0, adminCount: 0, deptAdminCount: 0, userCount: 0 })

const search = reactive({ current: 1, size: 10, keyword: '', deptId: null })

const form = reactive<any>({
  id: null, username: '', password: '123456', realName: '', phone: '', email: '',
  deptId: null, roleId: 3, status: 1
})

async function fetchStats() {
  try {
    const res = await getUserPage({ size: 1000 })
    const records = res.data.records || []
    stats.total = res.data.total
    stats.adminCount = records.filter((u: any) => u.roleId === 1).length
    stats.deptAdminCount = records.filter((u: any) => u.roleId === 2).length
    stats.userCount = records.filter((u: any) => u.roleId === 3).length
  } catch { /* stats are non-critical */ }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getUserPage(search)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openDialog(row?: any) {
  if (row) {
    dialogTitle.value = '编辑用户'
    Object.assign(form, row)
  } else {
    dialogTitle.value = '新增用户'
    Object.assign(form, {
      id: null, username: '', password: '123456', realName: '', phone: '', email: '',
      deptId: null, roleId: 3, status: 1
    })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (form.id) {
    await updateUser(form)
    ElMessage.success('用户更新成功')
  } else {
    await addUser(form)
    ElMessage.success('用户创建成功')
  }
  dialogVisible.value = false
  fetchData()
}

async function handleDelete(id: number) {
  await deleteUser(id)
  ElMessage.success('删除成功')
  fetchData()
}

async function handleResetPwd(id: number) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新密码', '重置密码', {
      confirmButtonText: '确定',
      inputType: 'password'
    })
    if (value) {
      await resetPassword(id, value)
      ElMessage.success('密码重置成功')
    }
  } catch { /* 用户取消 */ }
}

onMounted(() => {
  fetchData()
  fetchStats()
})
</script>
