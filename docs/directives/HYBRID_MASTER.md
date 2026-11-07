# ADDITIONAL MASTER DIRECTIVE
## GREEDY CAT — HYBRID ANDROID + PC + WEB INTELLIGENCE PLATFORM

### IMPORTANT CONTEXT

You are extending the existing Greedy Cat Android Intelligence project, not replacing it.

All requirements from the previous master implementation prompt remain in effect.

**NEW OBJECTIVE:** Build a companion web application and local desktop computation server capable of using the user's computer resources to improve the mobile application's analytical capabilities, data collection, training, research, and efficiency.

The Android app and desktop system must cooperate as a unified, distributed intelligence platform.

Use the computer's available CPU, GPU, RAM, and storage for computationally expensive work while keeping the Android application lightweight, responsive, and independently functional.

Do not create two disconnected applications.

Build one coherent system with shared game models, synchronized observations, clearly defined APIs, and coordinated learning.

---

# 1. INTELLIGENT HYBRID ARCHITECTURE

Create three cooperating components.

### Component A: Android Companion App

Responsible for:

- Observing Greedy Cat through authorized screen capture
- Detecting the current round and game phase
- Reading timers and visible game indicators
- Identifying winning outcomes
- Recording verified gameplay data
- Maintaining lightweight local probability estimates
- Displaying real-time analysis
- Operating independently when the computer is unavailable

### Component B: Local PC Intelligence Server

Responsible for:

- Heavy statistical computations
- Historical dataset analysis
- Computer vision assistance
- Model training and validation
- Advanced model comparisons
- Large simulations and backtesting
- Strategy research
- Model optimization
- Long-term dataset storage and backups
- Producing versioned model updates for the phone

### Component C: Web Intelligence Dashboard

Accessible through the user's computer browser.

Responsible for:

- Real-time game monitoring
- Device connection management
- Historical analytics
- AI training management
- Dataset inspection and corrections
- Model performance comparison
- Simulation management
- CPU/GPU/RAM monitoring
- Research reports
- System configuration

The web application must be a real functional application connected to the local computation server, not a static website.

---

# 2. USE THE USER'S COMPUTER RESOURCES INTELLIGENTLY

First inspect the development computer.

Identify:

- Operating system
- CPU and logical cores
- Installed RAM
- Available storage
- GPU manufacturer and model
- Available GPU memory
- Supported acceleration frameworks
- Python and Java installations
- Android SDK and ADB availability
- Existing project dependencies

Do not assume an NVIDIA GPU is present.

Select acceleration based on actual detected hardware.

Consider:

- CUDA where supported
- DirectML where appropriate
- ONNX Runtime execution providers
- CPU inference
- Hardware-appropriate native libraries
- GPU acceleration for sufficiently large workloads

Benchmark implementations before selecting them.

Use CPU execution when GPU setup overhead makes it faster or more efficient.

## Resource allocation

Build a Resource Manager capable of allocating workload according to available resources.

Support three operating modes:

**Eco Mode**
- Minimal CPU usage
- Limited parallelism
- Background processing
- Reduced power consumption

**Balanced Mode**
- Moderate parallel computation
- Efficient model updates
- Normal background simulations

**Performance Mode**
- Higher CPU/GPU utilization
- Parallel research experiments
- Large statistical simulations
- More intensive model training

Avoid unnecessary high resource utilization when the game is idle.

Allow users to configure limits on CPU threads, memory, GPU memory, concurrent jobs, and storage usage.

Never exhaust the computer's memory merely to increase training speed.

---

# 3. BUILD A LOCAL COMPUTE BACKEND

Suggested initial stack:

- Python for statistical research and machine-learning workloads
- FastAPI for the local backend
- WebSocket for streaming state and results
- NumPy and SciPy for numerical calculations
- scikit-learn for baseline models and evaluation
- ONNX Runtime for supported inference models
- PyTorch only when a research task genuinely benefits from it
- SQLite initially, with a future database upgrade only if necessary

Choose the final architecture based on actual project requirements.

The local server should provide services for:

1. Device management
2. Gameplay observation ingestion
3. Historical data queries
4. Probability estimation
5. Prediction generation
6. Model training
7. Model evaluation
8. Backtesting
9. Simulation
10. Model deployment
11. Resource monitoring
12. System health

Use typed request and response contracts.

Version every API.

Include automated contract tests between Android and the backend.

---

