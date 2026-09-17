# ruoyi-robot V1 需求与实现规格

## 1. 项目目标

项目名称：**ruoyi-robot**

项目定位：

> 基于 Spring Boot + Vue3 的开源机器人智能管理平台，面向机器人、服务机器人和 AMR，提供多租户、机器人管理、Mission、巡检、MQTT、OTA、Agent、数字人、知识库、告警和远程运维能力。

本项目基于 RuoYi-Vue-Pro 的架构思想和基础能力进行开发，内部代码统一使用 Robot Platform 命名。

仓库 README 必须明确：

> RuoYi Robot 是社区独立开源项目，并非 RuoYi 官方项目。

V1 目标不是实现完整机器人生态，而是优先完成：

**机器人管得起来、任务下得去、状态看得到、问题查得清、AI 能接入、租户能隔离。**

---

# 2. 技术基线

后端：

```text
Java 17
Spring Boot
Maven
MyBatis Plus
Mysql
Redis
Spring Security
WebSocket
EMQX / MQTT
```

前端：

```text
Vue 3
TypeScript
Element Plus
Pinia
Vite
```

基础工程优先复用现有 RuoYi-Vue-Pro 能力，包括：

```text
登录认证
RBAC
菜单权限
按钮权限
数据权限
多租户基础能力
字典
参数配置
文件管理
操作日志
登录日志
定时任务
WebSocket
```

不得为了机器人业务重复实现已有成熟基础能力。

---

# 3. 品牌和代码命名要求

项目仓库：

```text
ruoyi-robot
```

内部 Maven 模块统一使用：

```text
robot-platform-*
```

禁止新增旧品牌前缀或旧组织包名；执行 `scripts/verify-robot-platform-branding.sh` 检查受版本控制的文本和路径。

机器人业务代码建议根包：

```text
com.robot.platform
```

上游框架源码与运行时配置已统一迁移到上述命名。已有 HTTP API 路径及物理数据库表名、序列名保持兼容。

要求：

1. 新增代码使用 `robot-platform-*` 模块名和 `com.robot.platform` 根包。
2. 前端页面、Logo、标题、菜单统一使用项目品牌，不新增上游品牌展示。
3. 新增 API、数据库表和配置项使用项目命名；不得因品牌迁移改变已有 HTTP 路径、表名或序列名。
4. 不要为了品牌修改破坏框架稳定性的底层公共代码。
5. 必须保留上游项目许可证要求的版权和许可证声明。

---

# 4. Maven 模块结构

V1 控制模块数量，不允许一个业务领域拆一个 Maven Module。

目标结构：

```text
ruoyi-robot
│
├── robot-platform-dependencies
│
├── robot-platform-framework
│   ├── framework-common
│   ├── framework-web
│   ├── framework-security
│   ├── framework-database
│   ├── framework-tenant
│   ├── framework-redis
│   ├── framework-mqtt
│   └── framework-ai
│
├── robot-platform-module-system
├── robot-platform-module-tenant
├── robot-platform-module-member
├── robot-platform-module-device
├── robot-platform-module-robot
├── robot-platform-module-ai
├── robot-platform-module-operation
│
└── robot-platform-server
```

暂时不要继续拆：

```text
robot-mission
robot-inspection
robot-ota
robot-agent
robot-digital-human
```

这些作为业务包存在。

---

# 5. 后端业务包结构

机器人模块：

```text
robot-platform-module-robot
└── biz
    └── com.robot.platform.robot
        ├── robot
        ├── mission
        ├── inspection
        ├── command
        ├── alarm
        └── release
```

AI 模块：

```text
robot-platform-module-ai
└── biz
    └── com.robot.platform.ai
        ├── agent
        ├── skill
        ├── tool
        ├── prompt
        ├── model
        ├── knowledge
        ├── conversation
        └── digitalhuman
```

运维模块：

```text
robot-platform-module-operation
└── biz
    └── com.robot.platform.operation
        ├── status
        ├── service
        ├── log
        ├── diagnostic
        └── record
```

每个领域遵循现有项目 Controller / Service / DAL / DO / VO / Convert 代码规范，不引入第二套架构风格。

---

# 6. 多租户设计

## 6.1 租户模型

平台必须从 V1 开始支持多租户。

> **身份、数据、缓存、消息、文件、AI、设备、任务、运维、OTA、统计、实时推送，全链路隔离。**

