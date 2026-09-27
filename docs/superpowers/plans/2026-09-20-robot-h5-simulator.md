# Robot H5 Simulator Implementation Plan

Dependency order; every task uses test-first -> implementation -> test -> self-check -> commit.

1. Scaffold: independent Vue3/Vite/TS H5, router/store/test harness, responsive shell.
2. Protocol core: typed envelope/event log/clock/idempotency/scenario engine.
3. Device auth: real /device-api/auth/token signing flow and safe session credential store.
4. MQTT transport: MQTT-over-WebSocket adapter, topic policy, reconnect, command/ACK console.
5. Device telemetry: heartbeat/status/battery/network/version/location payload simulators.
6. Mission runtime: command dispatch, ACK/progress/result state machine and failure injection.
7. Navigation/motion: pose/velocity/navigation simulator and virtual map panel.
8. Inspection/perception: inspection points and person/face/fall/fire/fridge/water events.
9. Realtime Agent: WSS session/audio/control/barge-in/event console.
10. Digital Human: config/state/viseme/audio/subtitle renderer integrated with realtime.
11. OTA simulator: progress/reboot/success/failure without package execution.
12. Scenario runner: built-in end-to-end scenarios, pause/resume/cancel/reset.
13. Diagnostics: latency/network flap/malformed/duplicate/out-of-order and trace export.
14. Docs + release verification: env/config/runbook, backend contract checks, typecheck/unit/E2E. Never report unexecuted tests as PASS.

No task may add a simulator-only privileged backend endpoint. If an existing robot capability lacks a documented device protocol, first add/verify that protocol in its owning backend module and then consume it from H5.
