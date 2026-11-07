# PROJECT: GREEDY CAT INTELLIGENCE
## Senior Android Engineering, Game Research, Computer Vision, Statistical AI & Decision-Support System

### YOUR ROLE

Act as a coordinated team of exceptional engineers and researchers:

- Principal Android/Kotlin Engineer
- Senior Computer Vision Engineer
- Applied Machine Learning Researcher
- Probability and Bayesian Statistics Specialist
- Game Theory and Decision Science Researcher
- Data Engineer and Quantitative Analyst
- Senior Mobile UI/UX Designer
- Android Device Integration Specialist
- Software Testing and Quality Assurance Architect

You are not creating a simple betting calculator or a random prediction generator.

You are building a rigorous, evolving, offline-first research and decision-support system for an existing mobile game called **Greedy Cat**.

The system must be capable of observing the game with permission, collecting structured gameplay data, understanding game phases, analyzing historical results, estimating probabilities, testing hypotheses, recording its prediction errors, and assessing whether any strategy offers a real statistical advantage.

**CRITICAL PRINCIPLE:** A sophisticated model is not necessarily a predictive model. If results are independent and unpredictable, the application must discover and report that rather than inventing patterns or promising wins.

Build intelligence through verified data, measurable performance, and sound engineering—not through confident-looking guesses.

---

# 1. FIRST TASK: REPOSITORY AND REFERENCE AUDIT

Before modifying any source code:

1. Inspect the complete project structure.
2. Identify the current Android technology stack and build configuration.
3. Locate every screenshot, video, document, or other Greedy Cat reference supplied by the user.
4. Analyze available references and extract confirmed mechanics.
5. Inspect existing code for reusable components, models, and services.
6. Identify missing requirements and uncertain assumptions.
7. Determine which aspects can be implemented immediately and which require additional observations.
8. Produce a documented technical implementation plan.

If a repository is empty, create a clean Android project with a maintainable modular structure.

Do not invent unavailable reference files, recorded outcomes, or game behavior.

Use the actual files present in the working environment.

Keep this project independent of the previous roulette application. Reuse sound architectural ideas where useful, but do not automatically reuse its probability assumptions or game-specific logic.

Deliver initial documents:

- `docs/GAME_RULES.md`
- `docs/REFERENCE_ANALYSIS.md`
- `docs/UNKNOWN_MECHANICS.md`
- `docs/ARCHITECTURE.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/VALIDATION_PLAN.md`

Every identified mechanic must have a confidence classification: confirmed, observed, inferred, or unknown.

---

# 2. GAME KNOWLEDGE: CURRENT STARTING MODEL

The following information was extracted from user-provided Greedy Cat screenshots and the game's instructions.

## Main gameplay

- The game operates in numbered rounds.
- The normal betting period is 20 seconds.
- Players select wager denominations and food items.
- Up to six food items may be wagered on in one round.
- Regular results correspond to eight food items.
- Two special result types exist: Salad and Pizza.
- The game displays player balances, betting indicators, result animations, historical records, and changing UI markers.
- The game contains Basic and Professional tabs whose differences are not yet established.

## Known food items

| Food | Category | Displayed multiplier |
|---|---|---|
| Corn | Vegetable | ×5 |
| Tomato | Vegetable | ×5 |
| Carrot | Vegetable | ×5 |
| Red pepper | Vegetable | ×5 |
| Shrimp | Meat/seafood | ×10 |
| Cow | Meat | ×15 |
| Fish | Meat/seafood | ×25 |
| Chicken | Meat | ×45 |

The game's own classifications take precedence over external biological or culinary definitions.

## Special outcomes

**Salad:** According to the game guide, all eligible wagers on vegetable items win.

**Pizza:** According to the game guide, all eligible wagers on meat-category items win.

The instructions suggest that larger prize pools may increase the chance of these special results.

However, the exact prize-pool thresholds, probability functions, multiplier behavior, and special payouts are unverified.

Do not guess these parameters.

## Displayed wager denominations

4, 20, 100, 1,000, 5,000, 10,000, 50,000 Dobi Coins.

The instructions indicate 1 Dobi Coin = 1 Coin.

## Additional visible signals

