<template>
  <div class="login-container">
    <!-- 背景光斑：给玻璃面板提供折射层次 -->
    <span class="glow glow-a"></span>
    <span class="glow glow-b"></span>
    <span class="grain"></span>

    <div class="login-card">
      <div class="brand">
        <span class="brand-mark">F</span>
        <h1>高校固定资产协同管理系统</h1>
        <p class="brand-sub">Asset Management System</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" autocomplete="username">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          >
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button class="login-btn" :loading="loading" @click="handleLogin">登 录</el-button>
        </el-form-item>
      </el-form>

      <p class="tip">没有账号？<router-link to="/register">立即注册</router-link></p>
    </div>

    <p class="footer">© 高校固定资产协同管理系统</p>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const loading = ref(false)

// 不预填任何账号密码，避免把凭据写进页面
const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    ElMessage.success('登录成功')
  } catch (e: any) {
    // 错误提示已由 request.ts 响应拦截器统一处理，此处仅记录日志
    console.error('登录失败:', e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ============ 背景：深色渐变 + 两团光斑 ============ */
.login-container {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(1200px 600px at 15% 10%, #14243d 0%, rgba(20, 36, 61, 0) 60%),
    radial-gradient(900px 500px at 85% 90%, #102033 0%, rgba(16, 32, 51, 0) 65%),
    #0b0f16;
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  pointer-events: none;
}

.glow-a {
  width: 620px;
  height: 620px;
  top: -220px;
  left: -140px;
  background: #1f6feb;
  opacity: 0.45;
}

.glow-b {
  width: 520px;
  height: 520px;
  right: -160px;
  bottom: -200px;
  background: #2f81f7;
  opacity: 0.3;
}

/* 极淡的噪点，压住渐变的色带 */
.grain {
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.035;
  background-image: radial-gradient(rgba(255, 255, 255, 0.8) 0.5px, transparent 0.5px);
  background-size: 3px 3px;
}

/* ============ 玻璃面板 ============ */
.login-card {
  position: relative;
  z-index: 1;
  width: 400px;
  padding: 40px 36px 28px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.16);
  box-shadow:
    0 24px 64px rgba(0, 0, 0, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(26px) saturate(150%);
  -webkit-backdrop-filter: blur(26px) saturate(150%);
}

/* ============ 品牌区 ============ */
.brand {
  text-align: center;
  margin-bottom: 26px;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  margin-bottom: 14px;
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.92);
  color: #0b0f16;
  font-size: 20px;
  font-weight: 700;
}

.brand h1 {
  font-size: 17px;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 0.3px;
}

.brand-sub {
  margin-top: 6px;
  font-size: 10px;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.38);
}

/* ============ 输入框：透明玻璃质感 ============ */
.login-card :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-card :deep(.el-input__wrapper) {
  height: 44px;
  padding: 0 12px;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.06);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.15);
  transition: box-shadow 0.18s ease, background 0.18s ease;
}

.login-card :deep(.el-input__wrapper:hover) {
  background: rgba(255, 255, 255, 0.09);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.28);
}

.login-card :deep(.el-input__wrapper.is-focus) {
  background: rgba(255, 255, 255, 0.1);
  box-shadow: inset 0 0 0 1px #4f9dff, 0 0 0 3px rgba(79, 157, 255, 0.18);
}

.login-card :deep(.el-input__inner) {
  color: #ffffff;
  font-size: 14px;
}

.login-card :deep(.el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.38);
}

.login-card :deep(.el-input__prefix),
.login-card :deep(.el-input__suffix) {
  color: rgba(255, 255, 255, 0.45);
}

/* 浏览器自动填充会把输入框刷成白底，这里强制保持透明 */
.login-card :deep(.el-input__inner:-webkit-autofill),
.login-card :deep(.el-input__inner:-webkit-autofill:hover),
.login-card :deep(.el-input__inner:-webkit-autofill:focus) {
  -webkit-text-fill-color: #ffffff;
  caret-color: #ffffff;
  transition: background-color 99999s ease-in-out 0s;
}

.login-card :deep(.el-form-item__error) {
  color: #ff9b9b;
  padding-top: 4px;
}

/* ============ 登录按钮 ============ */
.login-btn {
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 9px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 6px;
  text-indent: 6px; /* 抵消 letter-spacing 造成的视觉偏移 */
  color: #ffffff;
  background: linear-gradient(135deg, #2f81f7 0%, #0969da 100%);
  box-shadow: 0 10px 24px rgba(9, 105, 218, 0.35);
  transition: transform 0.16s ease, box-shadow 0.16s ease, filter 0.16s ease;
}

.login-btn:hover {
  filter: brightness(1.08);
  box-shadow: 0 12px 28px rgba(9, 105, 218, 0.45);
  transform: translateY(-1px);
}

.login-btn:active {
  transform: translateY(0);
}

/* ============ 底部 ============ */
.tip {
  margin-top: 6px;
  text-align: center;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
}

.tip a {
  color: #79b8ff;
  text-decoration: none;
}

.tip a:hover {
  text-decoration: underline;
}

.footer {
  position: absolute;
  bottom: 20px;
  z-index: 1;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.26);
  letter-spacing: 0.5px;
}
</style>