核心关系：

```text
Platform
   │
   ├── Tenant A
   │    ├── User
   │    ├── Member
   │    ├── Robot
   │    ├── Mission
   │    ├── Agent
   │    └── Knowledge
   │
   └── Tenant B
        ├── User
        ├── Member
        ├── Robot
        ├── Mission
        ├── Agent
        └── Knowledge
```

租户表：

```text
tenant
tenant_package
tenant_quota
tenant_usage
```

租户至少包含：

```text
id
tenant_code
tenant_name
package_id
status
contact_name
contact_phone
robot_limit
user_limit
agent_limit
storage_limit
expire_time
```

---

## 6.2 数据库隔离

V1 使用：

**单数据库 + 单 Schema + tenant_id 行级隔离。**

所有租户业务表必须包含：

```text
tenant_id
```

通过统一 MyBatis Plus 租户拦截器自动增加：

```sql
WHERE tenant_id = currentTenantId
```

禁止业务代码依赖开发人员手工拼接 `tenant_id`。

后台请求通过统一 TenantContext 获取当前租户。

异步任务、MQTT 消费、定时任务必须显式建立 TenantContext。

Redis Key 必须具备租户维度，例如：

```text
tenant:{tenantId}:robot:{robotId}:status
```

对象存储路径必须具备租户维度：

```text
/{tenantId}/robot/
/{tenantId}/media/
/{tenantId}/knowledge/
```

唯一索引涉及租户业务时必须带：

```text
tenant_id
```

例如：

```text
UNIQUE(tenant_id, robot_code)
```

禁止发生跨租户查询、修改、删除。

---

# 7. 用户模型

必须区分：

```text
后台用户 User
消费者 Member
```

后台用户：

```text
system_user
```

用于：

```text
平台管理员
租户管理员
研发
运营
售后
```

消费者：

```text
member
```

用于：

```text
APP 用户
微信小程序用户
机器人家庭成员
```

机器人绑定：

```text
member_robot_binding

id
tenant_id
member_id
robot_id
relation
role
is_owner
status
bind_time
```

一个机器人允许绑定多个家庭成员。

---

# 8. 设备与机器人模型

必须区分：

```text
Product
Device
Robot
```

关系：

```text
Product
   │
   ▼
Device
   │
   ▼
Robot
```

Product 表示产品型号。

例如：

```text
LTZX4
家庭机器人 V2
```

Device 表示物理设备身份。

Robot 表示机器人业务实体。

核心表：

```text
device_product
device
device_group
robot
robot_capability
```

Robot 至少包含：

```text
id
tenant_id
device_id
robot_code
robot_name
robot_model
main_board
ros_version
software_version
online_status
work_status
battery
ip_address
last_heartbeat_time
activate_time
```

在线状态：

```text
ONLINE
OFFLINE
```

工作状态：

```text
IDLE
NAVIGATING
INSPECTING
CHARGING
ERROR
UPGRADING
```

禁止使用一个 `status` 同时表达在线状态和工作状态。

---

# 9. 设备生命周期

设备支持：

```text
UNACTIVATED
ACTIVATED
DISABLED
MAINTENANCE
SCRAPPED
```

出厂设备可以没有租户：

```text
tenant_id = NULL
```

租户激活后：

```text
Device
  ↓
Tenant
```

需要支持：

```text
设备入库
租户激活
解绑
重新绑定
禁用
返厂
```

V1 不实现供应链、生产制造管理。

---

# 10. Mission 任务中心

Mission 是整个机器人平台统一任务执行模型。

所有入口：

```text
后台
APP
语音
Agent
定时计划
巡检
```

最终必须进入：

```text
Mission Service
```

禁止各业务直接通过 MQTT 控制机器人。

架构：

```text
APP / Admin / Agent / Inspection
              │
              ▼
         MissionService
              │
              ▼
            Mission
              │
              ▼
          MissionAction
              │
              ▼
     RobotCommandGateway
              │
              ▼
             MQTT
              │
              ▼
            Robot
```

---

# 11. Mission 数据模型

核心表：

```text
robot_mission
robot_mission_action
robot_mission_execution
robot_mission_event
```

Mission 至少包含：

```text
id
tenant_id
mission_no
robot_id
mission_type
source
status
priority
request_id
creator_id
scheduled_time
start_time
finish_time
error_code
error_message
```

