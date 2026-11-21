import request from './request'

// ==================== 用户管理 ====================

export function getUserPage(params: any) {
  return request.get('/admin/user/page', { params })
}

// ==================== 公共查询（下拉列表） ====================

/** 获取全部部门列表（用于下拉选择） */
export function getDepartments() {
  return request.get('/auth/departments')
}

/** 获取全部用户列表（用于下拉选择） */
export function getUsers() {
  return request.get('/auth/users')
}

export function addUser(data: any) {
  return request.post('/admin/user', data)
}

export function updateUser(data: any) {
  return request.put('/admin/user', data)
}

export function deleteUser(id: number) {
  return request.delete(`/admin/user/${id}`)
}

export function resetPassword(id: number, password: string) {
  return request.put(`/admin/user/${id}/reset-password`, { password })
}
