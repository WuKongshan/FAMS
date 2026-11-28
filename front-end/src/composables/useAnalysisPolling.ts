/**
 * 分析任务轮询。
 *
 * 从 CollaborativeCheck.vue 抽出来的原因：这段逻辑的分支最多也最容易出错
 * （成功、失败、超时、状态不变），却是最没法靠手点覆盖的 —— 正常路径几秒就过了，
 * 超时和失败路径要人为制造。抽成纯函数后可以用假时钟把每条分支都走一遍。
 */

export type JobStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface AnalysisJobView {
  status: JobStatus
  result?: any[]
  error?: string
  degraded?: boolean
}

export interface PollOptions {
  /** 轮询间隔，默认 1500ms */
  intervalMs?: number
  /** 总等待上限，默认 200000ms（要大于后端最坏耗时） */
  timeoutMs?: number
  /** 取当前时间，测试时可注入假时钟 */
  now?: () => number
  /** 等待函数，测试时可注入空实现，避免真的 sleep */
  sleep?: (ms: number) => Promise<void>
}

const DEFAULT_INTERVAL = 1500
const DEFAULT_TIMEOUT = 200000

/**
 * 轮询直到任务进入终态，返回最后一次的任务视图。
 *
 * @throws 状态为 FAILED 时抛出后端给的原因；
 *         超过 timeoutMs 仍未结束则抛超时错误（后端进程被杀时任务会永远停在 RUNNING）
 */
export async function pollAnalysisJob(
  fetchJob: () => Promise<AnalysisJobView>,
  options: PollOptions = {}
): Promise<AnalysisJobView> {
  const intervalMs = options.intervalMs ?? DEFAULT_INTERVAL
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT
  const now = options.now ?? (() => Date.now())
  const sleep = options.sleep ?? ((ms: number) => new Promise<void>(resolve => setTimeout(resolve, ms)))

  const startedAt = now()

  while (now() - startedAt < timeoutMs) {
    await sleep(intervalMs)
    const job = await fetchJob()

    if (job.status === 'SUCCESS') {
      return job
    }
    if (job.status === 'FAILED') {
      throw new Error(job.error || '分析失败')
    }
  }

  throw new Error('分析超时，请稍后重试')
}
