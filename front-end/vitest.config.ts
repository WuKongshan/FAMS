import { defineConfig } from 'vitest/config'
import { resolve } from 'path'

// 只跑纯逻辑单测（守卫、轮询、格式化函数），不挂载组件，
// 所以不需要 jsdom / @vue/test-utils，environment 用 node 即可。
export default defineConfig({
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  test: {
    environment: 'node',
    include: ['src/**/*.spec.ts'],
    // 关掉文件级并行：多个 worker 同时写临时目录时，在 Windows + 受限环境下
    // 会出现 EPERM 导致部分测试文件加载失败（报错还很难看懂）。
    // 本套件只有 3 个文件、跑完不到 1 秒，串行没有任何性能代价。
    fileParallelism: false
  }
})
