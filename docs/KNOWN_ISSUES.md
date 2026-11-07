# Known issues

- No Android application or web dashboard yet (T004 onward). T002 shared contracts and T003 local health/device service are implemented.
- No game screenshots, videos or verified gameplay dataset supplied; all game claims remain inferred or unknown.
- No verified full payout matrix; expected-return and wagering recommendations must abstain.
- A real ADB list returned one AVAILABLE transport during T003. Game version/visibility, screen availability, capture support and device performance are NOT TESTED. No phone navigation or capture was performed.
- Acceleration frameworks and available GPU memory are NOT TESTED; reported adapter memory is not an inference budget.
- Initial machine snapshot showed limited free RAM; worker/resource defaults require measurements.
- Contract and server/test dependencies are pinned and tested on Windows/Python 3.13.4. Android dependencies, application ID and minimum supported Android version remain pending compatibility review.
- T002 integrity guards are stateless; transactional lock persistence, authenticated clocks and actual dataset/evidence verification are not implemented. Android conformance is NOT TESTED.
- V1.0 outcomes require human confirmation; uncertainty is explicitly NOT_ESTIMATED and skill is NO_PROVEN_EDGE. Automatic confirmation and measured model uncertainty require later validated contract revisions.
- Local diagnostics are unauthenticated to local processes, with peer/Host/browser-origin guards and no CORS. LAN pairing/encryption remain T008; do not expose through an external reverse proxy.
- Diagnostic device IDs are ephemeral per-server pseudonyms, not durable paired-device identities. Discovery retains at most 64 KiB output and polls temporary-file size every 20 ms; transient disk writes can exceed that threshold before termination.
- Source-checkout launch is supported; distributable packaging remains pending. The PowerShell wrapper passed syntax validation; actual HTTP smoke used its underlying Python launcher.
