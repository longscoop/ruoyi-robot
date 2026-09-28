import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { canMaintainInventory } from './devicePermissions'

describe('device inventory permission boundary', () => {
  it('does not expose inventory creation or editing to a tenant administrator', () => {
    expect(canMaintainInventory(['tenant_admin'])).toBe(false)
  })

  it('allows the platform administrator to maintain inventory', () => {
    expect(canMaintainInventory(['super_admin'])).toBe(true)
  })

  it('loads platform inventory into the device list for the platform administrator', () => {
    const source = readFileSync(new URL('./index.vue', import.meta.url), 'utf8')

    expect(source).toContain('if (isPlatformAdmin.value)')
    expect(source).toContain('DeviceApi.inventory()')
    expect(source).toContain('DeviceApi.list()')
    expect(source).toContain('.sort((a, b) => (b.id ?? 0) - (a.id ?? 0))')
  })
})
