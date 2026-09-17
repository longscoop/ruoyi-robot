<template>
  <ContentWrap>
    <el-form :inline="true" class="-mb-15px"
      ><el-form-item label="编号"
        ><el-input v-model="query.robotCode" clearable @keyup.enter="load" /></el-form-item
      ><el-form-item label="名称"
        ><el-input v-model="query.name" clearable @keyup.enter="load" /></el-form-item
      ><el-form-item
        ><el-button @click="load">搜索</el-button
        ><el-button @click="reset">重置</el-button></el-form-item
      ></el-form
    >
  </ContentWrap>
  <ContentWrap
    ><el-table v-loading="loading" :data="robots" empty-text="暂无机器人" stripe
      ><el-table-column label="机器人编号" prop="robotCode" min-width="150" /><el-table-column
        label="名称"
        prop="name"
        min-width="140"
      /><el-table-column label="在线状态" min-width="100"
        ><template #default="{ row }">{{
          realtime.byRobotId[row.id]?.onlineStatus ?? row.onlineStatus
        }}</template></el-table-column
      ><el-table-column label="工作状态" min-width="110"
        ><template #default="{ row }">{{
          realtime.byRobotId[row.id]?.workStatus ?? row.workStatus
        }}</template></el-table-column
      ><el-table-column label="电量" min-width="80"
        ><template #default="{ row }"
          >{{ realtime.byRobotId[row.id]?.batteryLevel ?? row.batteryLevel ?? '-' }}%</template
        ></el-table-column
      ><el-table-column label="操作" width="180"
        ><template #default="{ row }"
          ><el-button
            v-hasPermi="['robot:robot:query']"
            link
            type="primary"
            @click="detailRef.open(row.id)"
            >详情</el-button
          ><el-button v-hasPermi="['robot:robot:update']" link type="primary" @click="rename(row)"
            >改名</el-button
          ></template
        ></el-table-column
      ></el-table
    ><Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
  /></ContentWrap>
  <RobotDetail ref="detailRef" />
</template>

<script lang="ts" setup>
import { RobotApi, type RobotVO } from '@/api/robot/robot'
import { parseRobotRealtimeEvent, useRobotRealtimeStore } from '@/store/modules/robotRealtime'
import { getRefreshToken, getTenantId } from '@/utils/auth'
import { useWebSocket } from '@vueuse/core'
import RobotDetail from './RobotDetail.vue'
import { applyRobotRealtimeEvent, setupRobotRealtimeTenant } from './realtimeSetup'

defineOptions({ name: 'RobotList' })
const loading = ref(false)
const robots = ref<RobotVO[]>([])
const total = ref(0)
const detailRef = ref()
const realtime = useRobotRealtimeStore()
const query = reactive({ pageNo: 1, pageSize: 20, robotCode: '', name: '' })
const server =
  `${import.meta.env.VITE_BASE_URL}/infra/ws`.replace('http', 'ws') + `?token=${getRefreshToken()}`
const { data: websocketData, close } = useWebSocket(server, {
  autoReconnect: true,
  heartbeat: true
})
const load = async () => {
  loading.value = true
  try {
    const page = (await RobotApi.page(query)) as { list: RobotVO[]; total: number }
    robots.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}
const reset = () => {
  query.robotCode = ''
  query.name = ''
  query.pageNo = 1
  load()
}
const rename = async (robot: RobotVO) => {
  const { value } = await useMessage().prompt('请输入机器人名称', '修改名称', {
    inputValue: robot.name
  })
  if (value?.trim()) {
    await RobotApi.update(robot.id, { name: value.trim() })
    await load()
  }
}
watch(websocketData, (raw) => {
  if (!raw || raw === 'pong') return
  try {
    const frame = JSON.parse(raw) as { type?: string; content?: string }
    if (frame.type !== 'ROBOT_STATUS_CHANGED' && frame.type !== 'MISSION_STATUS_CHANGED') return
    const event = parseRobotRealtimeEvent(JSON.parse(frame.content || '{}'))
    if (event) applyRobotRealtimeEvent(realtime, event)
  } catch {
    // Ignore malformed network frames. The server remains the source of truth on refresh.
  }
})
onMounted(() => {
  setupRobotRealtimeTenant(realtime, getTenantId())
  load()
})
onBeforeUnmount(close)
</script>
