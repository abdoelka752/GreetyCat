# T008b: Android pairing and authenticated connection

This checkpoint adds explicit Android-PC pairing, not gameplay synchronization. T008a was pushed as `c2f707d527e8c8df2b48ff836ce420fb70f3d0d6`. Capture, floating game UI, automatic collection and server-side gameplay ingestion remain separate tracked work.

## Acceptance scope

Settings -> Connect your PC must require an HTTPS address, an operator-confirmed full certificate SHA-256 and a one-use invitation. Credentials must not leave the phone before certificate/IP verification. Pair, saved-credential reopen, check, rejection of expired/revoked credentials and explicit revocation must work against the PC service on the physical phone. Offline research must remain usable. Synthetic fixture credentials and device identifiers must stay out of Git. Default desktop startup stays loopback-only.

## Implementation

- Android 0.8.0/code 4 adds only INTERNET permission; cleartext is explicitly disabled. No new dependencies, Room schema changes, capture, overlay, storage-sharing or background-service permissions.
- Address parser accepts only canonical numeric RFC1918/loopback IPv4 HTTPS endpoints, with no credentials/path/query/fragment or DNS. The client disables proxies, redirects and caching. Requests run on IO only after a user action; there is no background polling or automatic connection. Responses are limited to 4096 bytes, four-second connect/read timeouts and a checked 12-second response-reading deadline (a blocking read may finish up to one read timeout after that deadline).
- Per-connection TLS permits only 1.2/1.3. The operator-provided leaf-certificate SHA-256 is the explicit trust anchor. The trust manager checks the exact digest, validity dates, self-signature and server-auth EKU. Android's HTTPS IP/hostname verification remains enabled; no global trust manager/verifier is changed. This deliberately supports only the app-generated self-signed identity, not arbitrary PKI chains. The custom-trust lint advisory is retained for visibility.
- Credentials are AES-256-GCM encrypted with fresh provider-generated IVs, format-bound AAD and an AndroidKeyStore non-exportable key. AtomicFile persistence is in noBackupFilesDir; no plaintext fallback, saved-state token or exported key. Unreadable files block pairing until explicit local forgetting. Hardware-backed/StrongBox storage is not claimed. App-process compromise and root access are outside this protection boundary.
- The screen masks the invitation, clears it after an attempt, requires confirmation, resets confirmation on address/fingerprint changes and protects its window with FLAG_SECURE. It labels a probe as a one-time check, not continuous connectivity. Saved credentials load without network access. Revoke must reach the trusted PC (or receive its already-invalid response) before local removal; offline forgetting explicitly warns that the PC credential remains valid. Research databases are untouched.
- One activity-scoped view model serializes pairing actions across navigation. Newly returned tokens are saved in a bounded non-cancellable section to avoid ordinary navigation cancellation dropping them. A process death/lost exchange response can still leave a PC session whose token was not saved; use operator list/revoke and a new invitation. No implicit retry/re-enrollment or trust replacement.
- PC `init --address PRIVATE_IP` includes that IP plus loopback in the certificate. `serve` still defaults to loopback. LAN operation requires both `--bind PRIVATE_IP --allow-lan` and a valid certificate with matching IP. Wildcard/public/link-local/DNS binds are rejected. The isolated pairing API admits only private/loopback peers and the exact configured Host; native TLS plus invitation/bearer authorization is still required. Diagnostic HTTP/dashboard listeners remain loopback-only. No firewall/router changes, automatic discovery, reverse proxy or remote device-control endpoints.

## Verification

**PASS**: final `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --console=plain`, 4m22s; 24 JVM tests with zero failures. Lint: 0 errors/14 warnings (13 existing dependency/target upgrade advisories and the visible CustomX509TrustManager review advisory). The initial build failed ContextCastToActivity lint; changed to the pinned Activity Compose LocalActivity API. The later security review added explicit protocol filtering, a response deadline and blocked replacement after storage failure; final build covers those changes.

**PASS**: complete backend suite, 78 tests, 15.864s; original diagnostics/contracts/dashboard regressions plus private address/Host boundary, explicit LAN flag and certificate-bind checks. `pip check` has no broken requirements. No dependency upgrades were needed. Documentation/link/provenance and staged whitespace checks pass.

**PASS**: physical API 34 phone, separate actual USB and direct Wi-Fi pairing runs. Each runs the native client against temporary PC TLS listeners: wrong pin rejected before exchange, correct pin/wrong IP rejected, valid exchange and authenticated probe, encrypted vault reopen, consumed invitation rejection, expired credential rejection, self-revocation followed by credential rejection. JUnit `OK (1 test)` each (0.763s USB, 0.852s Wi-Fi; functional test durations, not transport latency benchmarks). Neither run skipped its fixture-dependent test. No firewall settings were changed. All test tokens revoked; listeners, temporary identities, app-private fixture and owned USB mappings removed. Synthetic pairing does not enroll a lasting user session.

