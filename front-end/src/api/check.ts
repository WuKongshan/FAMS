import request from './request'

export function getCheckTaskPage(params: any) {
  return request.get('/check/task/page', { params })
}

export function createCheckTask(data: any) {
  return request.post('/check/task', data)
}

export function saveCheckRecord(data: any) {
  return request.post('/check/record', data)
}

export function getCheckRecords(taskId: number) {
  return request.get(`/check/record/${taskId}`)
}

// 提交异步分析，立即返回 { jobId, status }，结果需轮询 getAnalyzeJob
export function analyzeDiff(taskId: number) {
  return request.post(`/check/analyze/${taskId}`)
}

export function getAnalyzeJob(jobId: number) {
  return request.get(`/check/analyze/job/${jobId}`)
}

// 待处置差异：只做标记，不改资产台账
export function listPendingDiffs(taskId?: number) {
  return request.get('/check/diff/pending', { params: taskId ? { taskId } : {} })
}

export function handleDiff(recordId: number, handleType: string) {
  return request.post(`/check/diff/${recordId}/handle`, null, { params: { handleType } })
}

export function confirmDiff(taskId: number, recordIds: number[]) {
  return request.post(`/check/confirm/${taskId}`, { recordIds })
}