- Current round number
- Countdown timer
- Changing Hot marker
- Coin or wager indicators on food circles
- Today's winnings
- My Record
- Dobi Coin balance
- Player-level display
- Prize and chest milestones

Some signals may have no predictive value.

Your responsibility is to test whether they carry useful information.

---

# 3. MISSION A: UNDERSTAND THE GAME THROUGH REAL OBSERVATION

Build a dedicated **Game Research Mode**.

It should learn the structure of the game's interface through recorded videos, screenshots, and user-authorized observation sessions.

## 3.1 Research and discovery pipeline

The app must identify:

- Opening screen
- Basic mode
- Professional mode
- Betting screen
- Active betting phase
- Countdown completion
- Locked betting phase
- Result animation
- Final winning result
- Result summary screen
- History screen
- Rules screen
- Balance and record displays
- Special Salad and Pizza outcomes

Define each as a separate identifiable game state.

Use an explicit finite-state machine rather than treating every screenshot as an isolated image.

Recommended states:

`UNKNOWN → BETTING_OPEN → BETTING_CLOSED → SPINNING → RESULT_PENDING → RESULT_CONFIRMED → ROUND_COMPLETED`

Support recovery from:

- Missing frames
- Slow animations
- Popups
- Notifications
- Network lag
- Repeated frames
- Game disconnections
- Screen rotation
- Incorrect OCR
- Partially hidden information

## 3.2 Visual interpretation

Build a computer vision pipeline able to locate game elements across multiple screen resolutions and aspect ratios.

Recommended techniques:

- Reference image matching
- Normalized screen coordinates
- Anchor-based region detection
- OCR
- Shape and color analysis
- Image classification where required
- Frame-to-frame comparison
- Temporal smoothing
- Confidence scoring

Each detector should return:

1. Detected value
2. Confidence
3. Source frame
4. Timestamp
5. Screen coordinates
6. Validation status

Never convert a low-confidence detection directly into an authoritative game result.

Provide a correction interface for the user.

## 3.3 Screen-layout calibration

Implement a calibration system that maps the roulette-style food wheel to fixed semantic identities.

Do not rely only on absolute screen coordinates.

Use stable anchors such as the wheel center, food icons, result labels, and other visible elements.

Calibration should survive small layout changes.

If the layout changes substantially, invalidate the previous calibration and request review.

---

# 4. MISSION B: CONNECT TO AND OBSERVE THE USER'S PHONE

Build two supported acquisition modes.

## MODE 1: Android phone, standalone

Use Android MediaProjection for user-authorized screen capture.

Requirements:

- Explicit screen-sharing permission
- Correct foreground service
- Clear capture status indicator
- User-accessible stop button
- App-specific sharing when supported
- Correct handling of capture-session termination
- Appropriate frame-rate and performance controls
- Battery and thermal monitoring
- No root requirement

The captured frames should enter the real-time analysis pipeline.

The application should work offline wherever feasible.

Do not attempt silent background capture or circumvent secure windows.

## MODE 2: Development computer + Android phone

Create a desktop-assisted research environment.

Supported technologies may include:

- Android Debug Bridge
- Android Studio tooling
- scrcpy for authorized screen mirroring
- UI Automator for testing and exploration
- Android device logs where legitimately available
- Screenshot and screen-recording tools

Create a device discovery mechanism.

Show whether the phone is:

- Connected
- Authorized
- Screen available
- Game visible
- Capture active
- Analyzer active

For a user's own device, support guided exploration of non-wagering interfaces: viewing rules, opening history, switching between Basic and Professional, examining settings, and collecting screenshots.

Any navigation must be explicitly authorized, observable, and interruptible.

Do not bypass app permissions, security restrictions, or game anti-cheat mechanisms. Do not access private game servers, protected application data, or reverse-engineer hidden network protocols.

Never automatically place wagers during research. Gameplay decisions should remain user-confirmed and must respect the game's terms.

## 4.1 Communication architecture

Evaluate the best architecture for communication between desktop research tools and the phone application.

Possible design:

Phone Capture Service → Local Analysis Engine → Data Repository → Prediction Engine → UI.

For optional desktop assistance:

Phone → Authenticated Local Connection → Desktop Research Tool → Structured Observations.

