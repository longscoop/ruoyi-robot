"""SQLite-backed simulator command facts and cancellation tombstones."""

from __future__ import annotations

import sqlite3
import secrets
import time
from pathlib import Path
from typing import Optional

from .envelope import Envelope


class ProcessedMessageStore:
    """Persist commands before execution so QoS1 redelivery cannot run work twice.

    `BEGIN IMMEDIATE` serializes cancel-first and start delivery across callbacks/processes using
    the same sqlite file. It makes the tombstone check and START reservation one transaction.
    """

    def __init__(self, path: Path):
        self.path = str(path)
        # Each process has a unique fencing identity. A restarted process may recover an unclaimed
        # reservation, while a live process can retain its running lease.
        self.owner = secrets.token_hex(16)
        with self._connect() as connection:
            connection.executescript("""
                CREATE TABLE IF NOT EXISTS processed_message (
                    message_id TEXT PRIMARY KEY,
                    message_type TEXT NOT NULL,
                    response_json TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'COMPLETED',
                    lease_owner TEXT NULL,
                    lease_expires_at INTEGER NULL,
                    created_at INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS start_tombstone (
                    target_start_message_id TEXT PRIMARY KEY,
                    mission_id INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                );
            """)
            columns = {row[1] for row in connection.execute("PRAGMA table_info(processed_message)")}
            # The simulator has no released schema migrations, but preserving old local stores is
            # useful for developers upgrading from the first prototype.
            for name, ddl in (("status", "TEXT NOT NULL DEFAULT 'COMPLETED'"),
                              ("lease_owner", "TEXT NULL"), ("lease_expires_at", "INTEGER NULL")):
                if name not in columns:
                    connection.execute(f"ALTER TABLE processed_message ADD COLUMN {name} {ddl}")

    def reserve_start(self, command: Envelope, accepted: Envelope,
                      rejected: Envelope) -> Envelope:
        """Durably reserve a START before execution; reservation is recoverable after a crash."""
        with self._transaction() as connection:
            duplicate = self._existing(connection, command.message_id)
            if duplicate is not None:
                return duplicate
            blocked = connection.execute(
                "SELECT 1 FROM start_tombstone WHERE target_start_message_id=?", (command.message_id,)
            ).fetchone() is not None
            response = rejected if blocked else accepted
            connection.execute(
                "INSERT INTO processed_message(message_id,message_type,response_json,status,created_at) VALUES (?,?,?,?,?)",
                (command.message_id, command.type, response.to_bytes().decode("utf-8"),
                 "REJECTED" if blocked else "RESERVED", command.timestamp),
            )
            return response

    def claim_start_execution(self, message_id: str, lease_millis: int = 30_000) -> bool:
        """Fence execution with a lease; only a restarted owner can recover an unclaimed row."""
        now = int(time.time() * 1000)
        with self._transaction() as connection:
            row = connection.execute("SELECT status,lease_owner,lease_expires_at FROM processed_message WHERE message_id=?",
                                     (message_id,)).fetchone()
            if row is None or row[0] in {"COMPLETED", "REJECTED"}:
                return False
            status, owner, expires = row
            # RESERVED has no worker yet. RUNNING is reclaimable only after its durable lease.
            if status == "RUNNING" and (owner == self.owner or expires is None or expires > now):
                return False
            changed = connection.execute(
                "UPDATE processed_message SET status='RUNNING',lease_owner=?,lease_expires_at=? "
                "WHERE message_id=? AND status IN ('RESERVED','RUNNING')",
                (self.owner, now + lease_millis, message_id),
            ).rowcount
            return changed == 1

    def complete_start_execution(self, message_id: str) -> None:
        """Only the lease owner may mark this command's terminal work as completed."""
        with self._transaction() as connection:
            changed = connection.execute(
                "UPDATE processed_message SET status='COMPLETED',lease_owner=NULL,lease_expires_at=NULL "
                "WHERE message_id=? AND status='RUNNING' AND lease_owner=?", (message_id, self.owner),
            ).rowcount
            if changed != 1:
                raise RuntimeError("lost simulator execution lease")

    def release_start_execution(self, message_id: str) -> None:
        """Keep a failed output sequence replayable instead of committing an ACK-only START."""
        with self._transaction() as connection:
            connection.execute(
                "UPDATE processed_message SET status='RESERVED',lease_owner=NULL,lease_expires_at=NULL "
                "WHERE message_id=? AND status='RUNNING' AND lease_owner=?", (message_id, self.owner),
            )

    def record_cancel(self, command: Envelope, response: Envelope) -> Envelope:
        """Durably install target START's tombstone before acknowledging cancellation."""
        target = command.data.get("targetStartMessageId")
        with self._transaction() as connection:
            duplicate = self._existing(connection, command.message_id)
            if duplicate is not None:
                return duplicate
            if isinstance(target, str) and target:
                connection.execute(
                    "INSERT OR IGNORE INTO start_tombstone(target_start_message_id,mission_id,created_at) VALUES (?,?,?)",
                    (target, command.data["missionId"], command.timestamp),
                )
            connection.execute(
                "INSERT INTO processed_message(message_id,message_type,response_json,status,created_at) VALUES (?,?,?,?,?)",
                (command.message_id, command.type, response.to_bytes().decode("utf-8"), "COMPLETED", command.timestamp),
            )
            return response

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.path, timeout=10, isolation_level=None)
        connection.execute("PRAGMA journal_mode=WAL")
        return connection

    def _transaction(self):
        class Transaction:
            def __init__(self, store: "ProcessedMessageStore"):
                self.connection = store._connect()
            def __enter__(self):
                self.connection.execute("BEGIN IMMEDIATE")
                return self.connection
            def __exit__(self, kind, value, traceback):
                self.connection.execute("COMMIT" if kind is None else "ROLLBACK")
                self.connection.close()
        return Transaction(self)

    @staticmethod
    def _existing(connection: sqlite3.Connection, message_id: str) -> Optional[Envelope]:
        row = connection.execute("SELECT response_json FROM processed_message WHERE message_id=?", (message_id,)).fetchone()
        return Envelope.from_bytes(row[0].encode("utf-8")) if row else None