Mission 来源：

```text
ADMIN
APP
VOICE
AGENT
SCHEDULE
INSPECTION
SYSTEM
```

Mission 状态：

```text
CREATED
PENDING
DISPATCHED
RUNNING
PAUSED
SUCCESS
FAILED
CANCELLED
```

Action 状态：

```text
PENDING
RUNNING
SUCCESS
FAILED
SKIPPED
```

Action Type V1：

```text
NAVIGATE
SPEAK
PLAY_MEDIA
CAPTURE_IMAGE
INSPECT
FIND_PERSON
FIND_OBJECT
RETURN_HOME
WAIT
CUSTOM
```

Mission 必须支持：

```text
创建
下发
机器人接收确认
开始执行
Action 进度
完成
失败
取消
超时
历史记录
```

---

# 12. MQTT 通信设计

使用 EMQX。

按照租户、产品、设备三级隔离。

Topic：

```text
robot/{tenantCode}/{productKey}/{deviceSn}/command
robot/{tenantCode}/{productKey}/{deviceSn}/state
robot/{tenantCode}/{productKey}/{deviceSn}/event
robot/{tenantCode}/{productKey}/{deviceSn}/ota
```



设备认证必须限制机器人只能访问自己的 Topic。

---

## 12.1 MQTT 消息统一 Envelope

所有消息统一：

```json
{
  "messageId": "01KXXX",
  "requestId": "MISSION-10001",
  "timestamp": 1789041600000,
  "type": "MISSION_START",
  "source": "CLOUD",
  "data": {
    "missionId": 10001
  }
}
```

必须保证：

```text
messageId 唯一
requestId 可追踪
timestamp 毫秒时间戳
type 明确消息类型
data 为业务数据
```

Command 必须支持幂等。

重复收到相同 `messageId` 不允许重复执行机器人动作。

---

# 13. 巡检中心

巡检作为 Mission 上层业务。

数据模型：

```text
inspection_plan
inspection_route
inspection_point
inspection_record
inspection_result
inspection_abnormal
```

关系：

```text
InspectionPlan
      │
      ▼
InspectionRoute
      │
      ▼
InspectionPoint
      │
      ▼
Mission
      │
      ▼
Robot
```

巡检点可以配置：

```text
位置
停留时间
检测类型
拍照
视频
VLM 检测
语音播报
```

检测类型 V1 支持配置，不硬编码：

```text
elder_status
fall
fire
refrigerator_door
faucet
custom
```

巡检结果：

```text
NORMAL
ABNORMAL
UNKNOWN
```

异常必须可以关联：

```text
机器人
Mission
巡检记录
巡检点
图片/视频
检测结果
发生时间
```

---

# 14. Agent 中心

Agent 是独立业务实体，不等同于 LLM 配置。

Agent 模型：

```text
Agent
├── Model
├── Prompt
├── Skill
├── Tool
├── Knowledge
├── Memory
└── DigitalHuman
```

核心表：

```text
ai_agent
ai_agent_robot_binding
ai_skill
ai_tool
ai_prompt
ai_model_provider
ai_model
ai_knowledge_base
ai_knowledge_document
ai_conversation
ai_conversation_message
ai_agent_execution
ai_tool_call
ai_usage_record
```

Agent 至少包含：

```text
id
tenant_id
name
code
description
model_id
system_prompt_id
digital_human_id
memory_enabled
knowledge_enabled
status
```

Agent 与 Robot 使用多对多绑定。

不得设计为：

```text
Robot.agent_id
```

必须支持：

```text
一个 Agent → 多机器人

一个 Robot → 多 Agent
```

并允许设置默认 Agent。

---

# 15. Agent、Skill、Tool、Mission 边界

必须严格保持：

```text
Agent
  ↓
Skill
  ↓
Mission / Tool
```

禁止：

```text
LLM → MQTT
LLM → ROS Topic
LLM → Database Update
```

Agent 只能通过注册后的 Skill / Tool 使用系统能力。

机器人行为优先转成 Mission。

例如：

```text
用户：
去厨房看看有没有异常

↓ Agent

start_inspection(planId)

↓ Skill

MissionService.createMission()

↓ Mission

MQTT

↓ Robot
```

---

# 16. Skill 模型

V1 内置 Skill：

