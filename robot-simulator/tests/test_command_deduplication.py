import time

import pytest

from ruoyi_robot_simulator.command_handler import CommandHandler, RecordingExecutor
from ruoyi_robot_simulator.envelope import Envelope
from ruoyi_robot_simulator.message_store import ProcessedMessageStore


def mission_start(message_id: str) -> Envelope:
    return Envelope.command(
        message_id=message_id,
        message_type="MISSION_START",
        mission_id=42,
        payload={"actions": [{"id": 1}]},
    )


def mission_cancel(message_id: str, target_start_message_id: str) -> Envelope:
    return Envelope.command(
        message_id=message_id,
        message_type="MISSION_CANCEL",
        mission_id=42,
        payload={"reason": "operator cancelled", "targetStartMessageId": target_start_message_id},
    )


def test_duplicate_message_id_executes_action_once(tmp_path):
    store = ProcessedMessageStore(tmp_path / "messages.sqlite")
    executor = RecordingExecutor()
    handler = CommandHandler(store, executor)

    first = handler.handle(mission_start("01K0DUPLICATE00000000000000"))
    repeated = handler.handle(mission_start("01K0DUPLICATE00000000000000"))

    assert executor.executions == 1
    assert repeated == first


def test_cancel_first_persists_tombstone_and_rejects_later_start(tmp_path):
    store = ProcessedMessageStore(tmp_path / "messages.sqlite")
    executor = RecordingExecutor()
    handler = CommandHandler(store, executor)

    handler.handle(mission_cancel("01K0CANCEL0000000000000000", "01K0START00000000000000000"))
    result = handler.handle(mission_start("01K0START00000000000000000"))

    assert executor.executions == 0
    assert result.type == "MISSION_ACK"
    assert result.data["accepted"] is False
    assert result.data["reason"] == "MISSION_CANCELLED"


def test_restart_claims_unexecuted_reservation_once_after_crash_window(tmp_path):
    path = tmp_path / "messages.sqlite"
    command = mission_start("01K0CRASH00000000000000000")
    first_store = ProcessedMessageStore(path)
    accepted = Envelope.robot(request_id=command.request_id, message_type="MISSION_ACK", data={
        "missionId": 42, "commandMessageId": command.message_id, "accepted": True, "reason": "",
    })
    rejected = Envelope.robot(request_id=command.request_id, message_type="MISSION_ACK", data={
        "missionId": 42, "commandMessageId": command.message_id, "accepted": False, "reason": "MISSION_CANCELLED",
    })
    first_store.reserve_start(command, accepted, rejected)  # process crashes before it claims execution

    executor = RecordingExecutor()
    restarted = CommandHandler(ProcessedMessageStore(path), executor)
    restarted.handle(command)
    restarted.handle(command)

    assert executor.executions == 1


def test_expired_execution_lease_is_recovered_once_after_restart(tmp_path):
    path = tmp_path / "messages.sqlite"
    command = mission_start("01K0LEASE00000000000000000")
    first_store = ProcessedMessageStore(path)
    accepted = Envelope.robot(request_id=command.request_id, message_type="MISSION_ACK", data={
        "missionId": 42, "commandMessageId": command.message_id, "accepted": True, "reason": "",
    })
    rejected = Envelope.robot(request_id=command.request_id, message_type="MISSION_ACK", data={
        "missionId": 42, "commandMessageId": command.message_id, "accepted": False, "reason": "MISSION_CANCELLED",
    })
    first_store.reserve_start(command, accepted, rejected)
    assert first_store.claim_start_execution(command.message_id, lease_millis=1)

    # This models a process dying after it fenced the command but before terminal output.
    time.sleep(0.01)
    executor = RecordingExecutor()
    restarted = CommandHandler(ProcessedMessageStore(path), executor)
    restarted.handle(command)
    restarted.handle(command)

    assert executor.executions == 1


def test_handler_rejects_boolean_mission_id_from_a_bypassed_envelope_factory(tmp_path):
    command = Envelope("01K0A1B2C3D4E5F6G7H8J9K0MN", "request-1", int(time.time() * 1000), 1,
                       "MISSION_START", "CLOUD", {"missionId": True, "payload": {"actions": []}})

    with pytest.raises(ValueError, match="valid missionId"):
        CommandHandler(ProcessedMessageStore(tmp_path / "messages.sqlite")).handle(command)
