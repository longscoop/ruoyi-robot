# Digital Human Renderer Protocol

V1 客户端只需实现平台 Renderer 接口，不依赖任何云厂商 SDK。

## Session

设备继续连接 `/device-api/ai/realtime`。session.start 可携带 `digitalHumanCode` 和 `clientCapabilities.viseme/audioLevelLipSync`。不传时与旧 Realtime Agent 完全兼容。

服务端事件统一包含 `sessionId/sequence/serverTime`，turn 事件同时包含 `turnId`。

## Renderer

```
load(config)
setState(IDLE|LISTENING|THINKING|SPEAKING|EXECUTING|ERROR, actionCode)
pushViseme(timeline)
pushAudioLevel(0..1)
reset()
```

STATIC_2D 为 V1 必选实现。LIVE2D/THREE_D 通过相同接口接入，业务层不得判断具体渲染 SDK。

## Event mapping

- digital_human.config: 加载安全配置快照
- digital_human.state: 切换动作
- assistant.text.delta/done: 字幕
- assistant.audio.started + binary audio: 播放语音
- digital_human.viseme: 有可靠 Provider 时间轴时驱动口型
- playback.stop: 立即停止旧音频并 reset lip sync
- assistant.interrupted: 回到 LISTENING/IDLE，由随后 state 事件决定
- tool.started: EXECUTING
- session.error: ERROR

客户端不得用固定 timeout 猜测 THINKING/SPEAKING；以服务端状态和音频事件为准。

## Android reference boundary

仓库当前没有 Android 客户端工程，因此不虚构 Android module。Android 侧建议定义 `DigitalHumanRenderer` interface，与 Web 的 TypeScript interface 一致；WebSocket transport 只负责协议解码，Renderer 只负责 UI/动画。

后续 Live2D 可实现 `Live2DRenderer`，Unity/3D 可实现独立 `ThreeDRenderer` 或通过 Android Surface/WebView bridge 接入。它们都不能直接访问 Agent、MQTT、ROS 或 Provider secret。

## Interruption

收到 `playback.stop` 后必须同步：停止 AudioTrack/播放器、清空尚未播放的 PCM、取消旧 turn 的 viseme timeline。sequence/turnId 旧于当前 active turn 的事件必须丢弃。

## Security

只加载后端白名单允许的资源 URL；禁止 config_json 注入 JS/HTML；Provider credential 永远不进入设备配置。机器人动作仍走 Agent -> Tool/Skill -> Mission。
