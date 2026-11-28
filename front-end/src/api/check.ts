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

// 已处置差异：用于回看谁、何时、什么方式、关联哪张单
export function listHandledDiffs(taskId?: number) {
  return request.get('/check/diff/handled', { params: taskId ? { taskId } : {} })
}

// refNo 为关联单据号（报废单号 / 新资产编号），人工确认时可空
export function handleDiff(recordId: number, handleType: string, refNo?: string) {
  return request.post(`/check/diff/${recordId}/handle`, null, {
    params: refNo ? { handleType, refNo } : { handleType }
  })
}

export function revokeDiff(recordId: number) {
  return request.post(`/check/diff/${recordId}/revoke`)
}

export function confirmDiff(taskId: number, recordIds: number[]) {
  return request.post(`/check/confirm/${taskId}`, { recordIds })
}
