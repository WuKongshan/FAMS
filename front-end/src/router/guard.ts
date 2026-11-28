/**
 * 路由守卫的判定逻辑。
 *
 * 抽成纯函数是为了可测：守卫里一旦出现"菜单藏了、路由没拦"这类不对称，
 * 用户直接在地址栏敲路径就能绕过 —— 而这类问题靠手点很难发现。
 *
 * 注意：前端守卫只是体验层，真正的权限必须在后端校验。
 * 这里拦住的价值是"普通用户不会看到管理页面的骨架和已加载的数据"。
 */

export interface NavTarget {
  path: string
  meta: {
    /** 免登录页（登录、注册） */
    noAuth?: boolean
    /** 该页要求的角色，如 ROLE_ADMIN */
    role?: string
  }
}

export interface NavUser {
  token: string
  isAdmin: boolean
}

/** 角色字符串与实际身份是否匹配。目前只有 ROLE_ADMIN 一种受限页。 */
export function hasRole(user: NavUser, role: string): boolean {
  if (role === 'ROLE_ADMIN') return user.isAdmin
  // 未知角色按拒绝处理：宁可拦错，也不要放过没实现校验的页面
  return false
}

/**
 * 返回需要跳转到的路径；返回 undefined 表示放行。
 */
export function resolveNavigation(target: NavTarget, user: NavUser): string | undefined {
  // 1. 需要登录但没 token → 去登录页
  if (!target.meta.noAuth && !user.token) {
    return '/login'
  }
  // 2. 已登录还想去登录页 → 回首页
  if (target.path === '/login' && user.token) {
    return '/'
  }
  // 3. 页面声明了角色要求 → 校验，不通过回首页
  if (target.meta.role && !hasRole(user, target.meta.role)) {
    return '/'
  }
  return undefined
}
