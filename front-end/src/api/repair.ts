import request from './request'

export function getRepairPage(params: any) {
  return request.get('/repair/order/page', { params })
}

export function submitRepair(data: any) {
  return request.post('/repair/order', data)
}

export function updateRepair(data: any) {
  return request.put('/repair/order', data)
}

export function getRepairDetail(id: number) {
  return request.get(`/repair/order/${id}`)
}
