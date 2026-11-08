from contextlib import closing
import hashlib
import json
from pathlib import Path
import sqlite3
import time

from jsonschema import Draft202012Validator
from greedycat.contracts import CHECKER, load_record

SCHEMA = Path(__file__).resolve().parents[3] / "contracts/sync-v1/review.schema.json"
VALIDATOR = Draft202012Validator(json.loads(SCHEMA.read_text(encoding="utf-8")), format_checker=CHECKER)


class SyncConflict(Exception):
    def __init__(self, code, retryable=False):
        self.code, self.retryable = code, retryable


def validate(payload_json, event_id, digest):
    if len(payload_json.encode("utf-8")) > 8192 or hashlib.sha256(payload_json.encode("utf-8")).hexdigest() != digest:
        raise ValueError("Invalid payload checksum/size.")
    payload = load_record(payload_json)
    if not VALIDATOR.is_valid(payload):
        raise ValueError("Invalid review contract.")
    if payload["review_id"] != event_id or payload["observed_at"] > payload["confirmed_at"]:
        raise ValueError("Invalid review identity/time.")
    evidence = payload["evidence"]
    if evidence["imported_at"] > payload["confirmed_at"]:
        raise ValueError("Evidence was not imported before confirmation.")
    if evidence["mime"].startswith("image/"):
        if evidence["duration_ms"] is not None or evidence["video_offset_ms"] is not None:
            raise ValueError("Image has video metadata.")
    elif evidence["duration_ms"] is None or evidence["video_offset_ms"] is None or evidence["video_offset_ms"] >= evidence["duration_ms"]:
        raise ValueError("Invalid video position.")
    if (payload["revision"] == 1) != (payload["previous_id"] is None):
        raise ValueError("Invalid revision predecessor.")
    return payload


