import request from './request'

export function getDeptAssetStats() {
  return request.get('/statistics/dept-assets')
}

export function getCategoryStats() {
  return request.get('/statistics/category')
}

export function getStatusStats() {
  return request.get('/statistics/status')
}

export function getCheckDiffStats(taskId?: number) {
  return request.get('/statistics/check-diff', { params: { taskId } })
}
