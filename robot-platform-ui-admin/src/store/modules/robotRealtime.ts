import { defineStore } from 'pinia'

export interface RobotRealtimeStatus {
  onlineStatus?: string
  workStatus?: string
  batteryLevel?: number
  currentMissionId?: string
  softwareVersion?: string
  lastHeartbeatTime?: string
}

export interface RobotRealtimeEvent {
  schemaVersion?: number
  type: 'ROBOT_STATUS_CHANGED' | 'MISSION_STATUS_CHANGED'
  tenantId: number
  robotId: number
  eventId?: string
  occurredAt: string
  data: RobotRealtimeStatus
}

/**
 * WebSocket payloads are untrusted until their tenant routing fields are checked.
 * Keeping this parser next to the reducer prevents individual pages from applying
 * partial or cross-tenant payloads by accident.
 */
export const parseRobotRealtimeEvent = (value: unknown): RobotRealtimeEvent | undefined => {
  if (!value || typeof value !== 'object') return undefined
  const event = value as Record<string, unknown>
  if (
    (event.type !== 'ROBOT_STATUS_CHANGED' && event.type !== 'MISSION_STATUS_CHANGED') ||
    typeof event.tenantId !== 'number' ||
    typeof event.robotId !== 'number' ||
    typeof event.occurredAt !== 'string' ||
    !event.data ||
    typeof event.data !== 'object'
  ) {
    return undefined
  }
  return event as unknown as RobotRealtimeEvent
}

interface RobotRealtimeState {
  tenantId?: number
  byRobotId: Record<number, RobotRealtimeStatus>
  latestEventAt: Record<number, number>
  seenEventIds: Record<string, boolean>
}

/**
 * Tenant is an explicit fence: never merge a websocket event before its tenant
 * matches the page's active tenant. State is intentionally memory-only so a
 * prior tenant cannot leak through persisted Pinia storage after account switch.
 */
export const useRobotRealtimeStore = defineStore('robot-realtime', {
  state: (): RobotRealtimeState => ({ byRobotId: {}, latestEventAt: {}, seenEventIds: {} }),
  actions: {
    setTenant(tenantId?: number) {
      if (this.tenantId !== tenantId) {
        this.tenantId = tenantId
        this.byRobotId = {}
        this.latestEventAt = {}
        this.seenEventIds = {}
      }
    },
    apply(event: RobotRealtimeEvent) {
      if (this.tenantId == null || event.tenantId !== this.tenantId) return
      if (event.eventId && this.seenEventIds[event.eventId]) return
      const occurredAt = Date.parse(event.occurredAt)
      if (Number.isNaN(occurredAt) || occurredAt < (this.latestEventAt[event.robotId] || 0)) return
      this.byRobotId[event.robotId] = { ...(this.byRobotId[event.robotId] || {}), ...event.data }
      this.latestEventAt[event.robotId] = occurredAt
      if (event.eventId) this.seenEventIds[event.eventId] = true
    }
  },
  persist: false
})
