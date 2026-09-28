import { describe, expect, it } from 'vitest'
import { generateRoute } from './routerHelper'

const menu = (path: string, component = '', children?: AppCustomRouteRecordRaw[]) =>
  ({
    id: path,
    name: path,
    path,
    component,
    children,
    parentId: children ? 0 : 1,
    visible: true,
    keepAlive: false,
    icon: '',
    redirect: '',
    meta: {}
  }) as AppCustomRouteRecordRaw

describe('operational menu routes', () => {
  it('omits vendor documentation links while retaining other external links', () => {
    const routes = generateRoute([
      menu('https://doc.iocoder.cn/'),
      menu('https://cloud.iocoder.cn'),
      menu('www.iocoder.cn'),
      menu('https://example.org/help')
    ])

    expect(routes).toHaveLength(1)
    expect(routes[0].meta?.link).toBe('https://example.org/help')
  })

  it('omits missing pages and directories left empty by them', () => {
    const routes = generateRoute([
      menu('/cms', '', [menu('missing', 'cms/missing/index')]),
      menu('/system', '', [
        menu('missing', 'system/missing/index'),
        menu('user', 'system/user/index')
      ])
    ])

    expect(routes.map((route) => route.path)).toEqual(['/system'])
    expect(routes[0].children?.map((route) => route.path)).toEqual(['user'])
    expect(routes[0].redirect).toBe('/system/user')
  })

  it('does not register pages from modules absent in the server', () => {
    const routes = generateRoute([
      menu('/oa', '', [menu('attendance', 'oa/attendance/list/index')]),
      menu('/ai', '', [menu('enterprise', 'ai/enterprise/index')])
    ])

    expect(routes.map((route) => route.path)).toEqual(['/ai'])
  })

  it('omits unsupported legacy member pages while retaining robot management', () => {
    const routes = generateRoute([
      menu('/member', '', [menu('user', 'member/user/index')]),
      menu('/robot-platform', '', [menu('robot', 'robot/robot/index')])
    ])

    expect(routes.map((route) => route.path)).toEqual(['/robot-platform'])
  })

  it('omits legacy AI demo pages but retains the working management pages', () => {
    const routes = generateRoute([
      menu('/ai', '', [
        menu('chat', 'ai/chat/index/index.vue'),
        menu('enterprise', 'ai/enterprise/index'),
        menu('knowledge', 'ai/knowledge/index')
      ])
    ])

    expect(routes[0].children?.map((route) => route.path)).toEqual(['enterprise', 'knowledge'])
  })

  it('registers every realtime agent management page from the AI center menu', () => {
    const pages = [
      ['agent', 'ai/agent/index'],
      ['prompt', 'ai/prompt/index'],
      ['model', 'ai/model/index'],
      ['conversation', 'ai/conversation/index'],
      ['memory', 'ai/memory/index'],
      ['realtime', 'ai/realtime/index'],
      ['digital-human', 'ai/digital-human/index']
    ]
    const routes = generateRoute([
      menu('/robot-platform', '', [menu('ai', '', pages.map(([path, component]) => menu(path, component)))])
    ])

    expect(routes[0].children?.[0].children?.map((route) => route.path)).toEqual(
      pages.map(([path]) => path)
    )
  })

  it('retains a working parent page when only its child links are missing', () => {
    const routes = generateRoute([
      menu('/system', 'system/user/index', [menu('missing', 'cms/missing/index')])
    ])

    expect(routes).toHaveLength(1)
    expect(routes[0].children).toHaveLength(1)
    expect(routes[0].children?.[0].path).toBe('')
  })
})
