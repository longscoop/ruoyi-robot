# Robot Platform H5 Simulator

浏览器机器人端模拟器，用于在没有 RK3588/Android/ROS 真机时联调云端。

## Run
```bash
cd robot-platform-ui-simulator
pnpm install
pnpm dev
```

## Real protocol configuration
- Device HTTP base URL: backend address.
- Device login: `POST /device-api/auth/token`, HMAC-SHA256 proof is generated in browser; deviceSecret is not persisted.
- MQTT: EMQX WebSocket URL + server-issued MQTT username/password. Canonical topics are `robot/{tenantNamespace}/{productKey}/{deviceSn}/{command|ota|state|event}`, QoS 1, no retained robot publish.
- Realtime: `/device-api/ai/realtime` WSS with device token and existing session.start protocol.

Do not use an admin token as a device credential. The simulator intentionally has no tenant selector that changes authorization.

## Coverage
Device auth/heartbeat, MQTT, mission ACK/progress/result, navigation/motion, inspection, person/face/fall/fire/fridge/water perception, Realtime Agent, Digital Human, OTA state simulation, network/failure diagnostics, built-in scenario runner and trace export.

OTA simulation never downloads or executes packages. Sensor/image events are synthetic payloads; they are explicitly simulator events rather than fabricated backend results.

## Verification
```bash
pnpm type-check
pnpm test
pnpm build
```
For live integration, configure a dedicated test device and run scenarios against a non-production EMQX/backend first.
