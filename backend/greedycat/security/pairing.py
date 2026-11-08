"""Transactional, bounded pairing ledger. Only secret digests are persisted."""

from contextlib import closing, contextmanager
import hashlib
from pathlib import Path
import secrets
import sqlite3
import time
from uuid import uuid4


class PairingDenied(Exception):
    pass


class PairingStore:
    INVITATION_SECONDS = 120
    SESSION_SECONDS = 86400
    MAX_SESSIONS = 32

    def __init__(self, path: Path, clock=time.time):
        self.path = path
        self.clock = clock
        with closing(sqlite3.connect(path)) as db, db:
            if db.execute("PRAGMA user_version").fetchone()[0] not in (0, 1):
                raise ValueError("Unsupported pairing ledger version.")
            db.executescript("""
                CREATE TABLE IF NOT EXISTS clock_guard (id INTEGER PRIMARY KEY CHECK(id=1), last REAL NOT NULL);
                CREATE TABLE IF NOT EXISTS invitation (id INTEGER PRIMARY KEY CHECK(id=1), digest TEXT NOT NULL, expires REAL NOT NULL);
                CREATE TABLE IF NOT EXISTS sessions (id TEXT PRIMARY KEY, digest TEXT UNIQUE NOT NULL, issued REAL NOT NULL, expires REAL NOT NULL);
                PRAGMA user_version=1;
            """)

    @staticmethod
    def _digest(kind: str, secret: str) -> str:
        return hashlib.sha256((kind + ":" + secret).encode("ascii")).hexdigest()

    @contextmanager
    def _transaction(self):
        with closing(sqlite3.connect(self.path, timeout=5)) as db, db:
            db.execute("BEGIN IMMEDIATE")
            now = self.clock()
            previous = db.execute("SELECT last FROM clock_guard WHERE id=1").fetchone()
            if previous and now < previous[0]:
                raise PairingDenied("PC clock moved backwards; restore clock before pairing.")
            db.execute("INSERT OR REPLACE INTO clock_guard VALUES (1, ?)", (now,))
            db.execute("DELETE FROM sessions WHERE expires <= ?", (now,))
            try:
                yield db, now
            except PairingDenied:
                # Preserve expiry cleanup and the time watermark even on denied
                # authentication, so clock rollback cannot resurrect a token.
                db.commit()
                raise

    def invite(self) -> dict:
        secret = secrets.token_urlsafe(32)
        with self._transaction() as (db, now):
            expires = now + self.INVITATION_SECONDS
            db.execute("INSERT OR REPLACE INTO invitation VALUES (1, ?, ?)",
                       (self._digest("invite", secret), expires))
        return {"invitation": secret, "expires_at": expires}

    def exchange(self, secret: str) -> dict:
        with self._transaction() as (db, now):
            row = db.execute("SELECT digest, expires FROM invitation WHERE id=1").fetchone()
            if not row or row[1] <= now or not secrets.compare_digest(row[0], self._digest("invite", secret)):
                raise PairingDenied("Invalid invitation.")
            if db.execute("SELECT count(*) FROM sessions").fetchone()[0] >= self.MAX_SESSIONS:
                raise PairingDenied("Session limit reached.")
            token, identity = secrets.token_urlsafe(32), str(uuid4())
            expires = now + self.SESSION_SECONDS
            db.execute("INSERT INTO sessions VALUES (?, ?, ?, ?)",
                       (identity, self._digest("token", token), now, expires))
            db.execute("DELETE FROM invitation")
        return {"schema_version": "pairing-1", "pairing_id": identity, "token": token,
                "issued_at": now, "expires_at": expires}

    def authenticate(self, token: str, *, revoke=False) -> dict:
        with self._transaction() as (db, now):
            row = db.execute("SELECT id, issued, expires FROM sessions WHERE digest=?",
                             (self._digest("token", token),)).fetchone()
            if not row or row[2] <= now:
                raise PairingDenied("Invalid credential.")
            if revoke:
                db.execute("DELETE FROM sessions WHERE id=?", (row[0],))
        return {"schema_version": "pairing-1", "pairing_id": row[0], "issued_at": row[1],
                "expires_at": row[2], "status": "REVOKED" if revoke else "PAIRED"}

    def list_sessions(self) -> list[dict]:
        with self._transaction() as (db, _):
            return [dict(pairing_id=row[0], issued_at=row[1], expires_at=row[2])
                    for row in db.execute("SELECT id, issued, expires FROM sessions ORDER BY issued")]

    def cancel_invitation(self) -> None:
        with self._transaction() as (db, _):
            db.execute("DELETE FROM invitation")

    def revoke(self, identity: str) -> bool:
        with self._transaction() as (db, _):
            return db.execute("DELETE FROM sessions WHERE id=?", (identity,)).rowcount == 1
