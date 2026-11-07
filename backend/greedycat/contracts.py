"""Offline v1 contract validation and stateless cross-record integrity guards.

Schemas own structural rules. These guards own ordering, identity and sums.
Persistence, trusted clocks and transactional locking belong to later tasks.
"""

from datetime import datetime
from functools import lru_cache
import json
import math
from pathlib import Path
import re
from typing import Any, Literal

from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource

Kind = Literal["event", "round", "outcome", "prediction", "errors"]
SCHEMA_DIR = Path(__file__).resolve().parents[2] / "contracts" / "v1"
KINDS = {"event", "round", "outcome", "prediction", "errors"}
IDENTITY = ("round_id", "context_id", "mode", "provenance")
FINAL_PHASES = {"RESULT_CONFIRMED", "ROUND_COMPLETED"}
CHECKER = FormatChecker()


@CHECKER.checks("utc-millis", raises=ValueError)
def _utc_millis(value: Any) -> bool:
    if not isinstance(value, str):
        return True  # The schema owns type errors.
    if not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}Z", value):
        return False
    datetime.strptime(value, "%Y-%m-%dT%H:%M:%S.%fZ")
    return True


class ContractError(ValueError):
    """Stable, input-value-free error that can be serialized for API use later."""

    def __init__(self, code: str, path: str, message: str):
        self.code, self.path, self.message = code, path, message
        super().__init__(f"{code} at {path or '/'}: {message}")

    def as_dict(self) -> dict:
        return {"schema_version": "1.0", "code": self.code, "path": self.path,
                "message": self.message, "retryable": False}


def _fail(code: str, path: str, message: str) -> None:
    raise ContractError(code, path, message)


def _pairs(pairs: list) -> dict:
    result = {}
    for key, value in pairs:
        if key in result:
            _fail("INVALID_JSON", "", "Duplicate JSON property.")
        result[key] = value
    return result


def _bad_constant(_: str) -> None:
    _fail("INVALID_JSON", "", "Non-finite numbers are not JSON values.")


def load_record(text: str) -> Any:
    """Parse wire JSON without silently accepting duplicate keys or NaN."""
    try:
        record = json.loads(text, object_pairs_hook=_pairs, parse_constant=_bad_constant)
        _check_json(record)
        return record
    except (ValueError, TypeError, RecursionError) as error:
        if isinstance(error, ContractError):
            raise
        _fail("INVALID_JSON", "", "Malformed JSON document.")


def _check_json(value: Any) -> None:
    # Direct Python callers must follow the same value domain as wire clients.
    if value is None or type(value) in (str, bool, int):
        return
    if type(value) is float and math.isfinite(value):
        return
    if type(value) is list:
        for item in value:
            _check_json(item)
        return
    if type(value) is dict and all(type(key) is str for key in value):
        for item in value.values():
            _check_json(item)
        return
    _fail("INVALID_JSON", "", "Only finite JSON values are accepted.")


@lru_cache(maxsize=1)
def _validators() -> dict:
    schemas = [json.loads(path.read_text(encoding="utf-8"))
               for path in sorted(SCHEMA_DIR.glob("*.schema.json"))]
    for schema in schemas:
        Draft202012Validator.check_schema(schema)
    # No retrieval callback: references resolve only from the local registry.
    registry = Registry().with_resources(
        (schema["$id"], Resource.from_contents(schema)) for schema in schemas
    )
    return {kind: Draft202012Validator(
        next(schema for schema in schemas if schema["$id"].endswith(f"/{kind}.schema.json")),
        registry=registry, format_checker=CHECKER,
    ) for kind in KINDS}


def validate_record(kind: Kind, record: Any) -> None:
    """Reject invalid structure and semantics; never mutate the input."""
    if kind not in KINDS:
        raise ValueError("Unknown contract kind")
    try:
        _check_json(record)
    except RecursionError:
        _fail("INVALID_JSON", "", "JSON nesting exceeds validation capacity.")
    if isinstance(record, dict) and "schema_version" in record and record["schema_version"] != "1.0":
        _fail("UNSUPPORTED_VERSION", "/schema_version", "Unsupported contract version.")
    error = next(_validators()[kind].iter_errors(record), None)
    if error:
        path = "".join("/" + str(part).replace("~", "~0").replace("/", "~1")
                       for part in error.absolute_path)
        _fail("SCHEMA_INVALID", path, "Record does not satisfy the v1 schema.")
    if kind == "prediction":
        _prediction(record)
    elif kind == "outcome":
        _outcome(record)
    elif kind == "round":
        _round(record)
    elif kind == "event":
        _event(record)


