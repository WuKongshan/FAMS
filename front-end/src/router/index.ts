import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { resolveNavigation } from './guard'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', noAuth: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/RegisterView.vue'),
    meta: { title: '注册', noAuth: true }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/asset/list',
    children: [
      // 资产管理
      {
        path: '/asset/list',
        name: 'AssetList',
        component: () => import('@/views/asset/AssetList.vue'),
        meta: { title: '资产台账', icon: 'List' }
      },
      // 领用审批
      {
        path: '/approval/apply',
        name: 'ApprovalApply',
        component: () => import('@/views/approval/ApplyList.vue'),
        meta: { title: '领用/归还申请', icon: 'Document' }
      },
      {
        path: '/approval/manage',
        name: 'ApprovalManage',
        component: () => import('@/views/approval/ApprovalList.vue'),
        meta: { title: '审批管理', icon: 'Checked' }
      },
      // 协同盘点
      {
        path: '/check/task',
        name: 'CheckTask',
        component: () => import('@/views/check/CheckTask.vue'),
        meta: { title: '盘点任务', icon: 'DataAnalysis' }
      },
      {
        path: '/check/collaborate/:id',
        name: 'CollaborativeCheck',
        component: () => import('@/views/check/CollaborativeCheck.vue'),
        meta: { title: '协同盘点', icon: 'Edit' }
      },
      // 报修工单
      {
        path: '/repair/order',
        name: 'RepairOrder',
        component: () => import('@/views/repair/RepairOrder.vue'),
        meta: { title: '报修工单', icon: 'Tools' }
      },
      // 报废管理
      {
        path: '/scrap/manage',
        name: 'ScrapManage',
        component: () => import('@/views/scrap/ScrapManage.vue'),
        meta: { title: '报废管理', icon: 'Delete' }
      },
      // 数据统计
      {
        path: '/statistics',
        name: 'Statistics',
        component: () => import('@/views/statistics/Dashboard.vue'),
        meta: { title: '数据统计', icon: 'TrendCharts' }
      },
      // 用户管理（仅管理员可见）
      {
        path: '/admin/users',
        name: 'UserManage',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理', icon: 'User', role: 'ROLE_ADMIN' }
      },
      // 操作审计日志（仅管理员可见）
      {
        path: '/admin/audit',
        name: 'AuditLog',
        component: () => import('@/views/admin/AuditLog.vue'),
        meta: { title: '操作审计日志', icon: 'DocumentChecked', role: 'ROLE_ADMIN' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未登录跳登录页；管理页校验角色（菜单藏了但地址栏能直达，必须在这里拦）
router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  const redirect = resolveNavigation(
    { path: to.path, meta: to.meta as any },
    { token: userStore.token, isAdmin: userStore.isAdmin }
  )
  if (redirect) {
    next(redirect)
  } else {
    next()
  }
})

export default router