```text
navigate_to
return_home
start_inspection
find_person
find_object
take_photo
play_media
speak
get_battery
get_robot_status
```

Skill 至少配置：

```text
code
name
description
parameter_schema
risk_level
enabled
```

参数使用 JSON Schema 描述。

---

# 17. Tool 模型

Tool 表示底层工具，不等同于 Skill。

例如：

```text
HTTP_API
KNOWLEDGE_SEARCH
ROBOT_STATUS
CAMERA_CAPTURE
MISSION_CREATE
WEATHER_API
CUSTOM
```

Tool 必须通过统一 ToolExecutor 执行。

V1 禁止提供：

```text
任意 Shell
任意 SQL
任意 MQTT Topic Publish
```

给 Agent。

---

# 18. Agent 风险控制

Skill / Tool 必须具有：

```text
LOW
MEDIUM
HIGH
CRITICAL
```

风险级别。

默认策略：

```text
LOW       自动执行
MEDIUM    Agent 可执行
HIGH      需要用户或平台确认
CRITICAL  Agent 禁止自动执行
```

例如：

```text
查询电量       LOW
播放语音       LOW
拍照           MEDIUM
机器人移动     MEDIUM
机器人重启     HIGH
软件升级       CRITICAL
```

风险判断必须发生在服务端，不能依赖 Prompt。

---

# 19. AI 模型管理

模型配置独立于 Agent。

数据模型：

```text
ai_model_provider
ai_model
```

V1 实现统一接口：

```java
AiModelClient
```

至少提供：

```text
OpenAI Compatible
```

协议适配。

通过 OpenAI Compatible 接口可以接入兼容服务。

Agent 不允许直接保存：

```text
baseUrl
apiKey
```

必须引用：

```text
model_id
```

API Key 等敏感信息必须加密存储，不允许明文返回前端。

---

# 20. Knowledge Base

知识库必须支持租户隔离。

模型：

```text
KnowledgeBase
   │
   ├── Document
   └── Chunk
```

至少支持：

```text
创建知识库
上传文档
文档解析
分块
Embedding
检索
Agent 绑定
```

任何向量检索必须首先约束：

```text
tenant_id
knowledge_base_id
```

禁止跨租户向量搜索。

V1 不实现复杂知识图谱。

---

# 21. Conversation 与 Agent Trace

AI 会话必须可追踪。

模型：

```text
Conversation
   │
   ├── USER
   ├── ASSISTANT
   ├── TOOL_CALL
   └── TOOL_RESULT
```

后台必须能够查看：

```text
用户输入
Agent 回复
使用模型
Token
Skill 调用
Tool 调用
参数
结果
Mission ID
耗时
错误
```

目标是可以回答：

> “机器人为什么执行了这个动作？”

---

# 22. Agent Memory

V1 定义三级：

```text
SESSION
ROBOT
MEMBER
```

Memory Mode：

```text
NONE
SESSION
LONG_TERM
```

所有长期 Memory 必须关联：

```text
tenant_id
```

并根据业务关联：

```text
robot_id
member_id
```

禁止不同租户、不同家庭用户 Memory 串数据。

V1 可以先实现 Memory 接口和基础存储，不实现复杂自动记忆提炼。

---

# 23. 数字人中心

数字人负责：

> 怎么呈现 Agent 的回答。

Agent 负责：

> 说什么、做什么。

两者必须解耦。

领域模型：

```text
DigitalHuman
├── Avatar
├── Voice
├── Expression
└── Action
```

核心表：

```text
ai_digital_human
ai_digital_avatar
ai_digital_voice
ai_digital_expression
ai_digital_action
ai_digital_human_action
ai_digital_session
```

数字人至少配置：

```text
name
avatar_id
voice_id
default_expression
default_action
status
```

---

# 24. 数字人输出协议

云端输出统一结构：

```json
{
  "text": "好的，我去厨房看看。",
  "emotion": "happy",
  "action": "nod",
  "media": null
}
```

机器人主控负责将内容转发 Android 屏。

Android 数字人 Runtime 负责：

```text
Avatar
TTS
LipSync
Expression
Animation
```

V1 管理平台只负责：

```text
数字人配置
数字人绑定
输出协议
交互记录
```

**不负责实现 3D 数字人渲染引擎。**

---

# 25. OTA / 软件版本中心

机器人软件版本不同于普通 MCU Firmware。

模型：

