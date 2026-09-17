import { describe, expect, it } from 'vitest'
import { alarmSummary, createDashboardState, hasNoRobotData, onlineRate } from './dashboardView'

describe('robot dashboard view state', () => {
  const dashboard = {
    totalRobots: 4,
    onlineRobots: 3,
    todayMissions: 8,
    failedMissions: 1,
    alarmFeatureEnabled: false,
    robots: [],
    missionTrend: []
  }

  it('derives statistics from the real API response properties', () => {
    expect(onlineRate(dashboard)).toBe(75)
    expect(hasNoRobotData(dashboard)).toBe(false)
  })

  it('keeps empty state separate from invented data', () => {
    expect(hasNoRobotData(undefined)).toBe(true)
    expect(alarmSummary(dashboard)).toBe('告警功能未启用')
  })

  it('exposes loading and a real API response to the page', async () => {
    let resolve!: (value: typeof dashboard) => void
    const state = createDashboardState(
      () => new Promise<typeof dashboard>((done) => (resolve = done))
    )

    const pending = state.load()
    expect(state.loading.value).toBe(true)
    resolve(dashboard)
    await pending

    expect(state.loading.value).toBe(false)
    expect(state.dashboard.value).toEqual(dashboard)
    expect(state.errorMessage.value).toBe('')
  })

  it('shows a recoverable error instead of dashboard fixture data', async () => {
    const state = createDashboardState(async () => Promise.reject(new Error('network unavailable')))

    await state.load()

    expect(state.loading.value).toBe(false)
    expect(state.dashboard.value).toBeUndefined()
    expect(state.errorMessage.value).toBe('仪表盘数据加载失败，请检查网络后重试。')
  })
})
