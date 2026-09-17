<template>
  <ContentWrap>
    <div class="mb-16px flex items-center justify-between">
      <div>
        <h2 class="m-0">机器人运营概览</h2>
        <p class="mb-0 text-14px text-gray-500">所有统计仅包含当前租户的真实机器人和任务。</p>
      </div>
      <el-button :loading="loading" @click="load"
        ><Icon icon="ep:refresh" class="mr-5px" />刷新</el-button
      >
    </div>

    <el-alert
      v-if="errorMessage"
      :closable="false"
      :title="errorMessage"
      type="error"
      show-icon
      class="mb-16px"
    />
    <el-skeleton :loading="loading" animated>
      <el-row :gutter="16">
        <el-col
          v-for="item in summaryCards"
          :key="item.label"
          :lg="6"
          :md="12"
          :sm="12"
          :xs="24"
          class="mb-16px"
        >
          <el-card shadow="never">
            <div class="text-14px text-gray-500">{{ item.label }}</div>
            <div class="mt-8px text-28px font-bold">{{ item.value }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-alert
        v-if="alarmText"
        :closable="false"
        :title="alarmText"
        type="info"
        show-icon
        class="mb-16px"
      />
      <el-empty v-if="hasNoRobotData(dashboard)" description="当前租户暂无机器人数据" />

      <template v-else>
        <el-row :gutter="16">
          <el-col :lg="14" :md="24" :xs="24" class="mb-16px">
            <el-card shadow="never">
              <template #header>机器人实时状态</template>
              <el-table :data="dashboard?.robots || []" empty-text="暂无状态记录" stripe>
                <el-table-column label="编号" prop="robotCode" min-width="130" />
                <el-table-column label="名称" prop="name" min-width="120" />
                <el-table-column label="在线状态" prop="onlineStatus" min-width="100" />
                <el-table-column label="工作状态" prop="workStatus" min-width="100" />
                <el-table-column label="电量" min-width="80">
                  <template #default="{ row }">{{ row.batteryLevel ?? '-' }}%</template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-col>
          <el-col :lg="10" :md="24" :xs="24" class="mb-16px">
            <el-card shadow="never">
              <template #header>近 7 天任务趋势</template>
              <el-table
                :data="dashboard?.missionTrend || []"
                empty-text="暂无任务趋势"
                size="small"
              >
                <el-table-column label="日期" prop="date" min-width="100" />
                <el-table-column label="任务数" prop="total" min-width="80" />
                <el-table-column label="失败数" prop="failed" min-width="80" />
              </el-table>
            </el-card>
          </el-col>
        </el-row>
      </template>
    </el-skeleton>
  </ContentWrap>
</template>

<script lang="ts" setup>
import { RobotDashboardApi } from '@/api/robot/dashboard'
import { alarmSummary, createDashboardState, hasNoRobotData, onlineRate } from './dashboardView'

defineOptions({ name: 'Index' })

const { loading, dashboard, errorMessage, load } = createDashboardState(RobotDashboardApi.get)
const alarmText = computed(() => alarmSummary(dashboard.value))
const summaryCards = computed(() => [
  { label: '机器人总数', value: dashboard.value?.totalRobots ?? 0 },
  { label: '在线机器人', value: dashboard.value?.onlineRobots ?? 0 },
  { label: '今日任务', value: dashboard.value?.todayMissions ?? 0 },
  { label: '在线率', value: `${onlineRate(dashboard.value)}%` },
  { label: '今日失败任务', value: dashboard.value?.failedMissions ?? 0 }
])

onMounted(load)
</script>
