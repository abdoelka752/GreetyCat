# Greedy Cat Intelligence

An offline-first Android research companion with a local PC analysis server and browser dashboard. The system will collect authorized observations, estimate uncertain outcome probabilities, and evaluate predictions on later verified results. More computation does not guarantee a predictive advantage.

**Current state:** shared data contracts, a local FastAPI service, a React diagnostic dashboard and a Kotlin/Compose Android companion. Android provides offline reference import, audited human outcome review, statistical research summaries and immutable prospective abstention checkpoints, with separate synthetic demo data. Verified live probability forecasts, capture, automatic recognition and PC synchronization remain planned. No real gameplay dataset was supplied. See the task reports for verified checks and limits.

- [Task board and acceptance criteria](docs/TASKS.md)
- [Current status and next task](docs/STATUS.md)
- [File-level implementation plan](docs/IMPLEMENTATION_PLAN.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Repository and machine audit](docs/AUDIT.md)
- [Game claims and uncertainty](docs/GAME_RULES.md)
- [Validation plan](docs/VALIDATION_PLAN.md)
- [Shared contracts, tests and validator commands](contracts/README.md)
- [T002 validation report](docs/reports/T002_CONTRACTS.md)
- [Local server setup and API](backend/README.md)
- [T003 validation report](docs/reports/T003_LOCAL_SERVER.md)
- [Dashboard setup and commands](web/README.md)
- [T004 validation report](docs/reports/T004_DASHBOARD.md)
- [Android build, emulator and storage instructions](android/README.md)
- [T005 validation report](docs/reports/T005_ANDROID_FOUNDATION.md)
- [T006 reference import and review report](docs/reports/T006_REFERENCE_REVIEW.md)
- [T007 statistical research and checkpoint report](docs/reports/T007_STATISTICS_PREDICTIONS.md)
- [Original project directive](docs/directives/ANDROID_MASTER.md)
- [Original hybrid directive](docs/directives/HYBRID_MASTER.md)

The project is saved to [abdoelka752/GreetyCat](https://github.com/abdoelka752/GreetyCat). Each completed task gets a reviewed commit and push. Work still pending or blocked is tracked explicitly.

To run the dashboard on Windows (Python 3.13 and Node 22.12+), from repository root:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r backend/requirements.txt
Push-Location web
npm.cmd ci
Pop-Location
.\tools\start-dev.ps1
```

Open `http://127.0.0.1:8765`; stop with Ctrl+C. The server and dashboard share one loopback origin. See the setup links for tests and limitations. To verify the documentation structure:

```powershell
python tools/verify_project_docs.py
```
