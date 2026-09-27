# H5 Robot Simulator Verification

Implementation plan Tasks 1-13 are represented by executable simulator modules and the workbench. Task 14 documentation is complete.

Static contract review:
- MQTT topic shape matches RobotTopic: robot/tenant/product/device/channel.
- Device subscribes command/ota and publishes state/event only, QoS 1, retain false.
- Envelope matches backend messageId/requestId/timestamp/version/type/source/data.
- Heartbeat fields match HeartbeatPayload.
- Mission ACK/EVENT fields match backend DTOs.
- Device auth calls the existing anonymous device token route and does not send tenantId.
- Realtime uses existing session.start and digitalHumanCode capability extension.
- OTA cannot execute a package.
- No simulator-only privileged backend endpoint was introduced.

Dynamic test status:
This GitHub editing connection cannot install pnpm dependencies or start browser/backend/EMQX. Unit tests were added but pnpm test/type-check/build and live integration were NOT executed in this session and are not claimed PASS.

Release gate:
1. pnpm install --frozen-lockfile (after generating/committing lockfile in the normal dev environment)
2. pnpm type-check
3. pnpm test
4. pnpm build
5. Dedicated test device: auth -> MQTT -> heartbeat -> mission ACK/result
6. Realtime Agent + Digital Human WSS test
7. Network flap and OTA failure scenarios
