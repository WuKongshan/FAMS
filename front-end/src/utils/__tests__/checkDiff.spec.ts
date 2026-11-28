import { describe, expect, it } from 'vitest'
import { buildHandleTarget, formatDiff, formatTime, handleTypeText } from '../checkDiff'

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

describe('buildHandleTarget - 跳转去向', () => {
  const row = { assetCode: 'ZC-001', assetName: '联想笔记本' }

  it('转报废：跳报废管理并带上资产编号（对方页面据此预填并反查资产）', () => {
    const t = buildHandleTarget('SCRAP', row)
    expect(t).toEqual({ kind: 'route', path: '/scrap/manage', query: { assetCode: 'ZC-001' } })
  })

  it('补登记：跳资产台账并带新增标记与资产信息', () => {
    const t = buildHandleTarget('ASSET_ADD', row)
    expect(t).toEqual({
      kind: 'route',
      path: '/asset/list',
      query: { create: '1', assetCode: 'ZC-001', assetName: '联想笔记本' }
    })
  })

  it('补登记但资产名为空：不产生 undefined 参数', () => {
    const t = buildHandleTarget('ASSET_ADD', { assetCode: 'ZC-001', assetName: null })
    expect(t).toEqual({
      kind: 'route',
      path: '/asset/list',
      query: { create: '1', assetCode: 'ZC-001', assetName: '' }
    })
  })
})

describe('buildHandleTarget - 不跳转的情形', () => {
  it('没选处置方式：提示而非跳转', () => {
    const t = buildHandleTarget(undefined, { assetCode: 'ZC-001' })
    expect(t).toMatchObject({ kind: 'message', level: 'warning' })
    expect((t as any).text).toContain('请先选择处置方式')
  })

  it('人工确认：不需要跳转', () => {
    const t = buildHandleTarget('MANUAL', { assetCode: 'ZC-001' })
    expect(t).toMatchObject({ kind: 'message', level: 'info' })
  })

  it('没有资产编号（手工录入、未匹配台账）：不跳转，明确告知原因', () => {
    // check_record.asset_id 可空，这类记录跳过去也只会让人在目标页再找一遍
    const t = buildHandleTarget('SCRAP', { assetCode: null })
    expect(t).toMatchObject({ kind: 'message', level: 'warning' })
    expect((t as any).text).toContain('没有资产编号')
  })
})