def _ordered(earlier: str | None, later: str | None, path: str) -> None:
    # Canonical fixed-width UTC milliseconds are lexically chronological.
    if earlier is not None and later is not None and earlier > later:
        _fail("TIME_ORDER", path, "Timestamps are out of chronological order.")


def _identity(left: dict, right: dict) -> None:
    for key in IDENTITY:
        if left[key] != right[key]:
            code = "PROVENANCE_MISMATCH" if key == "provenance" else "IDENTITY_MISMATCH"
            _fail(code, f"/{key}", "Related records must have the same identity and provenance.")


def _prediction(record: dict) -> None:
    _ordered(record["training_cutoff"], record["features_cutoff"], "/training_cutoff")
    _ordered(record["features_cutoff"], record["issued_at"], "/features_cutoff")
    if record["issued_at"] >= record["expires_at"]:
        _fail("TIME_ORDER", "/expires_at", "Expiry must follow prediction creation.")
    if record["probabilities"] is not None:
        if not math.isclose(math.fsum(record["probabilities"].values()), 1.0,
                            rel_tol=0, abs_tol=1e-9):
            _fail("PROBABILITY_SUM", "/probabilities", "Outcome probabilities must sum to one.")


def _outcome(record: dict) -> None:
    _ordered(record["observed_at"], record["confirmed_at"], "/confirmed_at")
    ids = [item["evidence_id"] for item in record["evidence"]]
    if len(ids) != len(set(ids)):
        _fail("IDENTITY_MISMATCH", "/evidence", "Evidence identifiers must be unique.")
    for item in record["evidence"]:
        _ordered(item["captured_at"], record["observed_at"], "/evidence")


def _round(record: dict) -> None:
    _ordered(record["started_at"], record["betting_closed_at"], "/betting_closed_at")
    _ordered(record["started_at"], record["completed_at"], "/completed_at")
    _ordered(record["betting_closed_at"], record["completed_at"], "/completed_at")
    flags = {"round_number": "ROUND_NUMBER", "started_at": "START_TIME",
             "betting_closed_at": "BETTING_CLOSE", "outcome": "OUTCOME"}
    for field, flag in flags.items():
        if record[field] is None and flag not in record["missing_data"]:
            _fail("MISSING_DATA", "/missing_data", "Missing values require explicit flags.")
    if record["mode"] == "UNKNOWN" and "MODE" not in record["missing_data"]:
        _fail("MISSING_DATA", "/missing_data", "Unknown mode requires an explicit flag.")
    outcome = record["outcome"]
    confirmed = outcome is not None and outcome["review_status"] == "HUMAN_CONFIRMED"
    if outcome is not None:
        validate_record("outcome", outcome)
        _identity(record, outcome)
        _ordered(record["started_at"], outcome["observed_at"], "/outcome/observed_at")
        _ordered(record["betting_closed_at"], outcome["observed_at"], "/outcome/observed_at")
        for evidence in outcome["evidence"]:
            _ordered(record["started_at"], evidence["captured_at"], "/outcome/evidence")
            _ordered(record["betting_closed_at"], evidence["captured_at"], "/outcome/evidence")
    if (record["phase"] in FINAL_PHASES) != confirmed:
        _fail("UNCONFIRMED_OUTCOME", "/phase", "Final phases require a confirmed outcome and vice versa.")
    if confirmed and "OUTCOME" in record["missing_data"]:
        _fail("MISSING_DATA", "/missing_data", "Confirmed outcome cannot also be missing.")
    if record["phase"] == "ROUND_COMPLETED":
        if record["completed_at"] is None:
            _fail("MISSING_DATA", "/completed_at", "A completed round needs a completion timestamp.")
        _ordered(outcome["confirmed_at"], record["completed_at"], "/completed_at")
    elif record["completed_at"] is not None:
        _fail("TIME_ORDER", "/completed_at", "Completion timestamp requires a completed round.")
    if record["phase"] == "BETTING_OPEN" and (record["betting_closed_at"] or outcome):
        _fail("TIME_ORDER", "/phase", "An open round cannot already be closed or have a result.")
    if outcome is not None and record["phase"] not in FINAL_PHASES | {"RESULT_PENDING"}:
        _fail("TIME_ORDER", "/phase", "Result candidates require a result phase.")


