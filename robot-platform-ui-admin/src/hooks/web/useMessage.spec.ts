import { describe, expect, it, vi } from 'vitest'

const prompt = vi.hoisted(() => vi.fn())

vi.mock('element-plus', () => ({
  ElMessage: {},
  ElNotification: {},
  ElMessageBox: { prompt }
}))
vi.mock('./useI18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

import { useMessage } from './useMessage'

describe('useMessage.prompt', () => {
  it('forwards caller validation and initial value to Element Plus', () => {
    const pattern = /^[1-9]\d*$/
    useMessage().prompt('请输入设备 ID', '添加设备组成员', {
      inputPattern: pattern,
      inputValue: '12'
    })

    expect(prompt).toHaveBeenCalledWith(
      '请输入设备 ID',
      '添加设备组成员',
      expect.objectContaining({ inputPattern: pattern, inputValue: '12' })
    )
  })
})
