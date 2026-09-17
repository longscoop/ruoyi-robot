import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { parseRobotRealtimeEvent, useRobotRealtimeStore } from './robotRealtime'

describe('robot realtime store', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('ignores robot events from a different tenant', () => {
    const store = useRobotRealtimeStore()
    store.setTenant(10)

    store.apply({
      type: 'ROBOT_STATUS_CHANGED',
      tenantId: 20,
      robotId: 1,
      occurredAt: '2026-09-15T08:00:00Z',
      data: { batteryLevel: 9 }
    })

    expect(store.byRobotId[1]).toBeUndefined()
  })

  it('keeps the newest status event for the active tenant', () => {
    const store = useRobotRealtimeStore()
    store.setTenant(10)
    store.apply({
      type: 'ROBOT_STATUS_CHANGED',
      tenantId: 10,
      robotId: 1,
      occurredAt: '2026-09-15T08:01:00Z',
      data: { onlineStatus: 'ONLINE', batteryLevel: 86 }
    })
    store.apply({
      type: 'ROBOT_STATUS_CHANGED',
      tenantId: 10,
      robotId: 1,
      occurredAt: '2026-09-15T08:00:00Z',
      data: { onlineStatus: 'OFFLINE', batteryLevel: 1 }
    })

    expect(store.byRobotId[1]).toMatchObject({ onlineStatus: 'ONLINE', batteryLevel: 86 })
  })

  it('rejects a malformed websocket payload before it reaches the reducer', () => {
    expect(parseRobotRealtimeEvent({ type: 'ROBOT_STATUS_CHANGED', tenantId: 10 })).toBeUndefined()
  })
})