**PASS**: physical phone non-UI regression suite, 31 tests, 10.577s, including Keystore non-exportability/reopen and authenticated-ciphertext tamper rejection. App install/update and cold launch report `Status: ok`; one observed cold launch 2208ms, not a benchmark. No research data clear, game/gallery navigation or capture.

**PASS with explicit skip**: owned API 36 emulator reports `OK (37 tests)`, 72.464s: 36 passed, one fixture-dependent cross-device test skipped with status -4 because the physical-phone helper fixture was absent. That same integration test passed on the physical phone twice as described above. The new Compose test verifies pairing stays disabled without confirmation and changing the address resets confirmation; the full prior UI/storage suite also passed. The emulator was stopped after verification.

APK: 12,225,205 bytes; SHA-256 `684002f936d4544cd7fcd27f1f23cceb7fa96b55ae7f69847e9d0359ea0516c7`. APK/test logs/private identities remain ignored. Room schemas 1-4 are unchanged.

**NOT TESTED**: full phone UI automation (prior T006 stall remains unresolved), manual fingerprint-comparison UX on the phone, screenshot/visual fidelity, other phone/API levels, hardware-backed Keystore assurance, key-loss/process-death/renewal recovery, long-term reconnect and external security audit. Client/Keystore tests and emulator UI checks do not imply those were validated. No capture, overlay, real gameplay synchronization or demonstrated prediction edge is delivered by this task.

## Operation

From repository root, prepare dependencies as described in [backend instructions](../../backend/README.md). Use the existing default directory for USB loopback development, or a **new private ignored directory** for a LAN identity; initialization refuses to overwrite an identity. Replace PRIVATE_IP with your own PC's current RFC1918 address:

```powershell
Push-Location backend
..\.venv\Scripts\python.exe -m greedycat.pairing_server --directory ../data/pairing-lan init --address PRIVATE_IP
..\.venv\Scripts\python.exe -m greedycat.pairing_server --directory ../data/pairing-lan serve --bind PRIVATE_IP --allow-lan
# In another terminal, from backend, create a fresh 120-second invitation:
..\.venv\Scripts\python.exe -m greedycat.pairing_server --directory ../data/pairing-lan invite
Pop-Location
```

On Android open Settings -> Connect your PC, enter `https://PRIVATE_IP:8767`, compare the full fingerprint directly against the PC output, paste the invitation, confirm and pair. Use Check connection or Revoke on PC and forget. The PC must be reachable on the same trusted network; this task does not change firewall policy. Stop the foreground service with Ctrl+C. Never send a token/invitation to a certificate you did not independently confirm. No trust-on-first-use or trust-all workaround is provided.

For USB development the operator can explicitly map a free device TCP port to the loopback pairing listener using ADB reverse and enter that loopback HTTPS address on the phone; remove only that owned mapping afterwards. The acceptance helper manages its own temporary mappings with --no-rebind and removes them. It uses private, temporary synthetic identities/credentials and never installs or controls a game:

```powershell
.\.venv\Scripts\python.exe tools/test_pairing_phone.py
.\.venv\Scripts\python.exe tools/test_pairing_phone.py --lan-address PRIVATE_IP
```

Run these only under the phone owner's app-testing authorization. They install/update only this app and its test APK, use a private fixture via run-as stdin, enforce a 90-second instrumentation limit, revoke the test credential, remove fixtures/mappings, stop temporary listeners and reopen this app. No real pairing is established for the user by these synthetic acceptance tests. Secrets, addresses and serials are excluded from committed evidence. Raw JUnit evidence remains ignored under artifacts; temporary PC identity directories are removed after listeners stop.

Certificates expire after 365 days and sessions after 24 hours. A changed PC IP needs a matching identity and explicit re-pairing; certificate renewal/rotation is a manual new-identity workflow. Forgetting locally or uninstalling does not revoke server tokens. Automatic session renewal, mDNS, IPv6, remote browser access, production hardening/audit, API 26/other devices and long-term reconnect behavior remain unverified or future work. Pairing is not T009 synchronization and supplies no prediction/capture claims.

Official API references consulted: [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [AES/GCM guidance](https://developer.android.com/privacy-and-security/cryptography), [KeyGenParameterSpec](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec), [SSLSocket protocol selection](https://developer.android.com/reference/javax/net/ssl/SSLSocket), [cleartext policy](https://developer.android.com/privacy-and-security/security-config). Existing dependency versions remain pinned.
