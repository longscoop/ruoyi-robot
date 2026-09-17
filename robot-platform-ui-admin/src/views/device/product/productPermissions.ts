import type { DeviceProductVO } from '@/api/device/product'

/** Public product records are platform-owned; tenant admins may only edit their own scope. */
export const canMaintainProduct = (product: DeviceProductVO, roles: readonly string[]) =>
  product.tenantId != null || roles.includes('super_admin')
