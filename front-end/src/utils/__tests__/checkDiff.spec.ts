import { describe, expect, it } from 'vitest'
import { formatDiff, formatTime, handleTypeText } from '../checkDiff'

describe('handleTypeText', () => {
  it('已知处置方式转中文', () => {
    expect(handleTypeText('SCRAP')).toBe('转报废单')
    expect(handleTypeText('ASSET_ADD')).toBe('补登记')
    expect(handleTypeText('MANUAL')).toBe('人工确认')
  })

  it('未知值原样返回，便于发现后端新增了方式而前端没跟上', () => {
    expect(handleTypeText('TRANSFER')).toBe('TRANSFER')
  })

  it('空值给占位符', () => {
    expect(handleTypeText(null)).toBe('-')
    expect(handleTypeText(undefined)).toBe('-')
    expect(handleTypeText('')).toBe('-')
  })
})

describe('formatTime', () => {
  it('ISO 时间截断到分钟', () => {
    expect(formatTime('2026-11-28T17:13:45')).toBe('2026-11-28 17:13')
  })

  it('带毫秒/Z 也能处理', () => {
    expect(formatTime('2026-11-28T17:13:45.123Z')).toBe('2026-11-28 17:13')
  })

  it('空值给占位符', () => {
    expect(formatTime(null)).toBe('-')
    expect(formatTime(undefined)).toBe('-')
    expect(formatTime('')).toBe('-')
  })
})

describe('formatDiff', () => {
  it('盘盈带正号，盘亏带负号', () => {
    expect(formatDiff(3)).toBe('+3')
    expect(formatDiff(-2)).toBe('-2')
  })

  it('零和空值', () => {
    expect(formatDiff(0)).toBe('0')
    expect(formatDiff(null)).toBe('-')
    expect(formatDiff(undefined)).toBe('-')
  })
})