```text
robot_release
robot_release_package
robot_upgrade_task
robot_upgrade_record
```

Robot Release：

```text
Robot Release 1.4.0
│
├── maincontrol  2.3.1
├── navigation   1.7.2
├── perception   1.5.0
├── identity     1.3.4
├── offline-asr  1.2.0
└── config       20260910
```

软件包至少记录：

```text
package_name
version
download_url
sha256
size
git_commit
description
```

升级方式：

```text
指定机器人
批量机器人
租户全部机器人
灰度比例
```

升级状态：

```text
PENDING
DOWNLOADING
INSTALLING
VERIFYING
SUCCESS
FAILED
ROLLBACK
```

V1 不实现自动复杂版本依赖求解。

---

# 26. 远程运维

后台需要支持：

```text
机器人实时状态
服务状态
日志查看
远程控制
故障诊断
操作记录
```

实时状态：

```text
CPU
Memory
Disk
Temperature
Network
Battery
IP
ROS Status
Last Heartbeat
```

服务状态例如：

```text
maincontrol
navigation
camera
mic
identity
inspection
offline-asr
```

允许：

```text
查看状态
启动服务
停止服务
重启服务
```

所有远程操作必须写：

```text
operation_record
```

记录：

```text
tenant_id
robot_id
operator_id
operation
request
result
operation_time
```

V1 不提供 Web Shell 或任意远程命令执行。

---

# 27. 告警中心

核心表：

```text
robot_alarm_rule
robot_alarm
```

V1 内置：

```text
ROBOT_OFFLINE
LOW_BATTERY
MISSION_FAILED
UPGRADE_FAILED
SERVICE_DOWN
ROBOT_ERROR
```

告警级别：

```text
INFO
WARNING
ERROR
CRITICAL
```

状态：

```text
OPEN
ACKNOWLEDGED
RESOLVED
```

告警必须关联租户和机器人。

---

# 28. AI 用量与租户配额

记录：

```text
ai_usage_record

tenant_id
agent_id
member_id
robot_id
model_id
input_tokens
output_tokens
audio_seconds
tts_chars
image_count
cost
create_time
```

租户可以配置：

```text
robot_limit
user_limit
agent_limit
storage_limit
llm_token_limit
```

V1 只实现：

```text
配额检查
用量统计
超限禁止继续创建或调用
```

不实现支付、计费、订单。

---

# 29. 管理后台菜单

最终菜单：

```text
工作台

机器人中心
├── 机器人列表
├── 产品型号
├── 设备管理
├── 设备分组
├── 在线设备
└── 机器人能力

任务中心
├── 实时任务
├── 历史任务
├── 任务模板
└── 定时任务

巡检中心
├── 巡检计划
├── 巡检路线
├── 巡检点位
├── 巡检记录
├── 巡检报告
└── 异常事件

AI 中心
├── Agent 管理
├── Skill 管理
├── Tool 管理
├── Prompt 管理
├── 模型管理
├── 知识库
└── AI 会话

数字人中心
├── 数字人
├── 形象管理
├── 音色管理
├── 动作管理
├── 表情管理
└── 交互记录

版本中心
├── 软件版本
├── 软件包
├── 升级任务
└── 升级记录

远程运维
├── 实时状态
├── 远程控制
├── 服务状态
├── 日志中心
├── 指令记录
└── 故障诊断

告警中心
├── 当前告警
├── 历史告警
└── 告警规则

用户中心
├── 用户
├── 家庭成员
└── 机器人绑定

租户中心
├── 租户管理
├── 套餐管理
├── 租户配额
└── 用量统计

系统管理
├── 后台用户
├── 角色权限
├── 菜单管理
├── 部门管理
├── 字典管理
└── 参数配置
```

租户中心仅平台超级管理员可见。

---

# 30. Dashboard

首页第一版不要堆大量无实际意义图表。

顶部展示：

```text
机器人总数
在线机器人
离线机器人
故障机器人

今日任务
执行中任务
任务成功数
任务失败数

当前告警
严重告警
```

机器人列表：

```text
机器人
在线状态
工作状态
电量
当前任务
软件版本
最后心跳
```

下方提供：

```text
任务趋势
在线率
告警趋势
版本分布
```

所有数据必须调用真实后端接口。

禁止使用静态 Mock 数据冒充完成。

---

# 31. REST API 规范

