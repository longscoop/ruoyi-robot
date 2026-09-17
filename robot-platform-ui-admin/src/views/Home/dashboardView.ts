import type { RobotDashboardVO } from '@/api/robot/dashboard'
import { ref } from 'vue'

type DashboardLoader = () => Promise<RobotDashboardVO>

/**
 * Keeps asynchronous API state out of the page template so success, empty and failure states are
 * testable without rendering a dashboard fixture. The loader is injected by the page at runtime.
 */
export const createDashboardState = (loadDashboard: DashboardLoader) => {
  const loading = ref(true)
  const dashboard = ref<RobotDashboardVO>()
  const errorMessage = ref('')

  const load = async () => {
    loading.value = true
    errorMessage.value = ''
    try {
      dashboard.value = await loadDashboard()
    } catch {
      // Keep prior real data visible while clearly exposing a recoverable refresh failure.
      errorMessage.value = '仪表盘数据加载失败，请检查网络后重试。'
    } finally {
      loading.value = false
    }
  }

  return { loading, dashboard, errorMessage, load }
}

export const onlineRate = (dashboard?: RobotDashboardVO) => {
  if (!dashboard?.totalRobots) return 0
  return Math.round((dashboard.onlineRobots / dashboard.totalRobots) * 100)
}

export const hasNoRobotData = (dashboard?: RobotDashboardVO) =>
  !dashboard || dashboard.totalRobots === 0

export const alarmSummary = (dashboard?: RobotDashboardVO) =>
  dashboard?.alarmFeatureEnabled ? '' : '告警功能未启用'
