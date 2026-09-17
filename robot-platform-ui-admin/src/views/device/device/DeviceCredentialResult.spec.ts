import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

describe('DeviceCredentialResult', () => {
  it('renders activation secrets only in the one-time result', () => {
    const resultSource = readFileSync(
      new URL('./DeviceCredentialResult.vue', import.meta.url),
      'utf8'
    )

    expect(resultSource).toContain('credential.mqttSecret')
    expect(resultSource).toContain('credential.httpSecret')
    expect(resultSource).toContain('凭据仅显示一次')
  })

  it('never puts credential fields in the normal device list', () => {
    const listSource = readFileSync(new URL('./index.vue', import.meta.url), 'utf8')

    expect(listSource).not.toContain('mqttSecret')
    expect(listSource).not.toContain('httpSecret')
    expect(listSource).not.toContain('mqttUsername')
  })
})