统一：

```text
/admin-api/*
```

主要 API：

```text
/admin-api/tenant/tenants
/admin-api/tenant/packages

/admin-api/device/products
/admin-api/device/devices

/admin-api/robot/robots
/admin-api/robot/robots/{id}
/admin-api/robot/robots/{id}/status

/admin-api/robot/missions
/admin-api/robot/missions/{id}
/admin-api/robot/missions/{id}/cancel

/admin-api/robot/inspection/plans
/admin-api/robot/inspection/records
/admin-api/robot/inspection/reports

/admin-api/ai/agents
/admin-api/ai/skills
/admin-api/ai/tools
/admin-api/ai/models
/admin-api/ai/knowledge-bases
/admin-api/ai/conversations
/admin-api/ai/digital-humans

/admin-api/robot/releases
/admin-api/robot/upgrades

/admin-api/operation/robots/{id}/services
/admin-api/operation/robots/{id}/logs
/admin-api/operation/robots/{id}/diagnostics
```

遵循现有项目统一 Response、分页、错误码规范。

禁止自行设计第二套响应格式。

---

# 32. 权限编码

权限格式：

```text
{domain}:{resource}:{action}
```

例如：

```text
robot:robot:query
robot:robot:update
robot:mission:create
robot:mission:cancel
robot:inspection:create

ai:agent:create
ai:agent:update
ai:agent:delete
ai:knowledge:query

operation:robot:restart
operation:log:query

tenant:tenant:create
tenant:tenant:update
```

页面按钮必须真正绑定权限。

不能只隐藏按钮而不做服务端鉴权。

---

# 33. 数据库公共字段

租户业务表统一包含：

```text
id bigint
tenant_id bigint

creator varchar
create_time timestamp
updater varchar
update_time timestamp

deleted boolean
```

状态字段必须使用明确枚举。

禁止：

```text
status = 0/1/2/3
```

但代码中无法知道具体含义。

Java 使用 Enum 明确定义语义。

数据库可以存字符串或者稳定枚举值，但必须统一。

---

# 34. 实时状态存储

高频机器人状态不要每个心跳都写 PostgreSQL。

建议：

```text
Heartbeat
   ↓
Redis
```

保存：

```text
在线状态
电量
CPU
Memory
温度
当前任务
工作状态
IP
最后心跳
```

数据库只保存：

```text
关键状态变化
统计数据
历史事件
```

在线判断：

```text
currentTime - lastHeartbeat > timeout
```

则：

```text
OFFLINE
```

超时时间必须配置化。

---

# 35. WebSocket

后台机器人实时页面通过 WebSocket 接收：

```text
ROBOT_STATUS_CHANGED
MISSION_STATUS_CHANGED
ALARM_CREATED
UPGRADE_PROGRESS
```

WebSocket 推送必须带租户上下文。

租户 A 的连接绝对不能收到租户 B 的事件。

---

# 36. 核心领域关系

最终保持：

```text
                         Tenant
                            │
          ┌─────────────────┼─────────────────┐
          ▼                 ▼                 ▼
        User              Member            Product
                                                │
                                                ▼
                                              Device
                                                │
                                                ▼
                                              Robot
                                                │
                     ┌──────────────────────────┼────────────┐
                     ▼                          ▼            ▼
                  Mission                    Agent        Release
                     │                          │
                     ▼                    ┌─────┼─────┐
                  Action                  ▼     ▼     ▼
                                       Skill  KB   Memory
                                         │
                                         ▼
                                   Digital Human
```

两个最重要的调用边界：

```text
Agent → Skill → Mission
```

以及：

```text
Mission → RobotCommandGateway → MQTT → Robot
```

任何实现都不得绕开这两个边界。

---

# 37. V1 非目标

以下功能不在 V1 实现范围：

```text
Open-RMF 多机器人交通调度
复杂 AMR 路径冲突解决
VLA
多 Agent 自动协作
Agent Marketplace
Skill Marketplace
在线支付
商业计费订单
3D 数字人渲染引擎
手机 APP
微信小程序
复杂知识图谱
任意 Web Shell
远程桌面
机器人自主代码更新
```

可以预留接口，但禁止提前大规模开发。

---

# 38. Codex 开发约束

Codex 实现本需求时必须遵循以下要求。

## 38.1 禁止假实现

禁止为了让页面“看起来完成”而：