Consider local WebSocket communication only if it provides a genuine advantage.

Use connection authentication and explicit user pairing. Avoid exposing device-control endpoints publicly.

The standalone app must continue functioning if the computer disconnects.

---

# 5. MISSION C: AUTOMATIC GAME DATA COLLECTION

Build a persistent, structured collection engine.

This is one of the most important parts of the project.

## 5.1 Record information for every round

Capture at least:

- Round identifier
- Game mode
- Start timestamp
- Betting close timestamp
- Result timestamp
- Captured duration
- Winning result
- Outcome category
- Visual recognition confidence
- Current Hot marker observations
- Visible betting indicators
- Available prize-pool information
- Observed player balance where authorized
- User's wagers when supplied
- User's returns when observable
- Source screenshot/video identifiers
- Whether a human confirmed the outcome

Maintain separate entities for:

**Round:** Identity and lifecycle.

**RoundSnapshot:** Information visible at a particular moment.

**Outcome:** Verified final result.

**Observation:** Individual detected UI feature.

**Wager:** User-entered or confirmed stake.

**Prediction:** Probabilities generated before the result.

**ModelVersion:** Exact model and parameters.

**Evaluation:** Prediction accuracy and statistical metrics.

**CaptureSession:** Recording provenance and device information.

**Correction:** Original detection, corrected value, and reason.

Do not store every item as an unstructured text log.

Use a proper relational schema.

## 5.2 Data integrity

Enforce:

- Unique round identifiers within the correct game mode
- Deduplication
- Timestamp ordering
- State-transition validation
- Missing-data flags
- Confidence thresholds
- Human correction records
- Transactional storage
- Schema migrations
- Backup and restore

A round must not be treated as confirmed until the final outcome is observed reliably or verified by the user.

Do not fabricate missing rounds.

Preserve raw observations so extraction improvements can be applied retrospectively.

## 5.3 Storage strategy

Use Room/SQLite for structured data.

Use app-scoped storage for optional frames and short video segments.

Avoid storing unlimited video.

Provide configurable policies for:

- Recording frequency
- Event-triggered frame capture
- Image compression
- Data retention
- Storage limits
- Export
- Deletion

Support CSV and JSON export, including model-training datasets.

Keep personal information and unrelated screen content out of the research dataset wherever possible.

---

# 6. MISSION D: BUILD THE PROBABILITY INTELLIGENCE ENGINE

Do not begin with a large language model.

Begin with reliable statistical models whose results can be measured and understood.

Create a modular ensemble architecture.

## Engine A: Empirical Probability Estimator

Maintain frequencies for the eight regular food outcomes and both special outcomes.

Support:

- Complete history
- Recent observations
- Rolling windows
- Time-decayed frequencies
- Separate game modes
- Uncertainty estimates

Do not assume that historical frequency is the true underlying probability.

## Engine B: Bayesian Estimation

Implement a categorical Bayesian model using an appropriate Dirichlet prior.

Update posterior estimates after each verified result.

Provide posterior credible intervals.

Use hierarchical or mode-specific models only when supported by sufficient observations.

Avoid making probabilities appear more certain than the evidence permits.

## Engine C: Sequential Pattern Research

Evaluate hypotheses such as:

- Outcome frequency shifts
- Short-term dependencies
- Repeated-result tendencies
- Transition probabilities
- Time-of-day effects
- Round-number associations
- Hot-marker relationships
- Apparent periodic behavior
- Changes following special results

Do not assume any relationship is genuine.

Test against independent or suitably randomized baselines.

Use controls for multiple hypothesis testing and selection bias.

Do not build a system that merely discovers visually interesting but statistically meaningless patterns.

## Engine D: Prize-Pool Hypothesis Model

The game guide claims prize-pool size can influence special outcomes.

Investigate this specifically.

If a reliable prize-pool measurement is available, test whether the probabilities of Salad and Pizza vary with it.

If only indirect wager indicators are visible, clearly separate actual prize-pool values from unverified proxies.

Compare models with and without these features.

Do not infer hidden server logic from a few observations.

## Engine E: Change Detection

Test whether the observed result distribution changes over time.

Candidate techniques:

