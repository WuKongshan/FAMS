<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '232px'" class="aside">
      <div class="logo">
        <span class="logo-mark">F</span>
        <span v-if="!isCollapse" class="logo-text">固定资产管理系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        router
        background-color="#f6f8fa"
        text-color="#1f2328"
        active-text-color="#0969da"
      >
        <el-menu-item index="/asset/list">
          <el-icon><List /></el-icon>
          <span>资产台账</span>
        </el-menu-item>
        <el-sub-menu index="approval">
          <template #title>
            <el-icon><Document /></el-icon>
            <span>领用/归还审批</span>
          </template>
          <el-menu-item index="/approval/apply">我的申请</el-menu-item>
          <el-menu-item index="/approval/manage">审批管理</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="check-group">
          <template #title>
            <el-icon><DataAnalysis /></el-icon>
            <span>协同盘点</span>
          </template>
          <el-menu-item index="/check/task">盘点任务</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/repair/order">
          <el-icon><Tools /></el-icon>
          <span>报修工单</span>
        </el-menu-item>
        <el-menu-item index="/scrap/manage">
          <el-icon><Delete /></el-icon>
          <span>报废管理</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.isAdmin" index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.isAdmin" index="/admin/audit">
          <el-icon><DocumentChecked /></el-icon>
          <span>操作审计</span>
        </el-menu-item>
        <el-menu-item index="/statistics">
          <el-icon><TrendCharts /></el-icon>
          <span>数据统计</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-button @click="isCollapse = !isCollapse" text>
            <el-icon><component :is="isCollapse ? 'Expand' : 'Fold'" /></el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentTitle">{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <span class="user-info">{{ userStore.realName }} ({{ userStore.deptName }})</span>
          <el-dropdown>
            <el-avatar :size="32" style="cursor: pointer; margin-left: 8px;">
              {{ userStore.realName?.charAt(0) }}
            </el-avatar>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="userStore.logout()">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const route = useRoute()
const isCollapse = ref(false)

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta.title as string)
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

/* ---- 侧边栏：浅色 + 细边框（GitHub 风格） ---- */
.aside {
  background-color: #f6f8fa;
  border-right: 1px solid #d0d7de;
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.2s ease;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  border-bottom: 1px solid #d0d7de;
  overflow: hidden;
  white-space: nowrap;
}

.logo-mark {
  flex: 0 0 24px;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: #1f2328;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-text {
  font-size: 14px;
  font-weight: 600;
  color: #1f2328;
  letter-spacing: 0.2px;
}

/* ---- 顶栏 ---- */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  background: #ffffff;
  border-bottom: 1px solid #d0d7de;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.user-info {
  font-size: 13px;
  color: #656d76;
}

/* ---- 主内容区 ---- */
.main-content {
  background: #f6f8fa;
  padding: 0;
  overflow: hidden;
}
</style>