# 4. SECURE REAL-TIME ANDROID ↔ PC COMMUNICATION

The application must discover and connect to the user's computer on the same local network.

Implement:

- Local network discovery, with a manual IP fallback
- Secure device pairing
- Session authentication
- Device connection health monitoring
- Automatic reconnection
- Outbound data buffering
- Delivery acknowledgment
- Retry and synchronization logic
- Network latency monitoring
- Offline fallback

Evaluate mDNS for local discovery.

Use WebSocket for real-time messages where appropriate.

Use HTTP for bulk transfers and other operations that do not require streaming.

For development, optionally provide an authorized USB/ADB-assisted connection workflow.

Support secure local operation without requiring a paid cloud service.

Encrypt sensitive traffic and do not expose an unauthenticated server on the local network.

For remote access, prefer an authenticated VPN or equivalent secure private connection rather than opening public network ports.

## Latency awareness

A Greedy Cat betting round lasts approximately 20 seconds.

Create a latency-aware computation system.

When the phone sends observations, attach:

- Unique event ID
- Device timestamp
- Current round ID
- Game phase
- Data quality
- Observation version

Track clock differences between devices.

Reject analysis that arrives too late for its intended round.

Measure actual end-to-end response latency.

Target sub-second updates for lightweight operations when hardware and network conditions permit, but do not assume this is guaranteed.

The Android app must continue using local models if the PC disconnects.

---

# 5. INTELLIGENT WORKLOAD DISTRIBUTION

Implement a computation scheduler.

Not every task should run on the PC.

Use task characteristics, hardware availability, latency, energy use, and measured execution time.

| Workload | Preferred execution |
|---|---|
| Capture screen | Android |
| Detect current round | Android |
| Read visible countdown | Android |
| Basic OCR | Android |
| Complex OCR reprocessing | PC |
| Basic Bayesian inference | Android |
| Fast probability calculations | Android |
| Advanced feature engineering | PC |
| Large historical analysis | PC |
| Model training | PC |
| Large simulations | PC |
| Model calibration research | PC |
| Real-time interface | Android and Web |
| Long-term backup | PC |

Do not transmit full-resolution frames when compact extracted observations are sufficient.

Use event-driven communication.

Send relevant screenshots or regions only when needed and authorized.

Allow the PC to reprocess low-confidence observations without blocking essential phone functionality.

---

# 6. CREATE A SHARED INTELLIGENCE PIPELINE

The system should work approximately as follows:

1. Android observes the game.
2. It extracts available information.
3. It validates the observations.
4. It stores the local data.
5. It generates a time-locked prediction.
6. It sends appropriate observations to the PC.
7. The PC performs deeper analysis when time permits.
8. The PC returns versioned probability estimates and uncertainty.
9. Android displays the newest valid information.
10. The actual game result is observed and verified.
11. The actual outcome is synchronized.
12. Models are evaluated against their previously locked predictions.
13. PC training jobs analyze new verified evidence.
14. Only validated improvements are promoted.
15. Updated models are distributed to the Android app.

**IMPORTANT:** A PC result must never overwrite a historical prediction that was already locked before the outcome.

Maintain separate records for Android predictions, PC predictions, and final model evaluation.

---

# 7. BUILD THE ADVANCED PC LEARNING LABORATORY

The PC should support research beyond the limited resources of a phone.

Implement experiments for:

### A. Outcome distribution analysis

Study whether food-result frequencies differ from simple probability baselines.

### B. Temporal dependency analysis

Investigate whether previous rounds contain useful predictive information.

Use chronological evaluation and appropriate statistical significance controls.

### C. Special-result analysis

Study whether observable prize-pool proxies, betting activity, or other signals correlate with Salad and Pizza outcomes.

### D. Hot-marker research

Record Hot-marker changes and determine whether the marker has measurable predictive value.

### E. Drift detection

Test whether result distributions change between sessions, modes, or periods.

### F. Model selection

Compare:

- Regularized frequency estimates
- Bayesian categorical models
- Online regression models
- FTRL where applicable
- Calibrated classification models
- Temporal feature models, if justified
- Ensemble techniques

Do not select the most complicated model automatically.

Require reproducible improvements over a simple baseline on unseen future rounds.

### G. Model error research

Identify:

- Overconfident predictions
- Low-quality observations
- Calibration failures
- Data gaps
- Distribution changes
- False pattern discoveries

A model must not claim to have learned a pattern simply because it fit historical data.

