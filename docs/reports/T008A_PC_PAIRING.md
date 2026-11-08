# T008a: PC pairing and TLS boundary

Scope: first independently reviewable part of T008. The PC service can issue and consume explicit invitations, authenticate a session and revoke it over real HTTPS. **T008 remains incomplete** until Android trust/storage/UI and cross-device acceptance (T008b) pass. No LAN listener is enabled.

## Acceptance and implementation

- Separate `greedycat.pairing_server` operator CLI; the existing HTTP dashboard/diagnostic service is unchanged and cannot exchange credentials. Both launchers bind only `127.0.0.1` and reject host overrides. No reverse proxy support.
- `init` explicitly creates a P-256 self-signed TLS identity with a loopback IP subject alternative name and 365-day validity. It refuses nonempty directories and never silently replaces trust. Minimum TLS 1.2. Uvicorn uses a single worker, no proxy trust/access logs, 16 concurrent connections and five-second keepalive.
- `invite` replaces one outstanding invitation: 256 random bits, valid 120 seconds and consumed transactionally once. `cancel` invalidates it. The operator must deliberately share the invitation and independently verified certificate fingerprint with the intended client. No automatic invitation on server startup.
- A successful exchange returns a random bearer token once, a server-generated pseudonymous pairing ID, and issue/expiry timestamps (Unix UTC seconds). Sessions last 24 hours; at most 32 unexpired sessions. Replacement invitations do not revoke existing sessions. No raw device ID is collected.
- SQLite schema version 1 stores only domain-separated SHA-256 secret digests; no raw invitation/token, hardware identifier or gameplay data. Unknown future DB versions fail without rewriting. Sessions survive restart. Operator revocation and authenticated self-revocation remove credentials immediately for subsequent requests; they cannot undo a request already authorized.
- A persisted wall-clock watermark rejects backward time. Denied authentication still commits expiry cleanup/watermark, preventing rollback resurrection. Restore the PC clock if it moves backwards. This is a local clock guard, not authenticated time or protection against an attacker restoring a database backup; authenticated time/backup policy remain future work.
- API version `pairing-1`: `POST /api/pairing/v1/exchange` accepts only `invitation`; `GET` and `DELETE /api/pairing/v1/session` require exactly one `Authorization: Bearer ...`. Gameplay schemas remain unchanged. No GET/query credential acceptance, browser requests, CORS, diagnostic or device-control endpoints. Requests are bounded to 4096 bytes including streamed bodies. Validation and database errors do not echo secrets or paths; responses use no-store.

## Verification

**PASS**: `cd backend; ../.venv/Scripts/python.exe -m unittest discover -s tests -v`: 74 tests, including 18 new pairing tests. Real TCP/TLS launcher exercise: trusted certificate succeeds; default trust, a different certificate and wrong hostname fail; unauthorized probe fails; invitation exchange/probe succeeds; revocation denies the next request. Temporary listener and identity are removed after the test. No `verify=False`, LAN binding, physical device or capture is used.

**PASS**: invitation replacement/cancellation/expiry/replay, four concurrent exchange attempts with exactly one winner, persistence/hash-only storage, session cap/isolation, expiry/rollback, future-version rejection, self/operator revocation, duplicate/malformed/missing credentials, plaintext/remote/browser/Host/proxy rejection, ordinary and streamed body limits, secret-redacted failures.

**PASS**: `python -m pip check`: no broken requirements. `python tools/verify_project_docs.py` and `git diff --check`: documentation/provenance/links and whitespace passed. Source/test/doc diff reviewed before staging; private runtime files are excluded.

Initial verification found unclosed SQLite handles causing Windows temporary-directory cleanup failures; fixed using explicit connection closing. The first restricted-sandbox TCP attempt timed out; actual TLS tests then passed in the permitted execution environment. Neither initial failure is presented as a passing run.

**NOT TESTED / pending T008b**: Android client, Keystore-backed credential storage, manual address form, certificate pin comparison/trust UX, phone connection/revocation/restart, certificate renewal and LAN operation. No Android source or APK changed in T008a. No gameplay synchronization, capture, floating overlay or model improvement is delivered here.

## Operating boundary

Private identity/ledger live under ignored `data/pairing/` by default. The private key is unencrypted on disk under the operating-system account boundary; local users/processes able to read it can impersonate the service. No additional Windows ACL hardening has been verified. Never copy the key, ledger, invitation or token into Git/logs/reports. Avoid terminal recording while issuing invitations. Expired/revoked rows are removed logically; secure forensic erasure is not claimed.

Clients must trust the operator-confirmed certificate and verify its IP/hostname **before sending the invitation or token**. A displayed fingerprint alone does not implement client verification. The service currently accepts only loopback peers. Do not forward it, relax host/peer checks or add LAN binding before T008b passes. With no Android client yet, these commands are for local development:

```powershell
Push-Location backend
..\.venv\Scripts\python.exe -m greedycat.pairing_server init
..\.venv\Scripts\python.exe -m greedycat.pairing_server serve
# In another terminal, also from backend:
..\.venv\Scripts\python.exe -m greedycat.pairing_server invite
..\.venv\Scripts\python.exe -m greedycat.pairing_server list
..\.venv\Scripts\python.exe -m greedycat.pairing_server cancel
# Substitute an ID printed by list:
..\.venv\Scripts\python.exe -m greedycat.pairing_server revoke PAIRING_ID
Pop-Location
```

Stop the foreground listener with Ctrl+C. `serve --port 8768` changes the local port. `--directory PATH` precedes the subcommand; use only a private ignored directory. Losing a token requires a fresh invitation; existing tokens are never revealed by `list`. Losing the identity requires explicit trust replacement on clients, not silent regeneration. Automatic cleanup, clock recovery UI, renewal and retention are pending.

Dependencies pinned after installation: cryptography 50.0.2, cffi 2.1.1, pycparser 3.12. Official references consulted: [PyCA X.509 tutorial](https://cryptography.io/en/stable/x509/tutorial/), [Python TLS contexts and verification](https://docs.python.org/3/library/ssl.html), [Uvicorn settings](https://uvicorn.dev/settings/). The installed Uvicorn 0.54.0 Config/Server interfaces were also inspected and exercised by the real-listener test.
