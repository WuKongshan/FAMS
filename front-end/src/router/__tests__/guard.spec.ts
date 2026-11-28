import { describe, expect, it } from 'vitest'
import { resolveNavigation, hasRole, type NavTarget, type NavUser } from '../guard'

const admin: NavUser = { token: 'tk', isAdmin: true }
const normal: NavUser = { token: 'tk', isAdmin: false }
const guest: NavUser = { token: '', isAdmin: false }

function target(path: string, meta: NavTarget['meta'] = {}): NavTarget {
  return { path, meta }
}

const adminPage = target('/admin/users', { role: 'ROLE_ADMIN' })
const normalPage = target('/asset')

describe('resolveNavigation - 登录态', () => {
  it('未登录访问受限页：跳登录页', () => {
    expect(resolveNavigation(normalPage, guest)).toBe('/login')
  })

  it('未登录访问免登录页（登录/注册）：放行', () => {
    expect(resolveNavigation(target('/login', { noAuth: true }), guest)).toBeUndefined()
    expect(resolveNavigation(target('/register', { noAuth: true }), guest)).toBeUndefined()
  })

  it('已登录还去登录页：回首页', () => {
    expect(resolveNavigation(target('/login', { noAuth: true }), normal)).toBe('/')
  })

  it('已登录访问普通页：放行', () => {
    expect(resolveNavigation(normalPage, normal)).toBeUndefined()
  })
})

describe('resolveNavigation - 角色校验', () => {
  // 这组是本文件存在的理由：菜单用 isAdmin 藏了管理入口，
  // 但守卫若只查 token，普通用户在地址栏直接敲 /admin/users 就能进页面。
  it('普通用户访问管理员页面：拦回首页', () => {
    expect(resolveNavigation(adminPage, normal)).toBe('/')
  })

  it('管理员访问管理员页面：放行', () => {
    expect(resolveNavigation(adminPage, admin)).toBeUndefined()
  })

  it('普通用户访问未声明角色的页面：放行', () => {
    expect(resolveNavigation(normalPage, normal)).toBeUndefined()
  })

  it('未登录访问管理员页面：优先跳登录，而不是回首页', () => {
    expect(resolveNavigation(adminPage, guest)).toBe('/login')
  })
})

describe('hasRole', () => {
  it('ROLE_ADMIN 只对管理员成立', () => {
    expect(hasRole(admin, 'ROLE_ADMIN')).toBe(true)
    expect(hasRole(normal, 'ROLE_ADMIN')).toBe(false)
  })

  it('未知角色一律拒绝（宁可拦错，也不放过没实现校验的页面）', () => {
    expect(hasRole(admin, 'ROLE_SUPER_USER')).toBe(false)
    expect(hasRole(normal, 'ROLE_DEPT_ADMIN')).toBe(false)
  })
})
