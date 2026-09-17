import { describe, expect, it } from 'vitest'
import { canCancelMission } from './missionPermissions'

describe('mission cancellation permission', () => {
  it('disables cancellation when the permission is absent', () => {
    expect(canCancelMission('RUNNING', new Set())).toBe(false)
  })

  it('does not permit cancellation of a terminal mission', () => {
    expect(canCancelMission('SUCCESS', new Set(['robot:mission:cancel']))).toBe(false)
  })
})
