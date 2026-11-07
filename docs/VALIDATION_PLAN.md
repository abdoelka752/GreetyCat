# Validation and evidence plan

Use **PASS**, **FAIL**, **BLOCKED**, or **NOT TESTED** for each applicable check. Record commands, environment, fixture provenance, exit status and material limitations in task reports. A failed environment prerequisite is not a successful product test. No physical-device or browser visual claim without actual execution.

| Area | Required meaningful checks |
|---|---|
| Contracts | Missing/unknown versions, invalid values, timestamps, mode isolation, full ten-outcome vectors, finite nonnegative probabilities summing to one |
| Storage | Mode/context uniqueness, duplicate replay, transactional rollback, migration and backup restore, explicit missing values, correction history |
| Ingestion | Readable/unreadable image/video, hash provenance, manual confirmation, no guessed missing rounds, synthetic/real isolation |
| State and vision | Valid/invalid transitions, animation ambiguity, OCR noise, repeated/missing frames, notifications, layout shifts and invalid calibration |
| Probability | Exact posterior fixtures, normalization, empty-history abstention, credible interval behavior, separation of uncertainty and model skill |
| Prediction integrity | Locks precede result knowledge, immutable history, explicit cutoff, wrong-round/expired responses, no future-feature access |
| Evaluation | Log loss/Brier known values, walk-forward splits, calibration, future holdout, independent random-game negative control, multiple-testing safeguards |
| Decision engine | Verified regular/special settlement examples, correlated multi-selection returns, up-to-six limits, unknown-matrix refusal and unchanged risk limits after losses |
| Devices | Missing ADB, timeout, no phone, unauthorized/offline/available states; game visible and capture active must not be inferred from mere connection |
| Transport | Authentication failures, revocation, pairing expiry, encrypted trust, origin/request protection, spoofed device and local-only default |
| Synchronization | Duplicate/out-of-order/conflicting events, durable acknowledgments, crash mid-transfer, delayed responses, offline backlog and human-correction conflicts |
| Capture | Consent denial, visible notification/stop, revocation, foreground lifecycle, rotation, app switching, bounded memory and no secure-window bypass |
| Models/resources | Checksums, incompatible artifact, atomic activation, rollback, cancellation, memory limits, queue bounds and idle throttling |
| UI | Actual API data flow, honest empty/error states, correction workflow, accessibility, responsive layouts and working controls |
| End-to-end | Phone-only, phone+PC, PC-only; shutdown/reconnect/recovery, real-device capture, roundtrip data integrity and measured latency |

For software contracts use deterministic synthetic fixtures with clear provenance. For recognition evaluate separate annotated reference sets with error counts/coverage; do not tune on the evaluation set. For model comparisons use predeclared chronological cutoffs, effect/uncertainty criteria and reproducible seeds. Keep production, simulated and evaluation data distinguishable.

Run targeted checks for each task, then applicable builds/integration/device checks. Capture actual frame-to-analysis, inference, network, synchronization, memory, CPU and battery measurements only once those paths exist. Sub-second updates are a target, not an achieved property.

T001 verification is limited to document existence, local Markdown links, directive provenance/hashes, task coverage and diff review. There is no app build or model test to run yet.