class SyncStore:
    MAX_EVENTS = 100000
    MAX_CONFLICTS = 1000

    def __init__(self, path: Path):
        self.path = path
        with closing(self.connect()) as db:
            version = db.execute("PRAGMA user_version").fetchone()[0]
            if version not in (0, 1):
                raise ValueError("Unsupported synchronized research schema.")
            db.executescript("""
                CREATE TABLE IF NOT EXISTS sync_rounds (
                  owner TEXT NOT NULL, source TEXT NOT NULL, id TEXT NOT NULL,
                  context TEXT NOT NULL, mode TEXT NOT NULL, provenance TEXT NOT NULL, number TEXT NOT NULL,
                  PRIMARY KEY(owner, source, id), UNIQUE(owner, source, context, mode, provenance, number));
                CREATE TABLE IF NOT EXISTS sync_events (
                  owner TEXT NOT NULL, source TEXT NOT NULL, id TEXT NOT NULL,
                  sha256 TEXT NOT NULL, payload TEXT NOT NULL, received REAL NOT NULL,
                  PRIMARY KEY(owner, source, id));
                CREATE TABLE IF NOT EXISTS sync_reviews (
                  owner TEXT NOT NULL, source TEXT NOT NULL, id TEXT NOT NULL, round_id TEXT NOT NULL,
                  revision INTEGER NOT NULL, previous_id TEXT, value TEXT NOT NULL,
                  observed TEXT NOT NULL, confirmed TEXT NOT NULL, evidence_id TEXT NOT NULL,
                  PRIMARY KEY(owner, source, id), UNIQUE(owner, source, round_id, revision),
                  UNIQUE(owner, source, previous_id),
                  FOREIGN KEY(owner, source, round_id) REFERENCES sync_rounds(owner, source, id));
                CREATE INDEX IF NOT EXISTS sync_reviews_evidence ON sync_reviews(owner, source, evidence_id);
                CREATE TABLE IF NOT EXISTS sync_conflicts (
                  owner TEXT NOT NULL, source TEXT NOT NULL, event_id TEXT NOT NULL,
                  attempted_sha256 TEXT NOT NULL, code TEXT NOT NULL, received REAL NOT NULL,
                  PRIMARY KEY(owner, source, event_id, attempted_sha256, code));
                PRAGMA user_version=1;
            """)
            for table in ("sync_rounds", "sync_events", "sync_reviews", "sync_conflicts"):
                for operation in ("UPDATE", "DELETE"):
                    db.execute(f"CREATE TRIGGER IF NOT EXISTS {table}_no_{operation.lower()} BEFORE {operation} ON {table} BEGIN SELECT RAISE(ABORT, 'Synchronized records are append-only'); END")

    def connect(self):
        db = sqlite3.connect(self.path, timeout=5)
        db.execute("PRAGMA foreign_keys=ON")
        return db

    def ingest(self, owner: str, source: str, event_id: str, digest: str, payload_json: str):
        p = validate(payload_json, event_id, digest)
        with closing(self.connect()) as db, db:
            db.execute("BEGIN IMMEDIATE")
            scope = (owner, source)
            def conflict(code, retryable=False):
                if db.execute("SELECT count(*) FROM sync_conflicts").fetchone()[0] < self.MAX_CONFLICTS:
                    db.execute("INSERT OR IGNORE INTO sync_conflicts VALUES (?,?,?,?,?,?)", (*scope, event_id, digest, code, time.time()))
                    db.commit()
                raise SyncConflict(code, retryable)
            old = db.execute("SELECT sha256 FROM sync_events WHERE owner=? AND source=? AND id=?", (*scope, event_id)).fetchone()
            if old:
                if old[0] != digest:
                    conflict("EVENT_CONFLICT")
                return self.ack(event_id, digest, "REPLAY")
            if db.execute("SELECT count(*) FROM sync_events").fetchone()[0] >= self.MAX_EVENTS:
                conflict("STORAGE_LIMIT", True)
            identity = (p["context_id"], p["mode"], p["provenance"], p["round_number"])
            old_round = db.execute("SELECT context,mode,provenance,number FROM sync_rounds WHERE owner=? AND source=? AND id=?", (*scope, p["round_id"])).fetchone()
            if old_round is not None and old_round != identity:
                conflict("ROUND_IDENTITY_CONFLICT")
            natural = db.execute("SELECT id FROM sync_rounds WHERE owner=? AND source=? AND context=? AND mode=? AND provenance=? AND number=?", (*scope, *identity)).fetchone()
            if natural and natural[0] != p["round_id"]:
                conflict("ROUND_NUMBER_CONFLICT")
            latest = db.execute("SELECT id,revision,confirmed FROM sync_reviews WHERE owner=? AND source=? AND round_id=? ORDER BY revision DESC LIMIT 1", (*scope, p["round_id"])).fetchone()
            if p["revision"] > 1 and latest is None:
                conflict("MISSING_PREDECESSOR", True)
            if latest and p["revision"] > latest[1] + 1:
                conflict("MISSING_PREDECESSOR", True)
            if latest and (p["previous_id"] != latest[0] or p["revision"] != latest[1] + 1):
                conflict("REVISION_CONFLICT")
            if latest and p["confirmed_at"] < latest[2]:
                conflict("CORRECTION_TIME_CONFLICT")
            evidence = p["evidence"]
            existing_evidence = db.execute("SELECT e.payload FROM sync_reviews r JOIN sync_events e ON r.owner=e.owner AND r.source=e.source AND r.id=e.id WHERE r.owner=? AND r.source=? AND r.evidence_id=? LIMIT 1", (*scope, evidence["id"])).fetchone()
            if existing_evidence:
                old_evidence = json.loads(existing_evidence[0])["evidence"]
                if any(old_evidence[k] != evidence[k] for k in evidence if k != "video_offset_ms"):
                    conflict("EVIDENCE_IDENTITY_CONFLICT")
            if old_round is None:
                db.execute("INSERT INTO sync_rounds VALUES (?,?,?,?,?,?,?)", (*scope, p["round_id"], *identity))
            db.execute("INSERT INTO sync_events VALUES (?,?,?,?,?,?)", (*scope, event_id, digest, payload_json, time.time()))
            db.execute("INSERT INTO sync_reviews VALUES (?,?,?,?,?,?,?,?,?,?)", (*scope, event_id, p["round_id"], p["revision"], p["previous_id"], p["value"], p["observed_at"], p["confirmed_at"], evidence["id"]))
        return self.ack(event_id, digest, "STORED")

    @staticmethod
    def ack(event_id, digest, status):
        return dict(schema_version="sync-1", event_id=event_id, payload_sha256=digest, status=status)
