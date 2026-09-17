<template>
  <Dialog v-model="visible" title="机器人详情" width="760px">
    <el-descriptions v-if="robot" :column="2" border>
      <el-descriptions-item label="机器人编号">{{ robot.robotCode }}</el-descriptions-item
      ><el-descriptions-item label="名称">{{ robot.name }}</el-descriptions-item
      ><el-descriptions-item label="在线状态">{{
        status?.onlineStatus || robot.onlineStatus
      }}</el-descriptions-item
      ><el-descriptions-item label="工作状态">{{
        status?.workStatus || robot.workStatus
      }}</el-descriptions-item
      ><el-descriptions-item label="电量"
        >{{ status?.batteryLevel ?? robot.batteryLevel ?? '-' }}%</el-descriptions-item
      ><el-descriptions-item label="当前任务">{{
        status?.currentMissionId || '-'
      }}</el-descriptions-item
      ><el-descriptions-item label="软件版本">{{
        status?.softwareVersion || '-'
      }}</el-descriptions-item
      ><el-descriptions-item label="最近心跳">{{
        status?.lastHeartbeatTime || '-'
      }}</el-descriptions-item>
    </el-descriptions>
    <el-divider>能力</el-divider>
    <el-empty v-if="!loading && capabilities.length === 0" description="未配置能力" /><el-table
      v-else
      v-loading="loading"
      :data="capabilities"
      ><el-table-column label="能力编码" prop="capabilityCode" /><el-table-column
        label="配置"
        prop="configuration"
    /></el-table>
  </Dialog>
</template>

<script lang="ts" setup>
import {
  RobotApi,
  type RobotCapabilityVO,
  type RobotStatusVO,
  type RobotVO
} from '@/api/robot/robot'

defineOptions({ name: 'RobotDetail' })
const visible = ref(false)
const loading = ref(false)
const robot = ref<RobotVO>()
const status = ref<RobotStatusVO | null>()
const capabilities = ref<RobotCapabilityVO[]>([])
const open = async (id: number) => {
  visible.value = true
  loading.value = true
  try {
    const [robotValue, statusValue, capabilityValues] = await Promise.all([
      RobotApi.get(id),
      RobotApi.status(id),
      RobotApi.capabilities(id)
    ])
    robot.value = robotValue
    status.value = statusValue
    capabilities.value = capabilityValues
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>