def _event(record: dict) -> None:
    payload = record["payload"]
    kind = {"ROUND_SNAPSHOT": "round", "OUTCOME_REVIEW": "outcome",
            "PREDICTION_LOCKED": "prediction"}[record["event_type"]]
    validate_record(kind, payload)
    _identity(record, payload)
    if kind == "round":
        if record["capture_session_id"] != payload["capture_session_id"] or record["phase"] != payload["phase"]:
            _fail("IDENTITY_MISMATCH", "/payload", "Snapshot session and phase must match its envelope.")
        times = [payload[key] for key in ("started_at", "betting_closed_at", "completed_at")]
        if payload["outcome"]:
            times += [payload["outcome"][key] for key in ("observed_at", "confirmed_at")]
    elif kind == "outcome":
        expected = "RESULT_CONFIRMED" if payload["review_status"] == "HUMAN_CONFIRMED" else "RESULT_PENDING"
        if record["phase"] != expected:
            _fail("UNCONFIRMED_OUTCOME", "/phase", "Review event phase must match review status.")
        times = [payload["observed_at"], payload["confirmed_at"]]
    else:
        if record["phase"] != "BETTING_OPEN":
            _fail("TEMPORAL_LEAKAGE", "/phase", "Predictions must be locked while betting is open.")
        times = [payload["issued_at"]]
    for timestamp in times:
        _ordered(timestamp, record["device_timestamp"], "/device_timestamp")


def validate_prediction_context(prediction: dict, round_record: dict) -> None:
    """Check a historical lock against later round metadata, not arrival time."""
    validate_record("prediction", prediction)
    validate_record("round", round_record)
    _identity(prediction, round_record)
    closed = round_record["betting_closed_at"]
    if closed is not None and (prediction["issued_at"] >= closed or prediction["expires_at"] > closed):
        _fail("TEMPORAL_LEAKAGE", "/issued_at", "Prediction must precede betting close and expire by it.")
    outcome = round_record["outcome"]
    if outcome:
        earliest_evidence = min([outcome["observed_at"]] +
                                [item["captured_at"] for item in outcome["evidence"]])
        if prediction["issued_at"] >= earliest_evidence:
            _fail("TEMPORAL_LEAKAGE", "/issued_at", "Prediction must precede result evidence.")


def validate_live_result(prediction: dict, round_record: dict, now: str) -> None:
    """Require a caller-supplied clock already mapped to the round time basis."""
    validate_prediction_context(prediction, round_record)
    if not isinstance(now, str) or not CHECKER.conforms(now, "utc-millis"):
        _fail("SCHEMA_INVALID", "/now", "Current time must be canonical UTC milliseconds.")
    if now < prediction["issued_at"]:
        _fail("TIME_ORDER", "/now", "Result cannot arrive before it was issued.")
    if now >= prediction["expires_at"] or round_record["phase"] != "BETTING_OPEN":
        _fail("STALE_RESULT", "/expires_at", "Result is too late for this active round.")


def compare_replay(kind: Kind, existing: dict, incoming: dict) -> Literal["REPLAY", "DISTINCT"]:
    """Stateless pre-write guard; callers must use it inside a transaction later."""
    if kind not in {"event", "prediction"}:
        raise ValueError("Replay guard supports events and locked predictions")
    validate_record(kind, existing)
    validate_record(kind, incoming)
    key = "event_id" if kind == "event" else "prediction_id"
    if existing[key] != incoming[key]:
        return "DISTINCT"
    if existing != incoming:
        _fail("EVENT_CONFLICT" if kind == "event" else "PREDICTION_IMMUTABLE",
              f"/{key}", "An existing identifier cannot be reused with changed content.")
    return "REPLAY"


def main() -> int:
    """Validate one local file; no network, database or device access."""
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=sorted(KINDS))
    parser.add_argument("file", type=Path)
    args = parser.parse_args()
    try:
        record = load_record(args.file.read_text(encoding="utf-8"))
        validate_record(args.kind, record)
    except ContractError as error:
        print(json.dumps(error.as_dict()))
        return 1
    except (OSError, UnicodeError):
        print("Cannot read the specified UTF-8 JSON file.")
        return 2
    print(f"PASS: {args.kind} contract v1.0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
