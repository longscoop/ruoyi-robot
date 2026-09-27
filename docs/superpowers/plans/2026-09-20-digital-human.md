# Digital Human Implementation Plan

> 按 Task 顺序执行；每个 Task 必须：测试先行 -> 实现 -> 测试 -> 自检 -> commit。

**Goal:** 在现有 Realtime Agent 上增加多租户数字人配置、协议状态、管理端预览和端侧 renderer 边界。

**Spec:** `docs/superpowers/specs/2026-09-20-digital-human-design.md`

## Global Constraints

- 不复制 Agent/Conversation/Memory/Model Runtime。
- 不新增第二条实时音频通道；复用 `/device-api/ai/realtime`。
- 所有数据 tenant 隔离；ID 查询不能只按主键。
- 不向浏览器/设备返回 Provider secret。
- V1 只要求 STATIC_2D renderer 真正可用；Live2D/3D 只保留类型与 adapter 边界。
- 每个 Task 独立 commit，前一个 Task 通过后再执行下一个。

### Task 1: 数据库与领域模型

**Files:** 修改 AI schema/migration；创建 DigitalHuman/DigitalHumanAction DO、Mapper、枚举及测试。

先写测试覆盖租户唯一 code、状态动作唯一性、逻辑删除/tenant 查询。再实现 `ai_digital_human` 与 `ai_digital_human_action`。运行 AI module tests。

Commit: `feat(ai): add digital human domain model`

### Task 2: 数字人管理 Service 与租户安全

创建 DigitalHumanService，支持 CRUD、动作映射；校验 Agent、TTS Model 同租户且类型正确。测试先覆盖跨租户绑定、跨租户按 ID 读取、无效 TTS model、重复 code。

Commit: `feat(ai): add digital human management service`

### Task 3: Admin API

增加 `/admin-api/ai/digital-humans` CRUD 与 actions API；DTO 不含任何 secret/base credential。先写 controller contract/permission/tenant tests。

Commit: `feat(ai): expose digital human admin api`

### Task 4: Realtime session 解析数字人

扩展 session.start：可选 `digitalHumanCode` 和 clientCapabilities。解析 DigitalHuman -> Agent；若同时传 agentCode，二者必须一致。未传数字人保持现有协议完全兼容。

测试：不存在、禁用、跨租户、Agent 不一致、正常解析。

Commit: `feat(ai): resolve digital human realtime sessions`

### Task 5: 状态事件与配置快照

实现 `digital_human.config/state/viseme` DTO 与事件映射。将 speech started/stopped、assistant audio、tool lifecycle、interrupt/error 映射到标准状态。测试事件 sequence、turnId、barge-in 后不继续旧 SPEAKING。

Commit: `feat(ai): stream digital human state events`

### Task 6: Voice override 与 lip-sync capability

实现 voice resolution：DigitalHuman override > Agent voice；校验 Provider/model capability。仅在真实 provider 支持时发 viseme，否则协商 AUDIO_LEVEL。Trace 记录 resolved voice，不记录 secret。

Commit: `feat(ai): resolve digital human voice and lip sync`

### Task 7: 前端 typed API

创建 `src/api/ai/digital-human/index.ts` 及测试。类型与 Admin DTO 一致，不定义 secret 字段。

Commit: `feat(ui): add digital human api client`

### Task 8: 数字人管理页面

新增 AI 中心“数字人”：列表、编辑表单、Agent、STATIC_2D 资源、声音、欢迎语、打断、状态动作。测试表单条件和真实 API response shape。

Commit: `feat(ui): manage digital humans`

### Task 9: STATIC_2D 预览 Runtime

创建前端 `DigitalHumanRenderer` 接口和 Static2DRenderer；预览展示字幕、音频播放以及 IDLE/LISTENING/THINKING/SPEAKING/EXECUTING/ERROR 状态。禁止 fake realtime metrics。

Commit: `feat(ui): preview static digital human`

### Task 10: 菜单与权限

增加数字人菜单及 CRUD/action/preview 权限种子；contract test 确保所有 `@PreAuthorize` 权限存在于 seed。

Commit: `feat(ai): add digital human permissions and menu`

### Task 11: Realtime simulator / E2E

扩展已有 RK3588 simulator，增加 DIGITAL_HUMAN_CODE 和 capabilities；验证 config -> listening -> thinking -> speaking -> idle、tool executing、barge-in。匿名/身份/tenant 规则仍由 Agent E2E 复用。

Commit: `test(ai): add digital human realtime e2e`

### Task 12: Android/H5 renderer 协议文档与示例

若仓库存在 Android 客户端则按其现有模块实现 adapter；若不存在，不虚构 Android 工程，只提交平台协议、状态机和 renderer reference implementation 文档。明确未来 Live2D/3D 接入点。

Commit: `docs(ai): document digital human renderer protocol`

### Task 13: 发布验证与文档

运行 backend AI/server tests、frontend type-check/tests、mock-provider E2E；更新 README，记录环境限制和真实测试结果。不得把未运行测试写成 PASS。

Commit: `docs(ai): finalize digital human verification`

## Exit Criteria

数字人是 Agent 的表现层而非第二套 Agent；STATIC_2D 全链路可用；Realtime 协议向后兼容；tenant/secret/Robot Skill 边界不弱化；管理端和模拟器有真实测试覆盖；Live2D/3D 可通过 renderer adapter 后续扩展。
