import request from '@/config/axios'

export interface DeviceGroupVO {
  id?: number
  name: string
  remark?: string
  createTime?: string
}

export const DeviceGroupApi = {
  list: () => request.get<DeviceGroupVO[]>({ url: '/device/groups' }),
  create: (data: Omit<DeviceGroupVO, 'id' | 'createTime'>) =>
    request.post<number>({ url: '/device/groups', data }),
  update: (id: number, data: Omit<DeviceGroupVO, 'id' | 'createTime'>) =>
    request.put({ url: `/device/groups/${id}`, data }),
  delete: (id: number) => request.delete({ url: `/device/groups/${id}` }),
  addDevice: (id: number, deviceId: number) =>
    request.post({ url: `/device/groups/${id}/devices/${deviceId}` }),
  removeDevice: (id: number, deviceId: number) =>
    request.delete({ url: `/device/groups/${id}/devices/${deviceId}` })
}