```text
硬编码统计数字
返回固定机器人
Mock Mission
Mock Agent
Mock Dashboard
随机生成状态
伪造 AI 响应
前端 setTimeout 模拟任务成功
```

如果后端能力尚未实现：

**页面必须明确显示暂无数据或功能未启用。**

---

## 38.2 禁止自行猜字段

增加数据库字段之前必须确认：

```text
业务含义
数据类型
是否 nullable
默认值
索引
唯一约束
tenant_id 隔离
```

禁止因为前端需要展示而随意增加同义字段。

例如禁止同时出现：

```text
robot_status
status
work_status
running_status
```

表达同一概念。

---

## 38.3 禁止静默降级

MQTT、AI、Redis、数据库等关键能力失败时：

```text
明确报错
记录日志
返回标准错误码
```

禁止：

```text
catch Exception → return success
```

禁止失败后自动返回 Mock 数据。

---

## 38.4 禁止跨层调用

Controller 不直接操作 Mapper。

Agent 不直接调用 MQTT。

Inspection 不直接调用 MQTT。

MQTT Consumer 不直接操作大量业务 Mapper。

统一：

```text
Controller
 ↓
Service
 ↓
Domain Service / Gateway
 ↓
DAL / MQTT / AI
```

---

## 38.5 修改范围控制

每一个阶段只修改当前需求相关代码。

禁止：

```text
大规模格式化整个仓库
无关重命名
无关 Maven 升级
无关依赖升级
自动重构所有旧代码
```

---

# 39. 测试要求

核心业务必须有自动测试。

最低测试：

```text
Tenant 隔离测试
Robot CRUD
Mission 状态机
Mission 幂等
Mission Cancel
Inspection 创建 Mission
Agent Skill 权限
Agent 风险等级
Agent → Mission
MQTT 消息解析
MQTT messageId 幂等
OTA 状态机
租户 AI 配额
```

尤其必须编写：

### 跨租户隔离测试

创建：

```text
Tenant A
Tenant B
```

分别创建机器人。

必须验证：

```text
Tenant A 查询不到 Tenant B Robot
Tenant A 修改不了 Tenant B Robot
Tenant A 删除不了 Tenant B Robot
Tenant A Agent 无法访问 Tenant B Knowledge
Tenant A WebSocket 收不到 Tenant B 消息
```

这是发布阻塞测试。

---

# 40. 本地开发环境

提供：

```text
docker-compose.yml
```

至少包含：

```text
PostgreSQL
Redis
EMQX
```

提供：

```text
application-local.yaml
```

README 中写清：

```text
启动基础服务
初始化数据库
启动后端
启动前端
创建平台管理员
创建租户
创建设备
模拟机器人上线
创建 Mission
```

允许提供：

```text
robot-simulator
```

用于开发阶段模拟 MQTT Robot。

Simulator 必须发送真实协议消息。

不能绕过 MQTT 直接修改数据库。

---

# 41. Robot Simulator

为了没有真实机器时开发，V1 增加轻量机器人 Simulator。

功能：

```text
连接 EMQX
设备认证
定时 heartbeat
状态上报
接收 command
Mission ACK
Action 状态上报
Mission SUCCESS / FAILED
OTA 状态模拟
```

例如：

```bash
python robot_simulator.py \
  --sn LTZX4-TEST-001 \
  --broker localhost
```

Simulator 属于开发工具。

生产代码不得依赖 Simulator。

---

# 42. 实现阶段

Codex 不允许一次性实现整个系统。

严格按下面阶段开发。

## Phase 0：基础工程

完成：

```text
工程可启动
品牌调整
模块结构
PostgreSQL
Redis
登录
RBAC
基础多租户
```

验收后再继续。

---

## Phase 1：Tenant + Device + Robot

完成：

```text
租户
套餐
产品
设备
机器人
设备绑定租户
在线状态
心跳
Robot Simulator
```

必须完成 Tenant 隔离测试。

---

## Phase 2：MQTT + Mission

完成：

```text
EMQX
RobotMessageGateway
MQTT Envelope
Command
ACK
Mission
MissionAction
状态机
任务详情
实时状态
WebSocket
```

这是平台最核心阶段。

---

## Phase 3：Inspection

完成：

```text
巡检计划
路线
点位
巡检 Mission
记录
结果
异常
报告
```

---

