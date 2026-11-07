# Greedy Cat project workflow

Read `docs/TASKS.md`, `docs/STATUS.md`, and the two directives in `docs/directives/` before substantive work. The directives describe the target system; they are not evidence of implemented features or verified game rules.

Work one tracked task at a time: inspect, define acceptance, implement, verify, review the diff, update status and evidence, commit, push. The user has authorized saving each completed task to https://github.com/abdoelka752/GreetyCat.git. Do not force-push or rewrite published history. Stage only reviewed project files. If publication fails, preserve the commit and record the error; never claim it was saved remotely. Record the completed checkpoint in the next status update when its hash is available.

Keep each task independently reviewable. Do not mark a task complete because scaffolding or a button exists. Use PASS, FAIL, BLOCKED, and NOT TESTED with commands and evidence. Pending work must stay visible. Keep dependency versions pinned when selected; consult current official documentation before Android API/dependency choices.

No fabricated game observations, historical predictions, demonstrated edge, or device validation. Synthetic fixtures must be explicit and excluded from real datasets. Prediction records are immutable and must precede their outcome knowledge. Human corrections remain auditable. Unknown payout rules block return calculations.

Capture requires user consent, visible status, and an immediate stop. No automatic wagering, hidden navigation, private-server access, protected-data extraction, or security bypass. No device control merely because ADB detects a phone. Keep captures, local databases, credentials, device identifiers, and personal data out of Git.

Keep essential Android functionality offline. Version shared contracts and enforce idempotent synchronization. Desktop services start on loopback; authenticated encrypted pairing is required before LAN operation. Separate Android and PC predictions; reject stale results without rewriting locked records.

Prefer simple statistical baselines, bounded queues, and chronological evaluation. Do not add heavy AI dependencies without measured justification. Do not publish performance or prediction claims without reproducible evidence.
