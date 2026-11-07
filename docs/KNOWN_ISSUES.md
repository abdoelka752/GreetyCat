# Known issues

- No Android application, local server, dashboard or application build setup yet (T003 onward). T002 shared schemas and Python validation are implemented.
- No game screenshots, videos or verified gameplay dataset supplied; all game claims remain inferred or unknown.
- No verified full payout matrix; expected-return and wagering recommendations must abstain.
- Phone authorization, installed game version, capture support and device performance are NOT TESTED.
- Acceleration frameworks and available GPU memory are NOT TESTED; reported adapter memory is not an inference budget.
- Initial machine snapshot showed limited free RAM; worker/resource defaults require measurements.
- Contract validation dependencies are pinned and tested on Windows/Python 3.13.4. Server/Android dependencies, Android application ID and minimum supported Android version remain pending compatibility review.
- T002 integrity guards are stateless; transactional lock persistence, authenticated clocks and actual dataset/evidence verification are not implemented. Android conformance is NOT TESTED.
- V1.0 outcomes require human confirmation; uncertainty is explicitly NOT_ESTIMATED and skill is NO_PROVEN_EDGE. Automatic confirmation and measured model uncertainty require later validated contract revisions.
