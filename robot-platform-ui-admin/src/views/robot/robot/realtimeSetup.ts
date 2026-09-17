import type { RobotRealtimeEvent, useRobotRealtimeStore } from '@/store/modules/robotRealtime'

type RobotRealtimeStore = ReturnType<typeof useRobotRealtimeStore>

/**
 * The login tenant cache is the only browser-side tenant authority available to
 * this page. Do not coerce strings or arbitrary cache values: accepting one would
 * weaken the tenant fence before an untrusted WebSocket event reaches the store.
 */
export const setupRobotRealtimeTenant = (store: RobotRealtimeStore, cachedTenantId: unknown) => {
  const tenantId =
    typeof cachedTenantId === 'number' && Number.isSafeInteger(cachedTenantId) && cachedTenantId > 0
      ? cachedTenantId
      : undefined
  store.setTenant(tenantId)
  return tenantId
}

/** A page-level adapter keeps only valid tenant events on the live robot projection. */
export const applyRobotRealtimeEvent = (store: RobotRealtimeStore, event: RobotRealtimeEvent) => {
  store.apply(event)
}
