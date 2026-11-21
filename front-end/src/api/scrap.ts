import request from './request'

export function getScrapPage(params: any) {
  return request.get('/scrap/page', { params })
}

export function submitScrap(data: any) {
  return request.post('/scrap/apply', data)
}

export function approveScrap(data: any) {
  return request.post('/scrap/approve', data)
}

export function getScrapDetail(id: number) {
  return request.get(`/scrap/${id}`)
}