---

# 8. PC-ASSISTED COMPUTER VISION

The PC can improve visual analysis through more expensive processing.

Use it for:

- Reprocessing recorded frames
- Improving OCR accuracy
- Detecting misread round IDs
- Identifying difficult result animations
- Validating UI regions
- Comparing Basic and Professional layouts
- Generating new recognition templates
- Analyzing recognition failures

Create a human-review queue for low-confidence detections.

Allow corrected annotations to improve future computer vision models.

Keep model training datasets separate from evaluation datasets.

Produce optimized lightweight vision models or templates for Android when supported.

---

# 9. MODEL TRAINING AND DEPLOYMENT TO ANDROID

Create a model registry.

Every model must contain:

- Model identifier
- Version
- Architecture
- Training dataset identifier
- Training period
- Validation period
- Evaluation metrics
- Calibration information
- Required input features
- Compatible game modes
- File checksum
- Deployment status

Support:

- Local model training
- Model evaluation
- Model version comparison
- Model export
- Secure model transfer to Android
- Compatibility validation
- Atomic model activation
- Rollback to the previous model

Prefer compact model artifacts suitable for Android.

Where appropriate, use ONNX or direct native statistical implementations.

Do not deploy a new model solely because its training accuracy increased.

---

# 10. BUILD THE WEB DASHBOARD

Use a modern frontend architecture.

Suggested:

- React
- TypeScript
- Vite
- Tailwind CSS or a similarly maintainable design system
- Responsive charting library
- WebSocket-powered live state updates

The interface should look like a professional quantitative research terminal.

Use a compact dark theme.

Avoid excessive decorative elements.

## Dashboard A: Live Command Center

Display:

- Connected Android device
- Current Greedy Cat round
- Game mode
- Countdown
- Connection latency
- Recent outcome
- Probability distribution
- Model uncertainty
- Prediction source: Android or PC
- Active model version
- Capture/data status

## Dashboard B: Historical Intelligence

Display:

- Complete verified round history
- Food frequency statistics
- Special results
- Rolling probabilities
- Time-series visualization
- Filters
- Dataset export

## Dashboard C: AI Research Lab

Display:

- Training jobs
- Experiment status
- Model comparisons
- Calibration charts
- Error metrics
- Walk-forward validation
- Model promotion and rollback

## Dashboard D: Strategy Simulator

Allow hypothetical testing of:

- Different food selections
- Different wager amounts
- Up to six food selections
- Special outcomes
- Expected returns
- Downside risk
- Strategy assumptions

Include a no-wager option whenever no defensible advantage is demonstrated.

## Dashboard E: Computer Resources

Show:

- CPU utilization
- RAM utilization
- GPU utilization where supported
- GPU memory utilization
- Training resource usage
- Queue length
- Analysis latency
- Background job status

## Dashboard F: Device & Data Management

Provide:

- Android pairing
- Connection status
- Capture diagnostics
- Screen calibration
- Data synchronization
- Backup/restore
- Model deployment
- Error logs
- Dataset corrections

All controls must have functioning backend integration.

---

# 11. DATA SYNCHRONIZATION AND OWNERSHIP

Design a robust data synchronization architecture.

The Android device should maintain a durable local gameplay history.

The PC should maintain a synchronized research copy and derived datasets.

Use:

- Stable round identifiers
- Unique event identifiers
- Idempotent ingestion
- Explicit schema versions
- Change tracking
- Conflict resolution
- Checksums for large transfers
- Retry-safe synchronization

Never overwrite a verified outcome with an unverified OCR result.

Never overwrite a human correction silently.

A PC outage must not cause the loss of completed Android observations.

Support reconnecting after hours or days and resuming synchronization safely.

---

# 12. LARGE-SCALE SIMULATION ENGINE

Use computer resources to perform extensive offline experiments when useful.

Support synthetic scenarios including:

- Independent outcome distributions
- Nonuniform probabilities
- Time-varying probabilities
- Special outcome correlations
- Inaccurate observations
- Missing rounds
- Different wager structures
- Different uncertainty assumptions

Provide reproducible random seeds and experiment configurations.

Avoid generating millions of simulations if an exact calculation or a smaller statistically adequate simulation provides the same answer.

Report uncertainty and confidence intervals.

Separate simulated returns from actual results.

The main purpose is to test model behavior and risk assumptions, not to generate the illusion of reliable future profits.

---

