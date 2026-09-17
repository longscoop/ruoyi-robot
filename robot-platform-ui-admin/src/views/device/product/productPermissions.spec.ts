import { describe, expect, it } from 'vitest'
import { canMaintainProduct } from './productPermissions'

describe('product permission boundary', () => {
  it('does not allow a tenant administrator to edit a public product', () => {
    expect(
      canMaintainProduct({ productKey: 'PUBLIC', name: '公共型号', status: 0, tenantId: null }, [
        'tenant_admin'
      ])
    ).toBe(false)
  })
})
