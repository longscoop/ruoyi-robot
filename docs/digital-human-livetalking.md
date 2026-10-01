# LiveTalking 数字人接入

本次改动位于 `ruoyi-robot`：AI 模块负责渲染服务适配、音频转发和连接隔离；管理端负责选择服务和视频预览；服务端 YAML 提供实例配置。机器人原有 ASR、LLM、TTS 和长期记忆配置继续生效。LiveTalking 独立部署，不作为 Java 服务的进程或依赖启动。

## 启用服务

先按 [LiveTalking 上游安装说明](https://github.com/lipku/LiveTalking) 准备模型文件、形象和 GPU 环境，再启动 WebRTC 服务，例如：

```sh
python app.py --transport webrtc --model wav2lip --avatar_id wav2lip256_avatar1 --listenport 8010
```

平台后端设置：

```sh
export ROBOT_LIVETALKING_ENABLED=true
export ROBOT_LIVETALKING_URL=http://your-gpu-server:8010
# 上游反向代理启用了 Bearer 验证时才设置；LiveTalking 原生服务不要求此项。
export ROBOT_LIVETALKING_TOKEN=your-proxy-token
```

如果本地后端通过 `java -jar` 启动，先执行 `mvn -pl robot-platform-server -am -DskipTests package`，再用新生成的 JAR 重启后端；仅编译 AI 模块或刷新前端不会更新正在运行的 JAR。

重启平台后，在「AI → 数字人」选择「LiveTalking 实时数字人」、服务实例、形象 ID，保存后打开预览并点击「连接视频」。形象 ID 留空使用实例默认形象，图片在此模式下仅作可选封面。试听文本调用 LiveTalking 自身的 TTS；试听音色取决于 LiveTalking 配置。正式机器人对话使用平台智能体生成的音频，不调用 LiveTalking 的 LLM，不做二次语音合成。

`8010` 是上游示例端口。本仓库 PowerMem 默认也使用 `8010`，在同一主机部署时应给两者分配不同端口，并分别设置 URL。

平台代理 HTTP 信令与控制，浏览器直接与 LiveTalking 建立 WebRTC 媒体连接。应保证客户端能访问媒体地址，按部署网络开放 WebRTC UDP 或配置 TURN；仅反向代理 TCP/HTTP 无法保证媒体可达。LiveTalking 自身的 STUN 配置和平台发给浏览器的 `ice-servers` 均需符合现场网络。服务地址、代理令牌只保存在服务器配置，客户端只能选择预先登记的实例，不接受客户端传入任意目标 URL。

## 切换底层

有两层切换：

- 数字人渲染提供者：`BUILTIN`（现有静态形象）或 `LIVETALKING`。
- LiveTalking 实例：一个实例运行一个模型，例如分别部署 Wav2Lip、MuseTalk，通过选择实例切换模型；不能通过单次 `/offer` 请求切换模型。`avatarId` 只能选择该实例上已准备好的兼容形象。

在外部 `application-local.yaml` 或部署配置中增加实例：

```yaml
robot:
  ai:
    digital-human:
      services:
        wav2lip:
          enabled: true
          provider: LIVETALKING
          name: Wav2Lip 数字人
          url: http://gpu-host:8010
          timeout-seconds: 30
        musetalk:
          enabled: true
          provider: LIVETALKING
          name: MuseTalk 数字人
          url: http://gpu-host:8011
          timeout-seconds: 30
      ice-servers:
        - urls:
            - stun:your-stun-server:3478
        # TURN 凭据需要发给客户端使用，不属于后端代理 token。
        # 生产环境应使用有期限的 TURN 凭据。
        # - urls: ["turn:your-turn-server:3478"]
        #   username: ...
        #   credential: ...
```

数字人现有 `configJson` 新增一个独立字段，其他配置保留：

```json
{
  "rendering": {
    "provider": "LIVETALKING",
    "service": "wav2lip",
    "avatarId": "wav2lip256_avatar1"
  }
}
```

无需 SQL 迁移；未配置 `rendering` 时默认为 `BUILTIN`。切回内置静态形象后使用原图片和状态展示。配置变更在新连接生效，已有连接保持创建时的实例。

后端扩展点是 `DigitalHumanProvider` 接口，实现并注册为 Spring Bean 后由 `DigitalHumanProviders` 选择。新增不同视频协议时，前端工厂 `createDigitalHumanRenderer` 也需要增加对应实现。ASR、LLM、TTS 的模型注册表与渲染提供者相互独立。

## 管理端接口

已有数字人增删改查接口不变。`POST /admin-api/ai/digital-humans/{id}/preview-session` 增加 `rendering: {provider, iceServers}`，不返回上游服务地址、代理令牌、上游会话 ID。

| 方法 | `/admin-api/ai/digital-humans` 下的路径 | 用途 |
| --- | --- | --- |
| GET | `/render-services` | 已启用的实例 ID、名称和提供者，沿用查询权限 |
| POST | `/{id}/render-sessions` | `{ "sdp": "v=0..." }`；返回平台 `sessionId`、`type: answer` 和 SDP |
| POST | `/{id}/render-sessions/{sessionId}/speak` | `{ "text": "你好" }`，上限 2000 字 |
| POST | `/{id}/render-sessions/{sessionId}/interrupt` | 清空播报队列 |
| GET | `/{id}/render-sessions/{sessionId}/speaking` | 返回是否正在播放；同时维持预览活动时间 |
| DELETE | `/{id}/render-sessions/{sessionId}` | 释放平台预览句柄并尝试打断上游 |

连接操作沿用 `ai:digital-human:preview` 权限。平台预览句柄绑定租户、登录用户和数字人，禁止跨用户、跨租户调用。每位用户最多 2 个预览，单进程最多 32 个，120 秒无操作后过期并由清理任务回收。预览句柄在内存中，多实例部署需保持这些请求的会话粘性；重启后重新连接。

客户端断开时必须关闭 `RTCPeerConnection` 和媒体轨道；LiveTalking 通过 PeerConnection 关闭/失败释放模型会话。当前对接的上游通用 API 没有单独的删除会话接口，平台 DELETE 不能代替客户端关闭媒体连接。

## 机器人实时客户端

现有 `/device-api/ai/realtime` WebSocket 鉴权、`session.start` 和麦克风 PCM 协议保持不变。客户端按以下顺序连接：

1. `session.start` 继续传 `agentCode`、`digitalHumanCode` 和音频格式。
2. 收到 `digital_human.config` 后解析 `config` JSON 字符串，读取新增的 `rendering`。BUILTIN 继续原有展示。
3. LIVETALKING 创建 PeerConnection，使用 `rendering.iceServers`，依次添加 `audio`、`video` 的 `recvonly` transceiver。收到的轨道绑定同一个 video 元素。
4. 创建 offer，设置 local description，等待 ICE gathering complete，发送新增的 WebSocket 控制帧：

   ```json
   {"type":"digital_human.offer","sdp":"v=0..."}
   ```

5. 接收 `digital_human.answer`，其 `sdp` 为 answer，将 `{type: 'answer', sdp}` 设置为 remote description。等待 WebRTC connected 后再开始麦克风输入。同一 WebSocket 只协商一个视频连接。
6. 平台将实时模型或级联 TTS 的 24 kHz、单声道 PCM16 按 500 ms 分片封装 WAV，顺序调用 LiveTalking `/humanaudio`，尾部不足一片时在 `assistant.audio.done` 刷出。视频连接接管后，平台不再发送该连接的原始二进制回答音频，避免重复播放；字幕和对话事件继续发送。
7. `input.speech_started` 会丢弃待发送的旧音频并调用上游打断，即使模型已回答完成而视频仍在播放。原有模型中断和迟到帧丢弃逻辑继续生效。
8. 收到 `digital_human.error`，显示错误、关闭媒体和 WebSocket 后重新建立连接。会话结束、页面离开或 WebSocket 断开时也必须关闭 PeerConnection。

新增服务端事件保持原来的 `sessionId`、`sequence`、`serverTime` 信封：

```json
{"type":"digital_human.answer","sessionId":"...","sdp":"v=0...","sequence":3,"serverTime":"..."}
{"type":"digital_human.error","sessionId":"...","message":"数字人音视频暂时不可用，请重新连接","sequence":4,"serverTime":"..."}
```

旧客户端不发送 `digital_human.offer`，仍接收原来的二进制语音。内置动画预览与 LiveTalking 视频连接相互独立。

## 验证与边界

接口依据 [上游 API 文档](https://github.com/lipku/LiveTalking/blob/main/docs/api.md)、[RTC 实现](https://github.com/lipku/LiveTalking/blob/main/server/rtc_manager.py) 和 [业务接口实现](https://github.com/lipku/LiveTalking/blob/main/server/routes.py)。兼容数字型旧 sessionid 和当前 UUID 字符串，不将上游会话 ID 交给管理端。

自动化测试覆盖本地模拟 HTTP 服务的 SDP、形象选择、文本 echo、WAV multipart、上游 code 错误、预览隔离、500 ms 音频分片、排队上限、打断顺序、迟到帧丢弃、实时音频路由和浏览器资源释放。

```sh
mvn -pl robot-platform-module-ai -am \
  -Dtest='*DigitalHuman*,*RealtimeProtocolCodecTest,*RealtimeAgentRuntimeTest,*RealtimeInterruptionTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
cd robot-platform-ui-admin
pnpm test src/views/ai/digital-human/runtime/LiveTalkingRenderer.spec.ts
pnpm ts:check
```

尚未部署真实 LiveTalking，此轮验证不包含 GPU 推理、真实口型同步、模型素材和跨网段 WebRTC。部署后需检查：静音视频可播放、文字试听、机器人麦克风问答、播放中打断、关闭窗口后的 GPU 会话回收、切换实例后的新连接。

音频采用有上限的顺序上传队列，不阻塞实时模型的回调。500 ms 分片、网络和推理会引入额外延迟；队列超过 24 片时报告错误并停止，避免积压持续增长。`assistant.audio.done` 表示上游音频生成完成，不代表视频端已经播完。切换模型和服务不承诺无缝迁移正在进行的会话。

### 本地接口报错排查

若访问 `/admin-api/ai/digital-humans/render-services` 报 `id` 转换失败、输入值为 `render-services`，先确认运行的 JAR 是否包含 `DigitalHumanRenderController`。旧后端只有 `/{id}` 路由，会把新接口名当成 ID。重新打包并启动最新后端即可；无需修改数据库或数字人 ID。未启用 LiveTalking 时，新接口应成功返回空数组。


## 内置动画角色与语音对话测试

无需部署 LiveTalking。后台「数字人」选择启用的 BUILTIN 数字人，点击「预览」：

1. 默认显示内置机器人角色，自动眨眼、呼吸；可以切换开心、思考、惊讶表情，点击点头、摇头、挥手。开始回答会自动点头，口型大小由浏览器实际播放音频的 RMS 音量驱动，不是逐音素口型，也不推断回答情绪。
2. 有 `avatarUrl` 时可切换「上传图片」，保留呼吸和整张图片摆动效果，不对普通照片合成眨眼、张嘴或挥手。显示选择和表情仅作用于本次预览。
3. 点击「开始对话」建立当前管理员的独立测试会话，然后「开始说话」授权麦克风，说完点击「发送录音」。每轮最多 30 秒，届时自动发送。采用录完发送，回答字幕和音频流式接收；不是持续监听/VAD 免按键模式。
4. 数字人按绑定智能体的 NATIVE / CASCADE / AUTO 配置回答。NATIVE 使用实时语音模型和数字人 `voiceId`；CASCADE 使用 ASR、对话模型、TTS，数字人 `voiceModelId` / `voiceId` 可覆盖合成模型与音色。不填写音色则保留模型默认值。模型与智能体必须启用并配置有效凭据。
5. 开启 `interruptEnabled` 时可「打断回答」或「打断并说话」。本地排队音频立即停止，上游取消当前轮次；迟到的字幕/音频不会污染新轮次。关闭预览或结束对话会释放音频、麦克风和模型连接。

浏览器麦克风需要 HTTPS 或 localhost。预览复用模型适配器，但不模拟设备身份、不读写家庭长期记忆，也不执行机器人动作；会话内保留模型支持的多轮上下文，结束后丢弃。本次涉及 AI 后端、管理前端及 Web 开发日志脱敏；不修改 ROS、Android、设备鉴权和既有接口，无数据库迁移。

### 管理端对话接口

接口前缀 `/admin-api/ai/digital-humans/{id}/dialogue-sessions`，全部需要登录、租户上下文及 `ai:digital-human:preview` 权限。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | 空路径 | 创建会话，标准响应 data 为 sessionId |
| POST | `/{sessionId}/turns` | `{ "pcm": "base64..." }`，单声道 16 kHz PCM16LE，0.1–30 秒；返回 SSE |
| POST | `/{sessionId}/interrupt` | 取消当前回答 |
| POST | `/{sessionId}/keepalive` | 保活；页面每 45 秒发送 |
| DELETE | `/{sessionId}` | 结束测试会话 |

SSE 的 `data` 为 JSON，`type` 包括 `thinking`、`user.delta`、`user.done`、`assistant.delta`、`assistant.text`、`audio`、`done`、`interrupted`、`error`。字幕字段 `text`；音频字段 `pcm`（Base64 PCM16LE）和 `sampleRate: 24000`；错误字段 `message`。`done` 表示音频生成完成，UI 要等本地声音播完才恢复待机。录音参数不写入访问日志或开发控制台。

会话绑定租户、管理员和数字人，单用户最多 2 个、单进程最多 32 个；180 秒无活动回收。会话存于内存，多实例需会话粘性，重启需重新开始。SSE 单轮上限 120 秒，代理需关闭缓冲并提供足够读取超时。欢迎语作为初始字幕展示，不自动朗读。

验证：`DigitalHumanDialogueSessionsTest` 覆盖 SSE 路由、隔离、音频限制、打断/迟到事件、异常脱敏和关闭竞态；`DigitalHumanDialogueFactoryTest` 覆盖两种模型路由和音色选择。前端 `dialogue.spec.ts` 与 `StaticHumanDialogue.spec.ts` 覆盖重采样、PCM 拼接、流中断、播放停止、录音权限迟到、字幕更新和组件销毁。人工联调需使用真实麦克风确认识别、音色、播放音量及连续两轮问答。

### 录音发送后立即失败（2026-10-01 修复）

原 Qwen ASR 连接就绪前最多缓冲 256 KiB，仅约 8.19 秒 PCM16/16 kHz 音频；管理端录完后批量发送最长 30 秒，可能在上游 `task-started` 前触发 `pending audio buffer is full`。缓冲上限现与预览对齐为 960,000 字节（30 秒），仍拒绝超限输入，并保证音频按序发完后再发送 `finish-task`。设备端协议不变。

对话错误按录音初始化、上传、ASR 连接、对话模型、TTS 和超时分类；日志只记录阶段、音频字节数、异常类别及安全提示，不记录音频或上游原始错误文本。结束、打断和失败均收起待识别/待回答占位文字；ASR 累计字幕采用替换而非追加，避免重复。

验证：先用 30 秒录音回归测试复现旧上限异常，再验证修复后的 150 个 6400 字节分片顺序与超限拒绝。使用本机合成中文测试语音与现有配置 `paraformer-realtime-v2 → qwen-plus → qwen3-tts-flash-realtime / Cherry` 进行真实模型联调：同一会话连续提交 30 秒、8 秒音频，两轮均收到非空识别、回答和 PCM 音频。未采集用户麦克风；浏览器权限、扬声器实际播放效果仍需本机试听。

### 识别连接超时与本机代理

本地后台曾出现同一会话第一轮成功、第二轮恰好 10 秒连接超时。Java 自动继承了 macOS 的本机 HTTP 代理，连接阿里云识别服务时存在明显延迟；诊断时直连 HTTPS 三次均约 40–66 ms，HTTP 代理约 1.4–4.9 s，并在实际 WebSocket 调用中超时。独立进程偶尔成功不能替代正在运行的后台接口联调。

新增 `robot.ai.model-network.proxy-mode`，可选 `SYSTEM`（默认，保持已有 JDK 代理行为）或 `DIRECT`（模型客户端直连），由环境变量 `ROBOT_AI_MODEL_PROXY_MODE` 覆盖。本地 `local` profile 默认 `DIRECT`，其他环境默认 `SYSTEM`；均可通过环境变量覆盖。本机运行后端设置为 `DIRECT`。配置作用于 Qwen ASR/TTS/Realtime 和平台对话 HTTP 客户端，不更改系统代理设置。部署环境必须允许相应出口，要求走代理时保留 `SYSTEM`；切换需重启后端，无数据库迁移。

ASR 连接诊断区分超时、鉴权拒绝和其他连接失败；日志仅包含模型 ID、服务主机、错误类别、异常类和 HTTP 状态，不记录凭据或原始错误正文。

切换为 DIRECT 并重启后，使用登录后的真实后台 `/dialogue-sessions/.../turns` 接口连续测试四轮（第一轮完整 30 秒录音，其余 8 秒）：均收到非空识别、回答、音频及 `done`，耗时分别 3.59、2.98、5.07、3.98 秒（音频生成完成，不含本地播完时间）。连接检查确认直接连接服务端 443 端口。47 项相关回归测试与打包通过，前端 5173 和后端健康检查正常。