- Bayesian change-point analysis
- Page-Hinkley or CUSUM-style detection
- Sequential likelihood monitoring
- Distribution divergence metrics

Distinguish actual changes from ordinary random fluctuations.

Require adequate statistical evidence before switching model regimes.

---

# 7. MISSION E: PREDICT THE NEXT GAME OUTCOME

Create a prediction API that returns a probability distribution, not merely a guessed food.

For each upcoming round, estimate probabilities for:

- Corn
- Tomato
- Carrot
- Pepper
- Shrimp
- Cow
- Fish
- Chicken
- Salad
- Pizza

Ensure mutually exclusive outcome probabilities sum to one.

A useful prediction response should contain:

- Round ID
- Predicted outcome probabilities
- Top-ranked possible outcome
- Model confidence and uncertainty
- Data quality status
- Model version
- Prediction timestamp
- Reason for any model abstention
- Historical evaluation statistics

## Critical requirement: Prediction locking

Create the prediction before the actual outcome becomes known.

Store the prediction immutably.

Only afterward link it to the confirmed outcome.

Never recalculate old predictions with information from the future and present them as successful historical guesses.

This is essential to prevent data leakage.

## Confidence design

Do not equate the highest predicted outcome probability with the probability that the overall model is reliable.

Represent separately:

- Estimated event probability
- Statistical uncertainty
- Data quality
- Model calibration
- Whether the model has demonstrated predictive skill

If no model demonstrates reliable performance, display probability estimates and an explicit **No proven predictive edge** state.

---

# 8. MISSION F: MAKE THE SYSTEM LEARN FROM ITS ERRORS

Create a complete prediction feedback loop.

The learning sequence should be:

1. Observe available information.
2. Validate and store a round snapshot.
3. Generate a probability prediction.
4. Lock the prediction.
5. Observe and confirm the true outcome.
6. Score the prediction.
7. Store the error.
8. Update eligible online models.
9. Reevaluate on later unseen rounds.
10. Retain model changes only when their benefits are supported.

Track performance with:

- Multiclass log loss
- Brier score
- Top-1 hit rate
- Top-k accuracy where useful
- Calibration error
- Calibration plots
- Probability sharpness
- Per-outcome performance
- Results by game mode
- Performance across chronological periods

Compare against:

- Uniform baseline
- Historical-frequency baseline
- Rolling-frequency baseline
- Regularized Bayesian baseline

Use walk-forward validation and chronological holdouts.

Never use a shuffled split that leaks future data into training.

Implement champion/challenger evaluation: a new model must beat an established baseline on held-out future data before becoming the active model.

Log the exact reason for every model promotion or rejection.

---

# 9. MISSION G: STRATEGY & RISK RESEARCH

Build a separate Decision Science Engine.

The probability engine predicts outcome distributions.

The decision engine evaluates potential actions using those distributions.

Keep these responsibilities separate.

## 9.1 Expected return calculation

For each wager, calculate its potential payout under every possible outcome.

Use a verified payout matrix.

Include both regular and special outcomes.

Before payout behavior is confirmed, expose it as an unknown parameter. Do not substitute a convenient assumption.

Calculate:

- Expected gross return
- Expected net return
- Probability of positive return
- Maximum possible loss
- Downside risk
- Portfolio variance
- Sensitivity to probability error

## 9.2 Multiple selection analysis

Because players can select multiple foods, model outcomes jointly.

For example, Salad may make several vegetable selections win simultaneously.

This creates correlated returns.

Do not assume food wagers pay independently.

Evaluate hypothetical combinations across up to six food choices where rules allow.

Use exact scenario enumeration when feasible.

## 9.3 Risk controls

Support configurable:

- Session budget
- Maximum stake
- Maximum session loss
- Maximum daily loss
- Maximum exposure per round
- Automatic analysis pause when limits are reached

These controls must not be automatically relaxed after losing rounds.

Do not implement Martingale-style escalation as a default.

Do not imply that a losing streak makes a future win more likely.

## 9.4 Decision policy

The system should recommend abstention when:

- A prediction is unreliable
- A game outcome has not been modeled correctly
- Data quality is insufficient
- The estimated expected return is unfavorable
- Model uncertainty eliminates a demonstrated advantage
- The risk limit would be exceeded

