import request from '@/config/axios'

/** Deliberately excludes password, secret, hash and ciphertext fields. */
export interface DeviceVO {
  id?: number
  tenantId?: number | null
  productId: number
  robotId?: number | null
  deviceSn: string
  name: string
  lifecycleStatus: string
  credentialVersion?: number
  createTime?: string
  activateTime?: string
  lastBindTime?: string
}

/** Returned by activation/rotation only; callers must not persist it. */
export interface DeviceCredentialResultVO {
  deviceId: number
  robotId: number
  mqttUsername: string
  mqttSecret: string
  httpSecret: string
  credentialVersion: number
}

export interface DeviceActivateReqVO {
  robotCode: string
  robotName: string
}

export const DeviceApi = {
  inventory: () => request.get<DeviceVO[]>({ url: '/device/devices/inventory' }),
  list: () => request.get<DeviceVO[]>({ url: '/device/devices' }),
  get: (id: number) => request.get<DeviceVO>({ url: `/device/devices/${id}` }),
  create: (data: Pick<DeviceVO, 'productId' | 'deviceSn' | 'name'>) =>
    request.post<number>({ url: '/device/devices', data }),
  updateInventory: (id: number, data: Pick<DeviceVO, 'productId' | 'deviceSn' | 'name'>) =>
    request.put({ url: `/device/devices/${id}/inventory`, data }),
  deleteInventory: (id: number) => request.delete({ url: `/device/devices/${id}/inventory` }),
  activate: (id: number, data: DeviceActivateReqVO) =>
    request.post<DeviceCredentialResultVO>({ url: `/device/devices/${id}/activate`, data }),
  unbind: (id: number) => request.post({ url: `/device/devices/${id}/unbind` }),
  rotateCredentials: (id: number) =>
    request.post<DeviceCredentialResultVO>({ url: `/device/devices/${id}/credentials/rotate` }),
  updateLifecycle: (id: number, lifecycle: string) =>
    request.put({ url: `/device/devices/${id}/lifecycle/${lifecycle}` })
}
