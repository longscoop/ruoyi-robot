import request from '@/config/axios'

export interface DashboardRobotStatusVO {
  robotId: number
  robotCode: string
  name: string
  onlineStatus: string
  workStatus: string
  batteryLevel?: number
  lastHeartbeatAt?: string
}

export interface DashboardMissionTrendVO {
  date: string
  total: number
  failed: number
}

/** All dashboard values are tenant-scoped server aggregates, never display fixtures. */
export interface RobotDashboardVO {
  totalRobots: number
  onlineRobots: number
  todayMissions: number
  failedMissions: number
  alarmFeatureEnabled: boolean
  robots: DashboardRobotStatusVO[]
  missionTrend: DashboardMissionTrendVO[]
}

export const RobotDashboardApi = {
  get: () => request.get<RobotDashboardVO>({ url: '/robot/dashboard' })
}
