import request from './request'
import type { AxiosResponse } from 'axios'

export interface AuditLog {
  id: number
  module: string
  operation: string
  description: string
  requestMethod: string
  requestUrl: string
  requestParams: string
  responseResult: string
  operatorId: number
  operatorName: string
  operatorIp: string
  operationTime: string
  costTime: number
  status: number
  errorMsg: string
  createTime: string
}

export interface AuditLogPage {
  records: AuditLog[]
  total: number
  size: number
  current: number
}

export interface AuditLogQuery {
  current?: number
  size?: number
  module?: string
  operation?: string
  operatorName?: string
  status?: number
  startTime?: string
  endTime?: string
}

/** 分页查询操作审计日志 */
export function pageAuditLogs(params: AuditLogQuery): Promise<AuditLogPage> {
  return request.get('/admin/audit/page', { params }).then((res: any) => res.data)
}

/** 查询操作日志详情 */
export function getAuditLogDetail(id: number): Promise<AuditLog> {
  return request.get(`/admin/audit/${id}`).then((res: any) => res.data)
}