Do not force the engine to recommend a food every round.

Historical simulation must report both outcomes and the assumptions used.

A simulated profit is not proof of a future advantage.

---

# 10. MISSION H: SIMULATION & RESEARCH LAB

Create a local experimental environment.

It must support:

- Synthetic games with known probabilities
- Independent random outcomes
- Biased probability distributions
- Changing probability distributions
- Correlated special outcomes
- Noisy OCR observations
- Missing rounds
- Misidentified outcomes
- Variable result delays
- Hypothetical betting strategies

The simulator must distinguish synthetic observations from real gameplay data.

Never train production probability estimates on simulated outcomes as if they came from the actual game.

Use simulation to validate software correctness, model robustness, and theoretical claims.

Specific required test:

When provided with independent random outcomes, the learning engine must not falsely report that recent streaks reliably predict future results.

---

# 11. MISSION I: MOBILE AI MODEL SELECTION

Prioritize efficient offline inference.

Suggested hierarchy:

**Layer 1:** Deterministic rules and game-state recognition.

**Layer 2:** Bayesian and statistical estimation.

**Layer 3:** Online learning and change detection.

**Layer 4:** Optional compact machine-learning models, but only when justified by real, validated improvements.

Evaluate whether FTRL, online logistic regression, gradient-boosted models, or ONNX Runtime actually provide benefits for the observed features.

Do not add models simply to make the application appear intelligent.

A small model that is well calibrated and empirically tested is preferable to a larger, poorly evaluated model.

No paid AI APIs should be required.

Prefer free and open-source components. Where a free but proprietary component such as an on-device OCR SDK is considered, document its licensing and compare alternatives.

An LLM is optional for explaining analysis reports, not for fabricating probabilities or replacing the statistical engine.

---

# 12. MISSION J: DESIGN A PROFESSIONAL ANDROID INTERFACE

Use Kotlin with Jetpack Compose unless the repository provides a compelling reason otherwise.

The visual direction must be:

- Compact
- Modern
- Professional
- Fast
- Information-dense but readable
- Dark-mode optimized
- Clear visual hierarchy
- Minimal unnecessary text
- High-quality icons and charts
- Responsive to phone dimensions

Do not duplicate the original game's visual design unnecessarily.

Build a distinctive analysis interface.

## Screen 1: Live Intelligence Dashboard

Top section:

- Connection status
- Current round
- Game mode
- Countdown
- Capture status
- Last confirmed result

Center:

- Visual food wheel or ranked outcome grid
- Estimated probabilities
- Data-quality indicators
- Leading hypothetical outcome
- Model uncertainty

Bottom:

- Risk status
- Decision summary
- Recent results
- Capture/start/stop controls

Do not display an unvalidated predicted result as a certain answer.

## Screen 2: Live Observer

Show:

- Captured game preview
- Detection boundaries
- Recognized food labels
- Round-number OCR
- Countdown OCR
- Current state
- Recognition confidence
- Correction controls

Allow the user to compare detections with the actual image.

## Screen 3: History

Show:

- Round timeline
- Outcome history
- Food distribution
- Special results
- Search by round
- Time filters
- Basic/Professional filters
- Export

## Screen 4: Learning Laboratory

Show:

- Number of verified rounds
- Model calibration
- Prediction accuracy
- Comparison against baselines
- Learning progress
- Drift indicators
- Data gaps
- Model version

Learning progress must reflect evaluated evidence, not a fictional AI intelligence percentage.

## Screen 5: Strategy Simulator

Show:

- Hypothetical wager combinations
- Expected returns
- Risk distribution
- Special outcome effects
- Historical simulation outcomes
- Assumptions and uncertainty

## Screen 6: Settings & Device Integration

Include:

- Permission management
- Capture frequency
- Calibration
- Screen regions
- Data retention
- Offline mode
- Local export/import
- Debug mode
- Model settings
- Risk limits

---

# 13. ANDROID SOFTWARE ARCHITECTURE

Propose a maintainable architecture resembling:

`app/` — Navigation, dependency injection, application lifecycle.

`core/common/` — Shared utilities, configuration, errors.

`core/database/` — Room entities, migrations, repositories.

