# Robot H5 Simulator Design

Date: 2026-09-20

## Goal
新增独立 `robot-platform-ui-simulator` H5，用浏览器模拟真实 RK3588/Android 机器人端，供云端、MQTT、Mission、Realtime Agent、数字人联调。Simulator 必须走生产设备协议，不增加绕过鉴权/租户/任务边界的测试后门。

## Architecture
Browser Simulator -> device auth -> MQTT over WebSocket / device HTTP / Realtime WSS -> existing backend -> Mission/Agent.
ROS、底盘、传感器仅在浏览器内模拟；云端看到的仍是普通设备。

## Functional workbench
1. Device: SN/credential login, token, online/offline, heartbeat, battery/network/version/status.
2. MQTT: connect/disconnect, subscribed command log, raw payload inspector, ACK/result/status publishing.
3. Mission: automatic/manual ACK, RUNNING/SUCCEEDED/FAILED/CANCELED, progress, timeout/delay/error injection.
4. Motion/navigation: pose, velocity, move/rotate/stop, navigation target, arrived/blocked/localization-lost.
5. Inspection: points, progress, image/event placeholders, anomaly/no-anomaly, continue/abort.
6. Perception: person found/not-found, face/member, fall/fire/fridge/water alerts and confidence.
7. Voice/Realtime Agent: real WSS, session.start, microphone PCM when browser supports it, text/control event console, barge-in.
8. Digital Human: digitalHumanCode, config/state/viseme/audio/subtitle preview.
9. OTA: download/install/reboot/progress/success/failure state simulation; never actually execute packages.
10. Diagnostics: logs, latency, disconnect/reconnect, malformed message, duplicate/out-of-order event scenarios.

## Scenario runner
Built-in scenarios: boot-online, inspection-success, inspection-anomaly, summon, find-person, navigation-failure, fall-alert, realtime-dialogue, barge-in, ota-success, ota-failure, network-flap. Scenario is declarative steps; every step appears in timeline and can pause/resume/cancel.

## Security
Credentials are entered by tester and kept in session storage by default. No tenantId is trusted from UI. No admin token can impersonate a device. Raw MQTT publishing is restricted to topics granted to the authenticated device. UI must visibly mark SIMULATOR source where protocol supports metadata, without changing authorization.

## UI
Mobile/desktop responsive. Left: robot/device summary. Center: feature tabs and virtual robot. Right/bottom: live event timeline with direction, transport, topic/type, correlation/request/session/turn id, payload and latency.

## Acceptance
A developer can run the H5, authenticate one inventory device, connect transports, receive a real command, manually or automatically ACK/complete it, simulate navigation/inspection/perception/OTA, run Realtime Agent + Digital Human, inject failures, export the event trace, and reset the simulator without a physical robot.
