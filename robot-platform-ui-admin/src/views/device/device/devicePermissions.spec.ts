import { describe, expect, it } from 'vitest'
import { canMaintainInventory } from './devicePermissions'

describe('device inventory permission boundary', () => {
  it('does not expose inventory creation or editing to a tenant administrator', () => {
    expect(canMaintainInventory(['tenant_admin'])).toBe(false)
  })

  it('allows the platform administrator to maintain inventory', () => {
    expect(canMaintainInventory(['super_admin'])).toBe(true)
  })
})
