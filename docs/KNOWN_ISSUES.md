# Known issues

- T005 adds an offline Kotlin/Compose Android foundation with draft storage and separate synthetic demo records. Reference import, verified outcomes, capture, predictions and PC synchronization remain future work. Gameplay/history/model web dashboards remain future work.
- No game screenshots, videos or verified gameplay dataset supplied; all game claims remain inferred or unknown.
- No verified full payout matrix; expected-return and wagering recommendations must abstain.
- A real ADB list returned one AVAILABLE transport during T003. Game version/visibility, screen availability, capture support and device performance are NOT TESTED. No phone navigation or capture was performed.
- The latest T004 launcher/browser checks returned a valid empty device list. Device counts are changing snapshots, not a persistent connection claim.
- Acceleration frameworks and available GPU memory are NOT TESTED; reported adapter memory is not an inference budget.
- Initial machine snapshot showed limited free RAM; worker/resource defaults require measurements.
- Contract and server/test dependencies are pinned and tested on Windows/Python 3.13.4. Android uses JDK 17, Gradle 8.13/AGP 8.13.2, Kotlin 2.2.21, SDK 36 and Room 2.8.5; minimum SDK 26 and application ID org.greedycat.intelligence. Older/newer OS versions and physical devices are NOT TESTED. Dependency/target upgrades remain visible lint advisories.
- T002 integrity guards are stateless; authenticated clocks and actual dataset/evidence verification are not implemented. T005 adds transactional draft persistence, not outcome/prediction locks. Android checks one shared fixture and draft invariants; complete Android wire-contract conformance is NOT TESTED.
- V1.0 outcomes require human confirmation; uncertainty is explicitly NOT_ESTIMATED and skill is NO_PROVEN_EDGE. Automatic confirmation and measured model uncertainty require later validated contract revisions.
- Local diagnostics are unauthenticated to local processes, with peer/Host/browser-origin guards and no CORS. LAN pairing/encryption remain T008; do not expose through an external reverse proxy.
- Diagnostic device IDs are ephemeral per-server pseudonyms, not durable paired-device identities. Discovery retains at most 64 KiB output and polls temporary-file size every 20 ms; transient disk writes can exceed that threshold before termination.
- Source-checkout launch is supported; distributable packaging remains pending. T004 verified the actual Windows build/start script, HTTP service and browser UI. Interactive Ctrl+C was not separately exercised; smoke/test processes were stopped by their harnesses.
- Browser QA used isolated headless Chromium 158, with desktop 1440x1100 and phone 390x844 viewports. Other browsers, physical mobile devices and assistive technologies are NOT TESTED. Screenshots were inspected locally and kept out of Git.
- Dashboard refresh clears old snapshots; auto refresh is opt-in every 15 seconds while visible. It does not provide authenticated synchronization, screen observation, live game state or prediction results.
- Android history displays at most 100 recent drafts. Final outcomes, corrections, predictions, export/backup and outbox entities are deferred to their respective tasks. Compact spacing is the only persisted user setting.
- The owned API 36 emulator raises the requested 1 GiB RAM to 2.5 GiB. Build and emulator must run sequentially on this 8 GB machine. The headless emulator uses two guest cores and software graphics; this is not a phone-performance or battery benchmark.
- Android release signing, distribution, TalkBack, large-font/rotation coverage, minimum-SDK behavior and physical-device validation are NOT TESTED. Debug APKs, local SDK paths, databases and emulator artifacts are ignored.
