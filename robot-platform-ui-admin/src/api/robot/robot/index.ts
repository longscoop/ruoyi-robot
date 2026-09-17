import request from '@/config/axios'

export interface RobotVO {
  id: number
  deviceId: number
  productId: number
  robotCode: string
  name: string
  onlineStatus: string
  workStatus: string
  batteryLevel?: number
  createTime?: string
}

export interface RobotCapabilityVO {
  id: number
  robotId: number
  capabilityCode: string
  configuration: string
}

export interface RobotStatusVO {
  onlineStatus: string
  workStatus: string
  batteryLevel?: number
  currentMissionId?: string
  softwareVersion?: string
  lastHeartbeatTime?: string
}

export const RobotApi = {
  page: (params: Record<string, unknown>) => request.get({ url: '/robot/robots', params }),
  get: (id: number) => request.get<RobotVO>({ url: `/robot/robots/${id}` }),
  status: (id: number) => request.get<RobotStatusVO | null>({ url: `/robot/robots/${id}/status` }),
  update: (id: number, data: Pick<RobotVO, 'name'>) =>
    request.put({ url: `/robot/robots/${id}`, data }),
  capabilities: (id: number) =>
    request.get<RobotCapabilityVO[]>({ url: `/robot/robots/${id}/capabilities` }),
  upsertCapability: (id: number, capabilityCode: string, configuration: string) =>
    request.put({
      url: `/robot/robots/${id}/capabilities`,
      params: { capabilityCode },
      data: { configuration }
    })
}
