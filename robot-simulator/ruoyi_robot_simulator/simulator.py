"""Mission simulator and safe CLI; all cloud interaction goes over MQTT envelopes."""

from __future__ import annotations

import argparse
import logging
import os
import signal
import threading
import time
from typing import Protocol

from .command_handler import CommandHandler
from .envelope import Envelope, is_positive_signed_long
from .message_store import ProcessedMessageStore
from .topics import robot_topic


class Transport(Protocol):
    def publish_state(self, envelope: Envelope) -> None: ...


class InProcessTransport:
    """Contract-test transport that captures the exact envelopes the broker would receive."""
    def __init__(self):
        self.messages: list[Envelope] = []
    def publish_state(self, envelope: Envelope) -> None:
        self.messages.append(envelope)


class RobotSimulator:
    def __init__(self, handler: CommandHandler, transport: Transport, *, action_delay: float = 1,
                 fail_action: int | None = None, robot_id: int = 1):
        self.handler = handler
        self.transport = transport
        self.action_delay = action_delay
        self.fail_action = fail_action
        self.robot_id = robot_id

    def handle_command(self, command: Envelope) -> None:
        acknowledgement = self.handler.handle(command)
        try:
            # Each call waits for QoS1 confirmation. Failure must leave the reservation replayable.
            self.transport.publish_state(acknowledgement)
            if command.type != "MISSION_START" or not acknowledgement.data["accepted"]:
                return
            if not self.handler.consume_started(command.message_id):
                return
            actions = command.data.get("payload", {}).get("actions", [])
            terminal_status, terminal_error_code, terminal_error_message = "SUCCESS", None, None
            for action in actions:
                action_id = action.get("id") if type(action) is dict else None
                if not is_positive_signed_long(action_id):
                    terminal_status, terminal_error_code, terminal_error_message = (
                        "FAILED", "INVALID_ACTION", "action id is required")
                    break
                self._action(command, action_id, "RUNNING", None, None)
                if self.action_delay:
                    time.sleep(self.action_delay)
                if self.fail_action == action_id:
                    self._action(command, action_id, "FAILED", "SIMULATED_ACTION_FAILURE", "configured failure")
                    terminal_status, terminal_error_code, terminal_error_message = (
                        "FAILED", "SIMULATED_ACTION_FAILURE", "configured failure")
                    break
                self._action(command, action_id, "SUCCESS", None, None)
            # A terminal result is durable only after both the action state and this result obtain
            # QoS1 acknowledgement; otherwise the except block releases the reservation for replay.
            self._result(command, terminal_status, terminal_error_code, terminal_error_message)
            self.handler.complete_started(command.message_id)
        except Exception:
            self.handler.release_started(command.message_id)
            raise

    def heartbeat(self) -> Envelope:
        return Envelope.robot(request_id="heartbeat", message_type="HEARTBEAT", data={
            "robotId": self.robot_id, "battery": 100, "cpuUsage": 1, "memoryUsage": 1,
            "temperatureCelsius": 25, "workStatus": "IDLE", "ipAddress": "127.0.0.1",
            "currentMissionId": None, "softwareVersion": "simulator-0.1",
        })

    def _action(self, command: Envelope, action_id: int, status: str, error_code: str | None,
                error_message: str | None) -> None:
        self.transport.publish_state(Envelope.robot(request_id=command.request_id, message_type="MISSION_EVENT", data={
            "kind": "ACTION", "missionId": command.data["missionId"], "actionId": action_id,
            "status": status, "errorCode": error_code, "errorMessage": error_message,
        }))

    def _result(self, command: Envelope, status: str, error_code: str | None,
                error_message: str | None) -> None:
        self.transport.publish_state(Envelope.robot(request_id=command.request_id, message_type="MISSION_EVENT", data={
            "kind": "RESULT", "missionId": command.data["missionId"], "actionId": None,
            "status": status, "errorCode": error_code, "errorMessage": error_message,
        }))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="RuoYi Robot MQTT protocol simulator")
    parser.add_argument("--sn", required=True, help="device serial number")
    parser.add_argument("--broker", default=os.getenv("MQTT_BROKER", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("MQTT_PORT", "1883")))
    parser.add_argument("--mqtt-secret", default=os.getenv("MQTT_SECRET"), help="MQTT password; never logged")
    parser.add_argument("--heartbeat-seconds", type=float, default=10)
    parser.add_argument("--action-delay", type=float, default=1)
    parser.add_argument("--publish-timeout-seconds", type=float,
                        default=float(os.getenv("MQTT_PUBLISH_TIMEOUT_SECONDS", "5")))
    parser.add_argument("--fail-action", type=int)
    parser.add_argument("--tenant-namespace", default=os.getenv("ROBOT_TENANT_NAMESPACE", "tenant"))
    parser.add_argument("--product-key", default=os.getenv("ROBOT_PRODUCT_KEY", "product"))
    parser.add_argument("--robot-id", type=int, default=int(os.getenv("ROBOT_ID", "1")))
    parser.add_argument("--store", default=os.getenv("ROBOT_SIMULATOR_STORE", "messages.sqlite"))
    args = parser.parse_args(argv)
    if args.heartbeat_seconds <= 0 or args.action_delay < 0 or args.publish_timeout_seconds <= 0:
        parser.error("heartbeat/publish timeout must be positive and action-delay cannot be negative")
    logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"), format="%(asctime)s %(levelname)s %(message)s")
    from .mqtt_transport import PahoMqttTransport
    transport = PahoMqttTransport(args.broker, args.port, args.tenant_namespace, args.product_key, args.sn,
                                  os.getenv("MQTT_USERNAME"), args.mqtt_secret,
                                  publish_timeout_seconds=args.publish_timeout_seconds)
    simulator = RobotSimulator(CommandHandler(ProcessedMessageStore(__import__("pathlib").Path(args.store))), transport,
                               action_delay=args.action_delay, fail_action=args.fail_action, robot_id=args.robot_id)
    transport.on_command = simulator.handle_command
    transport.start()
    stopping = threading.Event()
    signal.signal(signal.SIGINT, lambda *_: stopping.set())
    signal.signal(signal.SIGTERM, lambda *_: stopping.set())
    try:
        while not stopping.wait(args.heartbeat_seconds):
            transport.publish_state(simulator.heartbeat())
    finally:
        transport.stop()
    return 0
