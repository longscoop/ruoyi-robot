import request from '@/config/axios'

export interface TenantQuotaVO {
  tenantId: number
  robotLimit: number
  robotUsed: number
}

export const TenantQuotaApi = {
  getCurrent: () => request.get<TenantQuotaVO>({ url: '/tenant/quotas' }),
  update: (tenantId: number, robotLimit: number) =>
    request.put({ url: `/tenant/quotas/${tenantId}`, data: { robotLimit } })
}
