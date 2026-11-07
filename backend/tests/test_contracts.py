"""Conformance fixtures and adversarial integrity cases; no device/server needed."""

from copy import deepcopy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from greedycat.contracts import (
    ContractError, compare_replay, load_record, validate_live_result,
    validate_prediction_context, validate_record,
)

FIXTURES = Path(__file__).resolve().parents[2] / "contracts" / "fixtures"


def fixture(name):
    return load_record((FIXTURES / name).read_text(encoding="utf-8"))


def amend(record, changes=None, remove=()):
    result = deepcopy(record)
    for pointer, value in (changes or {}).items():
        parts = pointer.strip("/").split("/")
        target = result
        for part in parts[:-1]:
            target = target[part]
        target[parts[-1]] = value
    for pointer in remove:
        parts = pointer.strip("/").split("/")
        target = result
        for part in parts[:-1]:
            target = target[part]
        del target[parts[-1]]
    return result


class ContractsTest(unittest.TestCase):
    def assert_rejected(self, code, function, *args):
        with self.assertRaises(ContractError) as caught:
            function(*args)
        self.assertEqual(code, caught.exception.code)
        validate_record("errors", caught.exception.as_dict())
        return caught.exception

    def test_shared_valid_records_offline_and_unchanged(self):
        for kind, name in (
            ("event", "event-snapshot.json"), ("round", "round-open.json"),
            ("round", "round-completed.json"), ("outcome", "outcome-confirmed.json"),
            ("prediction", "prediction-estimate.json"),
        ):
            with self.subTest(name=name), patch("socket.socket", side_effect=AssertionError("Network forbidden")):
                record = fixture(name)
                before = deepcopy(record)
                validate_record(kind, record)
                self.assertEqual(before, record)

    def test_shared_invalid_cases(self):
        for case in fixture("invalid-cases.json"):
            with self.subTest(name=case["name"]):
                record = amend(fixture(case["base"]), case.get("set"), case.get("remove", []))
                self.assert_rejected(case["code"], validate_record, case["kind"], record)

    def test_abstention_without_invented_probabilities(self):
        record = amend(fixture("prediction-estimate.json"), {
            "/status": "ABSTAINED", "/probabilities": None,
            "/abstention_reason": "No verified history", "/training_cutoff": None,
            "/data_quality": "UNKNOWN",
        })
        validate_record("prediction", record)
        for missing in (None, "", "   "):
            with self.subTest(reason=missing):
                record["abstention_reason"] = missing
                self.assert_rejected("SCHEMA_INVALID", validate_record, "prediction", record)

    def test_all_ten_outcomes_including_specials(self):
        outcome = fixture("outcome-confirmed.json")
        for food in fixture("prediction-estimate.json")["probabilities"]:
            with self.subTest(food=food):
                outcome["value"] = food
                validate_record("outcome", outcome)

    def test_unreviewed_detection_can_be_retained_without_becoming_final(self):
        outcome = amend(fixture("outcome-confirmed.json"), {
            "/review_status": "UNREVIEWED", "/confirmed_at": None, "/reviewer_id": None,
        })
        record = amend(fixture("round-open.json"), {"/phase": "RESULT_PENDING", "/outcome": outcome})
        validate_record("round", record)
        record["phase"] = "RESULT_CONFIRMED"
        self.assert_rejected("UNCONFIRMED_OUTCOME", validate_record, "round", record)

    def test_round_identity_mode_and_provenance(self):
        for key, value, code in (
            ("round_id", "00000000-0000-4000-8000-000000000099", "IDENTITY_MISMATCH"),
            ("context_id", "00000000-0000-4000-8000-000000000099", "IDENTITY_MISMATCH"),
            ("mode", "PROFESSIONAL", "IDENTITY_MISMATCH"),
            ("provenance", "REAL", "PROVENANCE_MISMATCH"),
        ):
            with self.subTest(key=key):
                record = amend(fixture("round-completed.json"), {f"/outcome/{key}": value})
                self.assert_rejected(code, validate_record, "round", record)

    def test_round_timing_and_completion(self):
        for changes, code in (
            ({"/betting_closed_at": "2026-01-01T11:59:59.000Z"}, "TIME_ORDER"),
            ({"/completed_at": "2026-01-01T12:00:24.000Z"}, "TIME_ORDER"),
            ({"/completed_at": None}, "MISSING_DATA"),
            ({"/phase": "RESULT_CONFIRMED"}, "TIME_ORDER"),
            ({"/missing_data": ["OUTCOME"]}, "MISSING_DATA"),
        ):
            with self.subTest(changes=changes):
                self.assert_rejected(code, validate_record, "round", amend(fixture("round-completed.json"), changes))

    def test_result_frame_cannot_belong_to_an_earlier_round_or_open_betting(self):
        for captured_at in ("2025-12-31T12:00:00.000Z", "2026-01-01T12:00:19.999Z"):
            with self.subTest(captured_at=captured_at):
                record = fixture("round-completed.json")
                record["outcome"]["evidence"][0]["captured_at"] = captured_at
                self.assert_rejected("TIME_ORDER", validate_record, "round", record)

    def test_evidence_cannot_come_from_future_or_reuse_identifier(self):
        outcome = fixture("outcome-confirmed.json")
        outcome["evidence"][0]["captured_at"] = "2026-01-01T12:00:26.000Z"
        self.assert_rejected("TIME_ORDER", validate_record, "outcome", outcome)
        outcome = fixture("outcome-confirmed.json")
        duplicate = deepcopy(outcome["evidence"][0])
        duplicate["sha256"] = "b" * 64
        outcome["evidence"].append(duplicate)
        self.assert_rejected("IDENTITY_MISMATCH", validate_record, "outcome", outcome)

    def test_probability_precision_and_sum_boundary(self):
        record = fixture("prediction-estimate.json")
        record["probabilities"]["CORN"] += 1e-10
        validate_record("prediction", record)
        record["probabilities"]["CORN"] += 1e-7
        self.assert_rejected("PROBABILITY_SUM", validate_record, "prediction", record)

    def test_nonfinite_and_nonjson_python_values(self):
        for value in (float("nan"), float("inf"), float("-inf"), (1, 2), {1: "value"}):
            with self.subTest(value=repr(value)):
                record = fixture("prediction-estimate.json")
                record["probabilities"]["CORN"] = value
                self.assert_rejected("INVALID_JSON", validate_record, "prediction", record)

    def test_wire_parser_rejects_ambiguous_or_nonfinite_json(self):
        for text in ('{"schema_version":"1.0","schema_version":"2.0"}',
                     '{"nested":{"x":1,"x":2}}', '{"x":NaN}', '{"x":Infinity}',
                     '{"x":-Infinity}', '{"x":1e999}', '{', '\ufeff{}'):
            with self.subTest(text=text):
                self.assert_rejected("INVALID_JSON", load_record, text)

    def test_missing_and_wrong_root_types_are_rejected(self):
        for record in (None, [], True, 1, "value", {}):
            with self.subTest(record=record):
                error = self.assert_rejected("SCHEMA_INVALID", validate_record, "event", record)
                self.assertEqual("", error.path)  # JSON Pointer for the document root.

    def test_event_payload_variants_and_phase(self):
        event = fixture("event-snapshot.json")
        event.update(event_type="OUTCOME_REVIEW", payload=fixture("outcome-confirmed.json"),
                     phase="RESULT_CONFIRMED", device_timestamp="2026-01-01T12:00:25.000Z")
        validate_record("event", event)
        event["phase"] = "RESULT_PENDING"
        self.assert_rejected("UNCONFIRMED_OUTCOME", validate_record, "event", event)
        event.update(event_type="PREDICTION_LOCKED", payload=fixture("prediction-estimate.json"),
                     phase="BETTING_OPEN", device_timestamp="2026-01-01T12:00:04.000Z")
        validate_record("event", event)
        event["phase"] = "RESULT_PENDING"
        self.assert_rejected("TEMPORAL_LEAKAGE", validate_record, "event", event)

    def test_event_snapshot_session_and_phase_match(self):
        for changes in ({"/phase": "SPINNING"},
                        {"/capture_session_id": "00000000-0000-4000-8000-000000000099"}):
            with self.subTest(changes=changes):
                event = amend(fixture("event-snapshot.json"), changes)
                self.assert_rejected("IDENTITY_MISMATCH", validate_record, "event", event)

    def test_replay_ignores_property_order_but_rejects_changed_content(self):
        original = fixture("event-snapshot.json")
        reordered = dict(reversed(list(original.items())))
        self.assertEqual("REPLAY", compare_replay("event", original, reordered))
        changed = amend(original, {"/sequence": 1})
        self.assert_rejected("EVENT_CONFLICT", compare_replay, "event", original, changed)
        changed["event_id"] = "00000000-0000-4000-8000-000000000099"
        self.assertEqual("DISTINCT", compare_replay("event", original, changed))

    def test_locked_prediction_cannot_be_rewritten(self):
        original = fixture("prediction-estimate.json")
        self.assertEqual("REPLAY", compare_replay("prediction", original, deepcopy(original)))
        for changes in ({"/source": "PC"}, {"/model_version": "retrained"},
                        {"/issued_at": "2026-01-01T12:00:05.000Z"},
                        {"/probabilities/CORN": 0.2, "/probabilities/PIZZA": 0.0}):
            with self.subTest(changes=changes):
                self.assert_rejected("PREDICTION_IMMUTABLE", compare_replay, "prediction",
                                     original, amend(original, changes))

    def test_android_and_pc_predictions_are_distinct_records(self):
        android = fixture("prediction-estimate.json")
        pc = amend(android, {"/source": "PC", "/prediction_id": "00000000-0000-4000-8000-000000000099"})
        self.assertEqual("DISTINCT", compare_replay("prediction", android, pc))

    def test_historical_prediction_must_precede_close(self):
        record = fixture("round-completed.json")
        prediction = fixture("prediction-estimate.json")
        validate_prediction_context(prediction, record)
        late = amend(prediction, {"/issued_at": "2026-01-01T12:00:20.000Z",
                                  "/expires_at": "2026-01-01T12:00:21.000Z"})
        self.assert_rejected("TEMPORAL_LEAKAGE", validate_prediction_context, late, record)
        prediction["expires_at"] = "2026-01-01T12:00:21.000Z"
        self.assert_rejected("TEMPORAL_LEAKAGE", validate_prediction_context, prediction, record)

    def test_result_evidence_blocks_retrospective_predictions_even_before_confirmation(self):
        record = fixture("round-completed.json")
        record["betting_closed_at"] = None
        record["missing_data"] = ["BETTING_CLOSE"]
        prediction = amend(fixture("prediction-estimate.json"), {
            "/issued_at": "2026-01-01T12:00:23.000Z", "/expires_at": "2026-01-01T12:00:24.000Z",
        })
        self.assert_rejected("TEMPORAL_LEAKAGE", validate_prediction_context, prediction, record)

    def test_prediction_context_rejects_other_round_and_synthetic_mix(self):
        for changes, code in (({"/round_id": "00000000-0000-4000-8000-000000000099"}, "IDENTITY_MISMATCH"),
                              ({"/mode": "PROFESSIONAL"}, "IDENTITY_MISMATCH"),
                              ({"/provenance": "REAL"}, "PROVENANCE_MISMATCH")):
            with self.subTest(changes=changes):
                self.assert_rejected(code, validate_prediction_context,
                                     amend(fixture("prediction-estimate.json"), changes), fixture("round-open.json"))

    def test_live_result_expiry_boundary_and_round_phase(self):
        prediction, record = fixture("prediction-estimate.json"), fixture("round-open.json")
        validate_live_result(prediction, record, "2026-01-01T12:00:19.999Z")
        self.assert_rejected("STALE_RESULT", validate_live_result, prediction, record, "2026-01-01T12:00:20.000Z")
        self.assert_rejected("TIME_ORDER", validate_live_result, prediction, record, "2026-01-01T12:00:03.999Z")
        record["phase"] = "SPINNING"
        self.assert_rejected("STALE_RESULT", validate_live_result, prediction, record, "2026-01-01T12:00:05.000Z")

    def test_live_clock_input_is_explicit_and_validated(self):
        for now in (None, 123, "2026-01-01", "2026-02-30T12:00:05.000Z", "2026-01-01T12:00:05.000+00:00"):
            with self.subTest(now=now):
                self.assert_rejected("SCHEMA_INVALID", validate_live_result,
                                     fixture("prediction-estimate.json"), fixture("round-open.json"), now)

    def test_error_messages_do_not_echo_rejected_values(self):
        record = fixture("event-snapshot.json")
        record["event_id"] = "private-identifier-do-not-log"
        try:
            validate_record("event", record)
        except ContractError as error:
            self.assertNotIn(record["event_id"], json.dumps(error.as_dict()))
        else:
            self.fail("Invalid UUID was accepted")

    def test_command_line_validation_exit_codes(self):
        cwd = Path(__file__).resolve().parents[1]
        valid = subprocess.run([sys.executable, "-m", "greedycat.contracts", "event",
                                str(FIXTURES / "event-snapshot.json")],
                               cwd=cwd, capture_output=True, text=True, timeout=15)
        self.assertEqual(0, valid.returncode, valid.stderr)
        self.assertIn("PASS", valid.stdout)
        with tempfile.TemporaryDirectory() as directory:
            invalid_path = Path(directory) / "invalid.json"
            invalid_path.write_text('{"x":NaN}', encoding="utf-8")
            invalid = subprocess.run([sys.executable, "-m", "greedycat.contracts", "event", str(invalid_path)],
                                     cwd=cwd, capture_output=True, text=True, timeout=15)
            self.assertEqual(1, invalid.returncode, invalid.stderr)
            self.assertEqual("INVALID_JSON", json.loads(invalid.stdout)["code"])
            missing = subprocess.run([sys.executable, "-m", "greedycat.contracts", "event",
                                      str(Path(directory) / "missing.json")],
                                    cwd=cwd, capture_output=True, text=True, timeout=15)
            self.assertEqual(2, missing.returncode, missing.stderr)


if __name__ == "__main__":
    unittest.main()
