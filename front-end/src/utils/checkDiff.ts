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
