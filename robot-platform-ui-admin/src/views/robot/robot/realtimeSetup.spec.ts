import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useRobotRealtimeStore } from '@/store/modules/robotRealtime'
import { applyRobotRealtimeEvent, setupRobotRealtimeTenant } from './realtimeSetup'

describe('robot list realtime setup', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('uses only a numeric cached tenant and applies its live event', () => {
    const store = useRobotRealtimeStore()

    expect(setupRobotRealtimeTenant(store, 10)).toBe(10)
    applyRobotRealtimeEvent(store, {
      type: 'ROBOT_STATUS_CHANGED',
      tenantId: 10,
      robotId: 7,
      occurredAt: '2026-09-16T08:00:00Z',
      data: { onlineStatus: 'ONLINE', batteryLevel: 92 }
    })

    expect(store.byRobotId[7]).toMatchObject({ onlineStatus: 'ONLINE', batteryLevel: 92 })
  })

  it('does not coerce a cache value into a tenant identity', () => {
    const store = useRobotRealtimeStore()

    expect(setupRobotRealtimeTenant(store, '10')).toBeUndefined()
    applyRobotRealtimeEvent(store, {
      type: 'ROBOT_STATUS_CHANGED',
      tenantId: 10,
      robotId: 7,
      occurredAt: '2026-09-16T08:00:00Z',
      data: { batteryLevel: 92 }
    })

    expect(store.byRobotId[7]).toBeUndefined()
  })
})
