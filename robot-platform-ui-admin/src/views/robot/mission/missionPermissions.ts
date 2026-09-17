/** Terminal missions must not show a cancellation action, even to an authorised user. */
const TERMINAL_STATUSES = new Set(['SUCCESS', 'FAILED', 'CANCELLED'])

export const canCancelMission = (status: string, permissions: ReadonlySet<string>) =>
  !TERMINAL_STATUSES.has(status) &&
  (permissions.has('*:*:*') || permissions.has('robot:mission:cancel'))
