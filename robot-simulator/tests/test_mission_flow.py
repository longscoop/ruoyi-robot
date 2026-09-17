from ruoyi_robot_simulator.command_handler import CommandHandler
from ruoyi_robot_simulator.envelope import Envelope
from ruoyi_robot_simulator.message_store import ProcessedMessageStore
from ruoyi_robot_simulator.simulator import InProcessTransport, RobotSimulator
import pytest
import time


def start(message_id: str) -> Envelope:
    return Envelope.command(
        message_id=message_id,
        message_type="MISSION_START",
        mission_id=99,
        payload={"actions": [{"id": 7}]},
    )


def test_successful_mission_publishes_ack_action_and_result(tmp_path):
    transport = InProcessTransport()
    simulator = RobotSimulator(
        CommandHandler(ProcessedMessageStore(tmp_path / "messages.sqlite")), transport, action_delay=0,
    )

    simulator.handle_command(start("01K0SUCCESS000000000000000"))

    assert [message.type for message in transport.messages] == [
        "MISSION_ACK", "MISSION_EVENT", "MISSION_EVENT", "MISSION_EVENT",
    ]
    assert [message.data["status"] for message in transport.messages[1:]] == [
        "RUNNING", "SUCCESS", "SUCCESS",
    ]


def test_failed_mission_publishes_action_failure_and_result(tmp_path):
    transport = InProcessTransport()
    simulator = RobotSimulator(
        CommandHandler(ProcessedMessageStore(tmp_path / "messages.sqlite")), transport,
        action_delay=0, fail_action=7,
    )

    simulator.handle_command(start("01K0FAILURE000000000000000"))

    assert [message.type for message in transport.messages] == [
        "MISSION_ACK", "MISSION_EVENT", "MISSION_EVENT", "MISSION_EVENT",
    ]
    assert [message.data["status"] for message in transport.messages[1:]] == [
        "RUNNING", "FAILED", "FAILED",
    ]


class FailOnceTransport(InProcessTransport):
    def __init__(self, fail_on_call: int):
        super().__init__()
        self.fail_on_call = fail_on_call
        self.calls = 0

    def publish_state(self, envelope: Envelope) -> None:
        self.calls += 1
        if self.calls == self.fail_on_call:
            raise RuntimeError("simulated qos1 publish failure")
        self.messages.append(envelope)


@pytest.mark.parametrize("fail_on_call", [2, 4])
def test_start_output_failure_releases_reservation_and_duplicate_replays_flow(tmp_path, fail_on_call):
    transport = FailOnceTransport(fail_on_call)
    simulator = RobotSimulator(
        CommandHandler(ProcessedMessageStore(tmp_path / "messages.sqlite")), transport, action_delay=0,
    )
    command = start("01K0RETRY00000000000000000")

    with pytest.raises(RuntimeError, match="qos1 publish failure"):
        simulator.handle_command(command)
    simulator.handle_command(command)

    assert sum(message.type == "MISSION_ACK" for message in transport.messages) == 2
    assert [message.type for message in transport.messages[-4:]] == [
        "MISSION_ACK", "MISSION_EVENT", "MISSION_EVENT", "MISSION_EVENT",
    ]
    assert [message.data["status"] for message in transport.messages[-3:]] == [
        "RUNNING", "SUCCESS", "SUCCESS",
    ]


def test_terminal_failed_action_completes_before_lease_expiry_and_is_never_reexecuted(tmp_path):
    path = tmp_path / "messages.sqlite"
    transport = InProcessTransport()
    command = start("01K0LEASEFAIL00000000000000")
    first = RobotSimulator(CommandHandler(ProcessedMessageStore(path), execution_lease_millis=1), transport,
                           action_delay=0, fail_action=7)

    first.handle_command(command)
    time.sleep(0.01)  # A legacy RUNNING reservation would now be reclaimed after the lease.
    restarted = RobotSimulator(CommandHandler(ProcessedMessageStore(path), execution_lease_millis=1), transport,
                               action_delay=0)
    restarted.handle_command(command)

    assert [message.type for message in transport.messages] == [
        "MISSION_ACK", "MISSION_EVENT", "MISSION_EVENT", "MISSION_EVENT", "MISSION_ACK",
    ]
    assert [message.data["status"] for message in transport.messages[1:4]] == ["RUNNING", "FAILED", "FAILED"]
