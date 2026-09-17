import request from '@/config/axios'

/** Product responses never include any provisioning or broker credential. */
export interface DeviceProductVO {
  id?: number
  tenantId?: number | null
  productKey: string
  name: string
  status: number
  remark?: string
  createTime?: string
}

export type DeviceProductSaveReqVO = Pick<
  DeviceProductVO,
  'productKey' | 'name' | 'status' | 'remark'
> & {
  /** Platform administrators may explicitly create a globally visible product. */
  publicProduct?: boolean
}

export const DeviceProductApi = {
  list: (params?: Partial<Pick<DeviceProductVO, 'productKey' | 'name' | 'status'>>) =>
    request.get<DeviceProductVO[]>({ url: '/device/products', params }),
  create: (data: DeviceProductSaveReqVO) => request.post<number>({ url: '/device/products', data }),
  update: (id: number, data: DeviceProductSaveReqVO) =>
    request.put({ url: `/device/products/${id}`, data }),
  delete: (id: number) => request.delete({ url: `/device/products/${id}` })
}
