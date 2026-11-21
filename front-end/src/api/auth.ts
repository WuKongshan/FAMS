import request from './request'

export interface LoginParams {
  username: string
  password: string
}

export interface RegisterParams {
  username: string
  password: string
  realName: string
  phone?: string
  email?: string
  deptId: number | null
  deptName: string
}

export function login(data: LoginParams) {
  return request.post('/auth/login', data)
}

export function register(data: RegisterParams) {
  return request.post('/auth/register', data)
}

export function getDepartments() {
  return request.get('/auth/departments')
}