`core/capture/` — MediaProjection and frame management.

`core/vision/` — OCR, screen calibration, image recognition.

`core/gamestate/` — Round state machine, event validation.

`core/domain/` — Game rules, payouts, outcome definitions.

`core/statistics/` — Frequency and Bayesian probability models.

`core/learning/` — Online updates, model evaluation, calibration.

`core/decision/` — Expected value, risk and abstention logic.

`core/simulation/` — Synthetic environments and backtesting.

`core/connectivity/` — Optional authenticated desktop-device link.

`feature/live/` — Dashboard.

`feature/observer/` — Live computer vision inspection.

`feature/history/` — Round history.

`feature/lab/` — Model research and evaluation.

`feature/strategy/` — Scenario calculator.

`feature/settings/` — User controls.

`tools/research/` — Desktop research utilities where appropriate.

`tests/` — Unit, instrumentation, integration, and statistical tests.

Adapt names and module boundaries to the repository.

Avoid needless modules if a simpler architecture provides equal maintainability.

Use clear interfaces, dependency injection, structured concurrency, deterministic testing, and explicit error handling.

All expensive frame analysis must run off the main UI thread.

Use bounded queues and backpressure to prevent runaway memory usage.

---

# 14. PERFORMANCE REQUIREMENTS

The game has a short betting window, so responsiveness matters.

Measure end-to-end latency from captured frame to displayed analysis.

Optimize:

- Capture frequency
- ROI processing
- OCR scheduling
- Image resizing
- Template matching
- Database writes
- Model inference
- UI recomposition
- Memory allocations
- Battery usage
- Device temperature

Use lightweight recognition for stable screens.

Increase processing only around relevant state transitions.

Avoid continuously running heavyweight neural networks if simpler vision is sufficient.

If latency or recognition uncertainty prevents useful live analysis, show the limitation clearly.

---

# 15. SECURITY, PRIVACY, AND USER CONTROL

The application must be transparent about its actions.

Requirements:

- No rooting
- No hidden screen recording
- No credential extraction
- No secret background navigation
- No anti-cheat or security bypass
- No unauthorized account access
- No hidden network interception
- No silent real-money or coin wagers
- No forced purchases
- No unrelated personal-data collection

Device exploration must use authorized development or testing interfaces.

Wager placement remains a separate, explicitly user-confirmed action, subject to the game's rules.

MediaProjection sessions require the appropriate Android consent and lifecycle handling.

Provide immediate stop controls for observation and research sessions.

Store captured data locally by default.

---

# 16. IMPLEMENTATION PHASES

Complete the project in incremental milestones.

### Phase 0 — Research and architecture

Audit repository, inspect references, establish game rules and unknown mechanics, produce architecture documents.

**Acceptance:** All assumptions are identified and all reference assets are indexed.

### Phase 1 — App foundation

Create the Android project, navigation, theming, Room schema, dependency injection, and test infrastructure.

**Acceptance:** Builds successfully, launches, persists sample data, and has functioning navigation.

### Phase 2 — Reference ingestion

Support importing screenshots and videos.

Implement initial screen detection and outcome annotation.

**Acceptance:** Supplied references can be examined and converted into structured observations with manual verification.

### Phase 3 — Phone observation

Implement permission-based MediaProjection capture and the optional ADB research workflow.

**Acceptance:** A user can start and stop capture, observe recognized game states, and retain a valid recording session.

### Phase 4 — Round collection

Implement round detection, timers, result recognition, validation, deduplication, and persistence.

**Acceptance:** A complete test round can be captured without duplicate records or guessed missing outcomes.

### Phase 5 — Probability baseline

Implement frequencies, Bayesian estimates, uncertainty, and prediction locking.

**Acceptance:** Probability vectors are valid and predictions cannot access future results.

### Phase 6 — Learning and model evaluation

Implement chronological evaluation, model comparison, calibration analysis, and online updates.

**Acceptance:** The app accurately reports whether a model beats its baselines on unseen results.

### Phase 7 — Decision simulation

Add the payout model, special outcomes, expected value, risk scenarios, and abstention logic.

**Acceptance:** Calculations pass deterministic tests with known payout examples.

### Phase 8 — Production UI

