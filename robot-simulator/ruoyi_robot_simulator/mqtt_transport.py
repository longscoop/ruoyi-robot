"""QoS1 Paho transport; credentials are used only by Paho and never logged."""

from __future__ import annotations

import logging
from concurrent.futures import CancelledError, Future, ThreadPoolExecutor
from typing import Callable

from .envelope import Envelope
from .topics import robot_topic

log = logging.getLogger(__name__)


class PahoMqttTransport:
    def __init__(self, broker: str, port: int, tenant_namespace: str, product_key: str, device_sn: str,
                 username: str | None, secret: str | None, *, publish_timeout_seconds: float = 5.0):
        if type(publish_timeout_seconds) not in {int, float} or publish_timeout_seconds <= 0:
            raise ValueError("publish timeout must be positive")
        try:
            import paho.mqtt.client as mqtt
        except ImportError as error:
            raise RuntimeError("install paho-mqtt to use the real MQTT simulator") from error
        self._mqtt = mqtt
        self.command_topic = robot_topic(tenant_namespace, product_key, device_sn, "COMMAND")
        self.state_topic = robot_topic(tenant_namespace, product_key, device_sn, "STATE")
        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"sim-{device_sn}")
        if username:
            self.client.username_pw_set(username, secret)
        elif secret:
            # MQTT brokers generally need a username too; reject rather than accidentally log/use it wrongly.
            raise ValueError("MQTT_USERNAME is required when mqtt-secret is supplied")
        self.broker, self.port = broker, port
        self.publish_timeout_seconds = float(publish_timeout_seconds)
        self.on_command: Callable[[Envelope], None] | None = None
        # Paho invokes on_message from its network loop. Keep one ordered worker so a command's
        # QoS1 PUBACK can still be processed while the handler waits for publish confirmation.
        self.command_executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="robot-mqtt-command")
        self.client.on_connect = self._connected
        self.client.on_message = self._message

    def start(self) -> None:
        self.client.connect(self.broker, self.port, keepalive=30)
        self.client.loop_start()

    def stop(self) -> None:
        # Do not block shutdown on queued work. A running publish has its own bounded PUBACK wait.
        self.command_executor.shutdown(wait=False, cancel_futures=True)
        self.client.loop_stop()
        self.client.disconnect()

    def publish_state(self, envelope: Envelope) -> None:
        result = self.client.publish(self.state_topic, envelope.to_bytes(), qos=1)
        if result.rc != self._mqtt.MQTT_ERR_SUCCESS:
            raise RuntimeError("MQTT state publish was not accepted by the client")
        try:
            # QoS1 is not complete until Paho observes the PUBACK. A bounded wait prevents a
            # stalled broker connection from indefinitely blocking the command callback loop.
            result.wait_for_publish(timeout=self.publish_timeout_seconds)
            published = result.is_published()
        except (RuntimeError, ValueError) as error:
            # Never surface payload/credential text carried by an untrusted Paho exception.
            raise RuntimeError("MQTT state publish failed") from error
        if not published:
            raise RuntimeError("MQTT state publish timed out waiting for broker acknowledgement")

    def _connected(self, client, userdata, flags, reason_code, properties) -> None:
        if reason_code.is_failure:
            log.error("MQTT connection rejected: %s", reason_code)
            return
        client.subscribe(self.command_topic, qos=1)
        log.info("simulator subscribed to canonical robot command topic")

    def _message(self, client, userdata, message) -> None:
        if message.topic != self.command_topic:
            return
        try:
            envelope = Envelope.from_bytes(message.payload)
        except Exception:
            # Payload text can be untrusted and may contain a secret; do not include it in logs.
            log.exception("discarded invalid MQTT command envelope")
            return
        if self.on_command:
            try:
                future = self.command_executor.submit(self.on_command, envelope)
                future.add_done_callback(self._observe_command)
            except RuntimeError:
                # This occurs only during shutdown; do not log untrusted command content.
                log.warning("discarded MQTT command because the command worker is unavailable")

    @staticmethod
    def _observe_command(future: Future[object]) -> None:
        try:
            future.result()
        except CancelledError:
            log.info("cancelled queued MQTT command during shutdown")
        except Exception:
            # The envelope and exception can contain untrusted fields; log only a stable fact.
            log.error("MQTT command handler failed; durable reservation remains replayable")