## Phase 4：Agent

完成：

```text
Model Provider
Model
Prompt
Agent
Skill
Tool
Robot Binding
Conversation
Agent Execution Trace
Agent → Skill → Mission
```

至少实现一个 OpenAI-Compatible Model Provider。

---

## Phase 5：Knowledge + Digital Human

完成：

```text
Knowledge Base
Document
Retrieval

Digital Human
Avatar
Voice
Expression
Action
Agent Binding
统一输出协议
```

不实现 3D Rendering。

---

## Phase 6：OTA + Operation + Alarm

完成：

```text
Release
Package
Upgrade Task
Upgrade Record

Robot Service
Robot Log
Diagnostic

Alarm
Alarm Rule
```

---

## Phase 7：Dashboard + 完整联调

完成：

```text
工作台
统计接口
权限检查
租户检查
MQTT 联调
Simulator 联调
Agent → Mission → Robot 联调
```

删除所有遗留 Mock。

---

# 43. 每个 Phase 的 Codex 完成标准

每完成一个 Phase 必须执行：

```bash
mvn test
```

以及前端：

```bash
pnpm lint
pnpm type-check
pnpm build
```

具体命令如果项目当前脚本名称不同，以仓库真实脚本为准。

同时检查：

```bash
git diff --stat
git diff
```

输出本阶段：

```text
完成内容
修改文件
数据库变更
新增 API
新增权限
测试结果
尚未完成内容
已知问题
```

测试失败不得声明完成。

---

# 44. V1 最终验收场景

必须能够完成以下真实闭环。

### 场景一：机器人上线

```text
创建租户
→ 创建产品
→ 创建设备
→ 激活机器人
→ Simulator 连接 EMQX
→ heartbeat
→ 后台显示 ONLINE
```

### 场景二：后台任务

```text
后台创建 Mission
→ MQTT command
→ Robot ACK
→ RUNNING
→ Action SUCCESS
→ Mission SUCCESS
→ 后台实时刷新
```

### 场景三：巡检

```text
创建巡检路线
→ 创建巡检计划
→ 执行
→ 创建 Mission
→ Robot 执行
→ 上传结果
→ 生成巡检记录
→ 异常进入告警
```

### 场景四：Agent

```text
用户：
“去厨房看看有没有异常”

→ Agent
→ Skill start_inspection
→ MissionService
→ Mission
→ MQTT
→ Robot
```

后台 AI Trace 能看到完整调用链。

### 场景五：数字人

```text
用户请求
→ Agent Response
→ DigitalHuman Response
→ text + emotion + action
→ Robot
→ Android Screen
```

### 场景六：多租户

```text
Tenant A：Robot A
Tenant B：Robot B
```

任何：

```text
HTTP
数据库
Redis
WebSocket
Agent
Knowledge
Mission
```

均不能发生跨租户访问。

---

# 45. 完成定义 Definition of Done

V1 只有同时满足以下条件才算完成：

```text
后端编译通过
前端构建通过
数据库脚本可从空库初始化
没有必须依赖手工修改数据库的步骤
Tenant 隔离测试通过
核心 Service 有单元测试
MQTT 可以通过 Simulator 完成闭环
Mission 状态可追踪
Agent 可以真实创建 Mission
Dashboard 使用真实 API
无 Mock 业务数据
无静默 catch
敏感配置不明文返回
权限控制同时存在于前端和服务端
README 可以让新开发者独立启动项目
```

---

# 46. 开发优先级

整个项目优先级固定为：

```text
P0
多租户
Robot
MQTT
Mission

P1
Inspection
OTA
Operation
Alarm

P2
Agent
Skill
Tool
Conversation

P3
Knowledge
Digital Human
AI Usage

P4
Dashboard 优化
高级统计
体验优化
```

任何 P3/P4 功能不得以破坏 P0 核心稳定性为代价提前实现。

---

# 47. 架构底线

开发过程中始终保持四条边界：

```text
Tenant
  ↓
所有业务数据必须隔离
```

```text
Agent
  ↓
Skill
  ↓
Mission
```

```text
Mission
  ↓
RobotCommandGateway
  ↓
MQTT
```

```text
Agent
      ├── 决定说什么、做什么
      │
Digital Human
      └── 决定如何呈现
```

如果后续设计与以上边界冲突，应优先调整后续设计，而不是破坏核心边界。
