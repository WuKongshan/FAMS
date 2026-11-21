import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi } from '@/api/auth'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(Number(localStorage.getItem('userId')) || 0)
  const username = ref(localStorage.getItem('username') || '')
  const realName = ref(localStorage.getItem('realName') || '')
  const roleId = ref(Number(localStorage.getItem('roleId')) || 0)
  const deptId = ref(Number(localStorage.getItem('deptId')) || 0)
  const deptName = ref(localStorage.getItem('deptName') || '')

  const isAdmin = computed(() => roleId.value === 1)
  const isDeptAdmin = computed(() => roleId.value === 2)

  async function login(loginUsername: string, password: string) {
    const res = await loginApi({ username: loginUsername, password })
    const data = res.data
    token.value = data.token
    userId.value = data.userId
    username.value = data.username
    realName.value = data.realName
    roleId.value = data.roleId
    deptId.value = data.deptId
    deptName.value = data.deptName

    localStorage.setItem('token', data.token)
    localStorage.setItem('userId', String(data.userId))
    localStorage.setItem('username', data.username)
    localStorage.setItem('realName', data.realName)
    localStorage.setItem('roleId', String(data.roleId))
    localStorage.setItem('deptId', String(data.deptId))
    localStorage.setItem('deptName', data.deptName)

    router.push('/')
  }

  function logout() {
    token.value = ''
    localStorage.clear()
    router.push('/login')
  }

  return { token, userId, username, realName, roleId, deptId, deptName, isAdmin, isDeptAdmin, login, logout }
})