Refine the dashboards, visualizations, corrections, settings, and responsive layouts.

**Acceptance:** Core analysis is readable and usable on a real Android phone.

### Phase 9 — Device and reliability testing

Test real-device performance, background lifecycle behavior, capture interruption, database recovery, app switching, and battery usage.

**Acceptance:** No critical crash, data corruption, silent recording, or unbounded resource consumption during the defined test suite.

### Phase 10 — Experimental model improvements

Research advanced features and alternative models only after a reliable baseline and sufficient verified observations exist.

**Acceptance:** Every proposed improvement has documented assumptions and a reproducible performance comparison.

---

# 17. QUALITY REQUIREMENTS

Do not claim a feature is implemented merely because the interface contains a button.

Every feature should have an actual working data flow.

Do not use mock prediction values in production displays.

Use explicit demo mode when synthetic data is shown.

Required tests include:

- Round state transitions
- OCR error handling
- Duplicate round detection
- Special outcomes
- Probability normalization
- Posterior updates
- Prediction locking
- Time-series data leakage
- Payout calculations
- Database migration
- Device disconnection
- Capture cancellation
- Permission denial
- Empty history
- Model abstention
- Independent random-game baseline behavior

Maintain a documented known-issues list.

When Android APIs or dependencies may have changed, consult current official documentation before selecting versions.

---

# 18. REQUIRED OUTPUTS

Provide:

1. Complete Android project source code.
2. Functional UI screens.
3. Local persistent database.
4. Screenshot/video import.
5. Authorized phone capture workflow.
6. Real-time game-state detection.
7. Automatic round collection.
8. Probability estimation engine.
9. Online model evaluation.
10. Strategy simulation engine.
11. Data export/import.
12. Testing and debugging utilities.
13. Reproducible build instructions.
14. Device installation and permission instructions.
15. Technical architecture documentation.
16. Model evaluation report.
17. List of remaining unknown game mechanics.

Do not report successful real-world prediction performance unless you have actually tested it on verified unseen outcomes.

---

# 19. HOW YOU MUST THINK AND WORK

Use this engineering reasoning process for each significant task:

**Observe → Form hypothesis → Identify assumptions → Design minimal test → Implement → Verify → Measure → Document → Improve.**

Continuously ask:

- Is the information actually observable?
- Is this game mechanic confirmed or merely suspected?
- Is our data reliable?
- Are we accidentally using information from the future?
- Does this feature improve prediction on unseen rounds?
- Is the model calibrated?
- Could this apparent pattern occur by chance?
- Does a simpler solution work as well?
- Can the system recover from a wrong detection?
- Can the result be independently reproduced?
- Are the performance and battery costs justified?
- Is the user always in control of device capture and gameplay?

Do not confuse complexity with intelligence.

Do not fabricate game knowledge.

Do not promise profitable predictions.

Do not allow the learning system to overwrite or reinterpret previous prediction records.

Prefer experimentally validated improvements over speculative features.

Document failures and rejected hypotheses as carefully as successful improvements.

# 20. START EXECUTION

Begin by auditing the repository and available references.

Then:

1. Produce a detailed file-level implementation plan.
2. Rank dependencies and technical risks.
3. Identify which game mechanics are sufficiently understood.
4. Create the initial project architecture.
5. Implement the first working vertical slice: import reference → recognize result → validate round → store observation → display history.
6. Add automated tests for this slice.
7. Build and verify it.
8. Continue through the planned phases while recording completed work, test results, blockers, and remaining assumptions.

Do not halt on minor design choices that can be resolved using sound engineering defaults.

Do not silently skip difficult requirements.

If live device access is unavailable, implement and verify the offline ingestion path and provide the exact phone-connection procedure needed for later testing.

**FINAL OBJECTIVE**

Deliver a professional Android game-analysis companion that can observe Greedy Cat with permission, collect verified gameplay data, maintain calibrated probability estimates, learn through rigorous feedback, evaluate hypothetical strategies, and provide useful, honest real-time analysis.

The success criterion is not how often the app claims to know the next result.

The success criterion is how reliably it measures what is knowable, how accurately it predicts genuinely unseen outcomes, and how safely it handles situations where no predictive advantage exists.
