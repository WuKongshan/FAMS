import request from './request'

export interface AssetQuery {
  current?: number
  size?: number
  keyword?: string
  categoryId?: number
  status?: string
  deptId?: number
}

export function getAssetPage(params: AssetQuery) {
  return request.get('/asset/page', { params })
}

export function addAsset(data: any) {
  return request.post('/asset', data)
}

export function updateAsset(data: any) {
  return request.put('/asset', data)
}

export function deleteAsset(id: number) {
  return request.delete(`/asset/${id}`)
}

export async function exportAssets(params: AssetQuery) {
  const res = await request.get('/asset/export', {
    params,
    responseType: 'blob'
  })
  // 从 Content-Disposition 头解析文件名，或使用默认名
  const disposition = res.headers?.['content-disposition']
  let fileName = '资产台账导出.xlsx'
  if (disposition) {
    const match = disposition.match(/filename\*?=(?:UTF-8'')?([^;\s]+)/)
    if (match) fileName = decodeURIComponent(match[1])
  }
  // 创建下载链接
  const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