# 13. OPTIONAL MULTI-ENGINE INTELLIGENCE

Evaluate whether different specialized engines should collaborate.

Possible roles:

**Observer Engine:** Interprets live game information.

**Probability Engine:** Calculates outcome probability estimates.

**Pattern Research Engine:** Tests hypotheses about observable dependencies.

**Risk Engine:** Evaluates hypothetical decisions.

**Model Evaluation Engine:** Detects prediction errors and calibration problems.

**Research Coordinator:** Manages experiment scheduling and reports.

These are logical software modules.

Do not implement multiple large language models as autonomous agents unless there is a measured reason to do so.

Build an efficient coordinated system rather than an unnecessarily expensive collection of AI services.

---

# 14. FAILOVER AND RELIABILITY

The complete system must support three modes:

### Mode 1: Phone Only

All essential observation, storage, and lightweight analysis features remain available.

### Mode 2: Phone + PC

The system adds heavier analysis, deeper research, training, and synchronization.

### Mode 3: PC Research Only

Users can inspect historical datasets, import recordings, run simulations, train models, and view reports without a connected phone.

Handle:

- PC shutdown
- Wi-Fi disconnection
- Android background restrictions
- Unavailable GPU
- Model incompatibility
- Database failures
- Interrupted training
- Duplicate network messages
- Delayed analysis responses

Create visible error states and recovery mechanisms.

---

# 15. TESTING AND PERFORMANCE ACCEPTANCE

Build test suites for:

- API compatibility
- Android-to-PC message delivery
- Device discovery
- Secure pairing
- Reconnection
- Offline buffering
- Dataset synchronization
- Prediction locking
- Model deployment
- Model rollback
- Training interruption
- Resource limits
- UI responsiveness
- Computer vision accuracy
- Time-series evaluation
- Statistical baseline comparison

Benchmark:

- Frame processing latency
- Phone inference latency
- PC inference latency
- Network round-trip latency
- Data synchronization throughput
- Model loading time
- Memory consumption
- CPU/GPU efficiency

Report actual measured results.

Do not describe performance as optimized without comparative measurements.

---

# 16. IMPLEMENTATION ORDER

Extend the previous Android project in this order:

**Stage 1:** Audit the existing Android architecture and define cross-platform contracts.

**Stage 2:** Build the local PC server with a health endpoint and real device discovery.

**Stage 3:** Build the functional React web dashboard.

**Stage 4:** Connect Android to the server through authenticated local communication.

**Stage 5:** Implement real-time observation and history synchronization.

**Stage 6:** Move expensive historical statistics and simulations to PC worker jobs.

**Stage 7:** Add PC model training and reproducible evaluation.

**Stage 8:** Implement model registry, deployment, and rollback.

**Stage 9:** Add comprehensive resource management and latency-based scheduling.

**Stage 10:** Conduct real-device and PC end-to-end testing.

For each stage, implement working code and tests before considering it complete.

---

# 17. FINAL DELIVERABLES

The finished system must include:

1. Android companion application.
2. PC-local intelligence backend.
3. Responsive browser dashboard.
4. Secure Android-to-PC communication.
5. Real-time gameplay data synchronization.
6. Advanced historical analytics.
7. PC-based model training and evaluation.
8. GPU acceleration when supported and beneficial.
9. Strategy simulation engine.
10. Model registry and phone deployment.
11. Resource monitoring.
12. Offline and disconnected operation.
13. Data backup, import, and export.
14. Automated tests.
15. Windows-compatible development and startup instructions, plus other platforms where practical.
16. Clear architecture and deployment documentation.

Provide simple development commands and a convenient way to launch the local server and dashboard.

Do not require cloud hosting or subscriptions.

# FINAL EXECUTION DIRECTIVE

First analyze the existing Greedy Cat repository and identify which Android components can be reused.

Then create a file-by-file hybrid architecture plan and begin implementation.

Prioritize an end-to-end working vertical slice:

**Android Observation → Local Database → PC Synchronization → PC Calculation → Web Dashboard → Versioned Result Returned to Android.**

Verify this sequence with actual tests before expanding to advanced AI training.

Build for reliability, reproducibility, speed, and maintainability.

The goal is a unified intelligent research platform that uses both devices efficiently—not two separate interfaces displaying unrelated information.

The system must be honest about its predictive limitations. More processing power does not guarantee better predictions when outcomes are random or the necessary information is not observable.