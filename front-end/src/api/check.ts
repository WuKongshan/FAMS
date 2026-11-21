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

export function analyzeDiff(taskId: number) {
  return request.post(`/check/analyze/${taskId}`)
}

export function confirmDiff(taskId: number, recordIds: number[]) {
  return request.post(`/check/confirm/${taskId}`, { recordIds })
}
