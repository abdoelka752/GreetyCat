from contextlib import closing
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import sqlite3
from tempfile import TemporaryDirectory
import unittest
from uuid import uuid4

import httpx
from greedycat.api.pairing import create_pairing_app
from greedycat.security.pairing import PairingStore
from greedycat.sync.ingest import SyncStore, SyncConflict, validate

FIXTURE = Path(__file__).resolve().parents[2] / "contracts/sync-v1/fixtures/review.synthetic.json"


def envelope(p):
    raw = json.dumps(p, ensure_ascii=False, separators=(",", ":"))
    return dict(schema_version="sync-1", source_id="858c9680-f8b8-416d-90f8-1530a0a4c534", event_id=p["review_id"],
                payload_json=raw, payload_sha256=hashlib.sha256(raw.encode()).hexdigest())


class IngestionTests(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "research.sqlite3"
        self.store = SyncStore(self.path)
        self.p = json.loads(FIXTURE.read_text())
        self.owner = str(uuid4())

    def send(self, p=None, store=None, owner=None):
        e = envelope(p or self.p)
        return (store or self.store).ingest(owner or self.owner, e["source_id"], e["event_id"], e["payload_sha256"], e["payload_json"])

    def counts(self):
        with closing(self.store.connect()) as db:
            return tuple(db.execute(f"SELECT count(*) FROM {table}").fetchone()[0] for table in ("sync_rounds", "sync_events", "sync_reviews", "sync_conflicts"))

    def correction(self, original=None):
        old = original or self.p
        return {**deepcopy(old), "review_id": str(uuid4()), "revision": old["revision"] + 1,
                "previous_id": old["review_id"], "value": "TOMATO", "confirmed_at": "2026-01-01T12:02:00.000Z",
                "reason": "Synthetic audited correction, not game data."}

    def test_commit_before_lost_ack_replay_survives_reopen(self):
        self.send()  # Pretend this response never arrived at the phone.
        result = self.send(store=SyncStore(self.path))
        self.assertEqual("REPLAY", result["status"])
        self.assertEqual(envelope(self.p)["payload_sha256"], result["payload_sha256"])
        self.assertEqual((1, 1, 1, 0), self.counts())

    def test_corrections_are_append_only_and_stale_branch_is_audited(self):
        self.send()
        correction = self.correction()
        self.send(correction)
        with self.assertRaises(SyncConflict) as error:
            self.send(self.correction())
        self.assertEqual("REVISION_CONFLICT", error.exception.code)
        self.assertEqual((1, 2, 2, 1), self.counts())
        with closing(self.store.connect()) as db:
            self.assertEqual([("CORN",), ("TOMATO",)], db.execute("SELECT value FROM sync_reviews ORDER BY revision").fetchall())
            for table in ("sync_rounds", "sync_events", "sync_reviews", "sync_conflicts"):
                with self.assertRaises(sqlite3.IntegrityError):
                    db.execute(f"DELETE FROM {table}")

    def test_out_of_order_predecessors_can_be_retried(self):
        second = self.correction()
        third = self.correction(second)
        for p in (second, third):
            with self.assertRaises(SyncConflict) as error:
                self.send(p)
            self.assertTrue(error.exception.retryable)
        self.send()
        with self.assertRaises(SyncConflict) as error:
            self.send(third)
        self.assertEqual("MISSING_PREDECESSOR", error.exception.code)
        self.send(second)
        self.send(third)
        self.assertEqual(3, self.counts()[1])

    def test_changed_event_round_and_evidence_are_never_overwritten(self):
        self.send()
        changed = {**self.p, "value": "PIZZA"}
        with self.assertRaises(SyncConflict) as error:
            self.send(changed)
        self.assertEqual("EVENT_CONFLICT", error.exception.code)
        changed = self.correction()
        changed["mode"] = "PROFESSIONAL"
        with self.assertRaises(SyncConflict) as error:
            self.send(changed)
        self.assertEqual("ROUND_IDENTITY_CONFLICT", error.exception.code)
        changed = self.correction()
        changed["evidence"]["sha256"] = "b" * 64
        with self.assertRaises(SyncConflict) as error:
            self.send(changed)
        self.assertEqual("EVIDENCE_IDENTITY_CONFLICT", error.exception.code)
        self.assertEqual((1, 1, 1, 3), self.counts())

    def test_natural_round_collision_and_separate_pairings(self):
        self.send()
        changed = {**self.p, "review_id": str(uuid4()), "round_id": str(uuid4())}
        with self.assertRaises(SyncConflict) as error:
            self.send(changed)
        self.assertEqual("ROUND_NUMBER_CONFLICT", error.exception.code)
        self.send({**self.p, "value": "TOMATO"}, owner=str(uuid4()))
        self.assertEqual(2, self.counts()[1])

    def test_transaction_failure_leaves_no_partial_round_or_event(self):
        with closing(self.store.connect()) as db:
            db.execute("CREATE TRIGGER fail_fixture BEFORE INSERT ON sync_reviews BEGIN SELECT RAISE(ABORT, 'Synthetic failure'); END")
        with self.assertRaises(sqlite3.IntegrityError):
            self.send()
        self.assertEqual((0, 0, 0, 0), self.counts())

    def test_invalid_payloads_checksum_duplicates_and_timing_are_rejected(self):
        cases = [{**self.p, "observed_at": "2026-02-31T12:00:00.000Z"},
                 {**self.p, "confirmed_at": "2025-01-01T12:00:00.000Z"},
                 {**self.p, "round_number": "001"}, {**self.p, "extra": "private"},
                 {**self.p, "revision": 2}, {**self.p, "provenance": "unknown"}]
        bad_video = deepcopy(self.p)
        bad_video["evidence"].update(mime="video/mp4", duration_ms=10, video_offset_ms=10)
        cases.append(bad_video)
        for p in cases:
            with self.assertRaises(ValueError):
                self.send(p)
        e = envelope(self.p)
        with self.assertRaises(ValueError):
            validate(e["payload_json"], e["event_id"], "0" * 64)
        raw = e["payload_json"][:-1] + ',"value":"PIZZA"}'
        with self.assertRaises(ValueError):
            validate(raw, e["event_id"], hashlib.sha256(raw.encode()).hexdigest())
        self.assertEqual((0, 0, 0, 0), self.counts())

    def test_capacity_and_conflict_log_are_bounded_and_future_version_preserved(self):
        self.store.MAX_EVENTS = 1
        self.store.MAX_CONFLICTS = 1
        self.send()
        for number in ("101", "102"):
            changed = {**self.p, "review_id": str(uuid4()), "round_id": str(uuid4()), "round_number": number}
            with self.assertRaises(SyncConflict) as error:
                self.send(changed)
            self.assertEqual("STORAGE_LIMIT", error.exception.code)
        self.assertEqual((1, 1, 1, 1), self.counts())
        with closing(self.store.connect()) as db:
            db.execute("PRAGMA user_version=2")
        with self.assertRaises(ValueError):
            SyncStore(self.path)


class SyncApiTests(unittest.IsolatedAsyncioTestCase):
    async def test_authenticated_endpoint_ack_replay_revoke_and_payload_limit(self):
        with TemporaryDirectory() as name:
            store = PairingStore(Path(name) / "pairing.sqlite3")
            app = create_pairing_app(store)
            c = store.exchange(store.invite()["invitation"])
            e = envelope(json.loads(FIXTURE.read_text()))
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app, client=("127.0.0.1", 1)), base_url="https://127.0.0.1:8767") as client:
                url = "/api/sync/v1/reviews"
                self.assertEqual(401, (await client.post(url, json=e)).status_code)
                headers = {"Authorization": "Bearer " + c["token"]}
                first = await client.post(url, json=e, headers=headers)
                self.assertEqual("STORED", first.json()["status"])
                self.assertEqual("REPLAY", (await client.post(url, json=e, headers=headers)).json()["status"])
                changed = envelope({**json.loads(e["payload_json"]), "value": "PIZZA"})
                conflict = await client.post(url, json=changed, headers=headers)
                self.assertEqual(409, conflict.status_code)
                self.assertEqual("EVENT_CONFLICT", conflict.json()["code"])
                self.assertEqual("no-store", conflict.headers["cache-control"])
                bad = {**e, "payload_sha256": "0" * 64}
                self.assertEqual(422, (await client.post(url, json=bad, headers=headers)).status_code)
                self.assertEqual(413, (await client.post(url, content=b"x" * 16385, headers=headers)).status_code)
                store.revoke(c["pairing_id"])
                self.assertEqual(401, (await client.post(url, json=e, headers=headers)).status_code)
