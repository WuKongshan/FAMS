import { describe, expect, it, vi } from 'vitest'
import { pollAnalysisJob, type AnalysisJobView } from '../useAnalysisPolling'

/** 假时钟：sleep 时直接推进时间，不真的等待 */
function fakeClock() {
  let t = 0
  return {
    now: () => t,
    sleep: async (ms: number) => {
      t += ms
    },
    elapsed: () => t
  }
}

const success: AnalysisJobView = { status: 'SUCCESS', result: [{ recordId: 1 }], degraded: false }

describe('pollAnalysisJob - 正常结束', () => {
  it('第一次轮询就 SUCCESS：直接返回该结果', async () => {
    const clock = fakeClock()
    const fetchJob = vi.fn(async () => success)

    const job = await pollAnalysisJob(fetchJob, { now: clock.now, sleep: clock.sleep, intervalMs: 100 })

    expect(job).toBe(success)
    expect(fetchJob).toHaveBeenCalledTimes(1)
  })

  it('先 RUNNING 后 SUCCESS：持续轮询直到终态', async () => {
    const clock = fakeClock()
    const seq: AnalysisJobView[] = [
      { status: 'PENDING' },
      { status: 'RUNNING' },
      { status: 'RUNNING' },
      success
    ]
    let i = 0
    const fetchJob = vi.fn(async () => seq[i++])

    const job = await pollAnalysisJob(fetchJob, { now: clock.now, sleep: clock.sleep, intervalMs: 100 })

    expect(job.status).toBe('SUCCESS')
    expect(fetchJob).toHaveBeenCalledTimes(4)
    expect(clock.elapsed()).toBe(400)
  })

  it('轮询间隔按参数生效', async () => {
    const clock = fakeClock()
    let i = 0
    const seq: AnalysisJobView[] = [{ status: 'RUNNING' }, success]

    await pollAnalysisJob(async () => seq[i++], { now: clock.now, sleep: clock.sleep, intervalMs: 1500 })

    expect(clock.elapsed()).toBe(3000)
  })
})

describe('pollAnalysisJob - 失败与超时', () => {
  it('FAILED：抛出后端给的原因', async () => {
    const clock = fakeClock()

    await expect(
      pollAnalysisJob(async () => ({ status: 'FAILED', error: '分析服务不可用' }), {
        now: clock.now,
        sleep: clock.sleep
      })
    ).rejects.toThrow('分析服务不可用')
  })

  it('FAILED 但没带原因：给兜底文案', async () => {
    const clock = fakeClock()

    await expect(
      pollAnalysisJob(async () => ({ status: 'FAILED' }), { now: clock.now, sleep: clock.sleep })
    ).rejects.toThrow('分析失败')
  })

  it('一直 RUNNING 到超时：抛超时错误，且轮询次数与超时/间隔相符', async () => {
    const clock = fakeClock()
    const fetchJob = vi.fn(async () => ({ status: 'RUNNING' as const }))

    await expect(
      pollAnalysisJob(fetchJob, {
        now: clock.now,
        sleep: clock.sleep,
        intervalMs: 1000,
        timeoutMs: 5000
      })
    ).rejects.toThrow('分析超时，请稍后重试')

    // 5000 / 1000 = 5 次 —— 也证明了不会无限轮询（后端进程被杀会停在 RUNNING）
    expect(fetchJob).toHaveBeenCalledTimes(5)
  })

  it('取状态本身抛异常：原样透出，不被吞掉', async () => {
    const clock = fakeClock()

    await expect(
      pollAnalysisJob(
        async () => {
          throw new Error('网络断了')
        },
        { now: clock.now, sleep: clock.sleep }
      )
    ).rejects.toThrow('网络断了')
  })
})

describe('pollAnalysisJob - 返回值', () => {
  it('降级标记随结果一起返回，供前端提示「未使用 AI」', async () => {
    const clock = fakeClock()
    const degraded: AnalysisJobView = { status: 'SUCCESS', result: [], degraded: true }

    const job = await pollAnalysisJob(async () => degraded, { now: clock.now, sleep: clock.sleep })

    expect(job.degraded).toBe(true)
  })
})
