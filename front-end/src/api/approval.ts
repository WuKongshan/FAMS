import request from './request'

export function getApprovalPage(params: any) {
  return request.get('/approval/page', { params })
}

export function submitApply(data: any) {
  return request.post('/approval/apply', data)
}

export function approve(data: any) {
  return request.post('/approval/approve', data)
}

export function getApprovalDetail(id: number) {
  return request.get(`/approval/${id}`)
}
