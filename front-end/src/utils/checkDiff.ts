/** 盘点差异处置的展示辅助函数（从 CollaborativeCheck.vue 抽出，便于测试与复用）。 */

export const HANDLE_TYPE_TEXT: Record<string, string> = {
  SCRAP: '转报废单',
  ASSET_ADD: '补登记',
  MANUAL: '人工确认'
}

/** 处置方式的中文名；未知值原样返回，空值给占位 '- ' */
export function handleTypeText(type?: string | null): string {
  if (!type) return '-'
  return HANDLE_TYPE_TEXT[type] || type
}

/** 后端返回的 ISO 时间（如 2026-11-28T17:13:00）格式化为 'YYYY-MM-DD HH:mm' */
export function formatTime(value?: string | null): string {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

/** 差异数量的符号化展示：盘盈带 +，盘亏带 - */
export function formatDiff(diff?: number | null): string {
  if (diff === null || diff === undefined) return '-'
  return diff > 0 ? `+${diff}` : String(diff)
}

/** 「去处理」的去向：跳转到某个页面，或者只提示一句话 */
export type HandleTarget =
  | { kind: 'route'; path: string; query: Record<string, string> }
  | { kind: 'message'; level: 'warning' | 'info'; text: string }

/**
 * 根据处置方式与资产信息，决定「去处理」应该跳到哪。
 *
 * 之所以不无脑跳转：盘亏/盘盈都要落到真实单据上，而 check_record 允许
 * asset_id 为空（手工录入、没匹配到台账），这种记录没有资产编号可带，
 * 跳过去只会让人在目标页再找一遍，不如直接说清楚。
 */
export function buildHandleTarget(
  handleType: string | undefined,
  row: { assetCode?: string | null; assetName?: string | null }
): HandleTarget {
  if (!handleType) {
    return { kind: 'message', level: 'warning', text: '请先选择处置方式' }
  }
  if (handleType === 'MANUAL') {
    return { kind: 'message', level: 'info', text: '人工确认不需要跳转，核实后标记已处置即可' }
  }
  if (!row.assetCode) {
    return {
      kind: 'message',
      level: 'warning',
      text: '该记录没有资产编号（手工录入、未匹配台账），无法跳转，请人工核实'
    }
  }
  if (handleType === 'SCRAP') {
    return { kind: 'route', path: '/scrap/manage', query: { assetCode: row.assetCode } }
  }
  return {
    kind: 'route',
    path: '/asset/list',
    query: { create: '1', assetCode: row.assetCode, assetName: row.assetName || '' }
  }
}
