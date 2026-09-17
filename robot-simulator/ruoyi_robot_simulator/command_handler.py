"""Exactly-once command admission independent of MQTT transport details."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

from .envelope import Envelope, has_valid_mission_data, is_positive_signed_long
from .message_store import ProcessedMessageStore


class StartExecutor(Protocol):
    def execute(self, command: Envelope) -> None: ...


@dataclass
class RecordingExecutor:
    executions: int = 0

    def execute(self, command: Envelope) -> None:
        self.executions += 1


class CommandHandler:
    def __init__(self, store: ProcessedMessageStore, executor: StartExecutor | None = None,
                 *, execution_lease_millis: int = 30_000):
        if type(execution_lease_millis) is not int or execution_lease_millis <= 0:
            raise ValueError("execution lease must be a positive integer")
        self.store = store
        self.executor = executor
        self.execution_lease_millis = execution_lease_millis
        self._new_start_ids: set[str] = set()
        self._active_start_ids: set[str] = set()

    def handle(self, command: Envelope) -> Envelope:
        if command.source != "CLOUD" or command.type not in {"MISSION_START", "MISSION_CANCEL"}:
            raise ValueError("only cloud mission commands are accepted")
        mission_id = command.data.get("missionId")
        if not is_positive_signed_long(mission_id) or not has_valid_mission_data(command.type, command.data):
            raise ValueError("mission command lacks a valid missionId")
        if command.type == "MISSION_CANCEL":
            return self.store.record_cancel(command, self._ack(command, True, ""))
        response = self.store.reserve_start(
            command, self._ack(command, True, ""), self._ack(command, False, "MISSION_CANCELLED"),
        )
        if response.data["accepted"] and self.store.claim_start_execution(
                command.message_id, lease_millis=self.execution_lease_millis):
            self._new_start_ids.add(command.message_id)
            self._active_start_ids.add(command.message_id)
            if self.executor is not None:
                try:
                    self.executor.execute(command)
                    self.store.complete_start_execution(command.message_id)
                except Exception:
                    self.store.release_start_execution(command.message_id)
                    raise
                finally:
                    self._new_start_ids.discard(command.message_id)
                    self._active_start_ids.discard(command.message_id)
        return response

    def consume_started(self, message_id: str) -> bool:
        """Allow a transport loop to emit progress only for newly reserved START commands."""
        if message_id not in self._new_start_ids:
            return False
        self._new_start_ids.remove(message_id)
        return True

    def complete_started(self, message_id: str) -> None:
        """The transport loop calls this after its terminal mission event is published."""
        self.store.complete_start_execution(message_id)
        self._active_start_ids.discard(message_id)

    def release_started(self, message_id: str) -> None:
        """Make an interrupted output flow claimable on duplicate delivery or process restart."""
        if message_id in self._active_start_ids:
            self.store.release_start_execution(message_id)
            self._active_start_ids.discard(message_id)
            self._new_start_ids.discard(message_id)

    @staticmethod
    def _ack(command: Envelope, accepted: bool, reason: str) -> Envelope:
        return Envelope.robot(request_id=command.request_id, message_type="MISSION_ACK", data={
            "missionId": command.data["missionId"], "commandMessageId": command.message_id,
            "accepted": accepted, "reason": reason,
        })
