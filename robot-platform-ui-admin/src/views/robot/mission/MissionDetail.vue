<template>
  <Dialog v-model="visible" title="任务详情" width="760px"
    ><el-descriptions v-if="mission" :column="2" border
      ><el-descriptions-item label="任务号">{{ mission.missionNo }}</el-descriptions-item
      ><el-descriptions-item label="状态">{{ mission.status }}</el-descriptions-item
      ><el-descriptions-item label="任务类型">{{ mission.missionType }}</el-descriptions-item
      ><el-descriptions-item label="优先级">{{ mission.priority }}</el-descriptions-item
      ><el-descriptions-item label="开始时间">{{ mission.startedTime || '-' }}</el-descriptions-item
      ><el-descriptions-item label="结束时间">{{
        mission.finishedTime || '-'
      }}</el-descriptions-item
      ><el-descriptions-item label="错误">{{
        mission.errorMessage || '-'
      }}</el-descriptions-item></el-descriptions
    ><el-divider>动作时间线</el-divider
    ><el-timeline v-if="mission?.actions?.length"
      ><el-timeline-item
        v-for="(action, index) in mission.actions"
        :key="index"
        :timestamp="action.finishedTime || action.startedTime"
        >{{ action.actionType }} · {{ action.status || 'PENDING' }}</el-timeline-item
      ></el-timeline
    ><el-empty v-else description="暂无动作时间线" /><el-divider>事件时间线</el-divider
    ><el-timeline v-if="mission?.events?.length"
      ><el-timeline-item
        v-for="event in mission.events"
        :key="event.id"
        :timestamp="event.occurredTime"
        >{{ event.eventType }}</el-timeline-item
      ></el-timeline
    ><el-empty v-else description="暂无事件时间线"
  /></Dialog>
</template>

<script lang="ts" setup>
import { MissionApi, type MissionVO } from '@/api/robot/mission'

defineOptions({ name: 'MissionDetail' })
const visible = ref(false)
const mission = ref<MissionVO>()
const open = async (id: number) => {
  visible.value = true
  mission.value = await MissionApi.get(id)
}
defineExpose({ open })
</script>
