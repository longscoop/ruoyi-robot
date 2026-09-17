import request from '@/config/axios'

export interface MissionActionVO {
  id?: number
  sequenceNo?: number
  actionType: string
  parameters: string
  status?: string
  startedTime?: string
  finishedTime?: string
  errorCode?: string
  errorMessage?: string
}

export interface MissionVO {
  id: number
  missionNo: string
  robotId: number
  missionType: string
  source: string
  status: string
  priority: number
  requestId: string
  scheduledTime?: string
  startedTime?: string
  finishedTime?: string
  errorCode?: string
  errorMessage?: string
  actions?: MissionActionVO[]
  events?: MissionEventVO[]
}

export interface MissionEventVO {
  id?: number
  actionId?: number
  eventType: string
  fromStatus?: string
  toStatus?: string
  occurredTime: string
  payload?: string
}

export interface MissionCreateVO {
  robotId: number
  missionType: string
  requestId: string
  priority: number
  scheduledTime?: string
  actions: MissionActionVO[]
}

export const MissionApi = {
  page: (params: Record<string, unknown>) => request.get({ url: '/robot/missions', params }),
  get: (id: number) => request.get<MissionVO>({ url: `/robot/missions/${id}` }),
  create: (data: MissionCreateVO) => request.post<MissionVO>({ url: '/robot/missions', data }),
  cancel: (id: number, reason?: string) =>
    request.post({ url: `/robot/missions/${id}/cancel`, data: { reason } })
}
