# Greedy Cat Intelligence

An offline-first Android research companion with a local PC analysis server and browser dashboard. The system will collect authorized observations, estimate uncertain outcome probabilities, and evaluate predictions on later verified results. More computation does not guarantee a predictive advantage.

**Current state:** audit, task organization, and v1 shared data contracts with an executable Python validator and tests. No Android application, server, web dashboard, capture pipeline, or trained model exists yet. No real gameplay dataset was supplied.

- [Task board and acceptance criteria](docs/TASKS.md)
- [Current status and next task](docs/STATUS.md)
- [File-level implementation plan](docs/IMPLEMENTATION_PLAN.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Repository and machine audit](docs/AUDIT.md)
- [Game claims and uncertainty](docs/GAME_RULES.md)
- [Validation plan](docs/VALIDATION_PLAN.md)
- [Shared contracts, tests and validator commands](contracts/README.md)
- [T002 validation report](docs/reports/T002_CONTRACTS.md)
- [Original project directive](docs/directives/ANDROID_MASTER.md)
- [Original hybrid directive](docs/directives/HYBRID_MASTER.md)

The project is saved to [abdoelka752/GreetyCat](https://github.com/abdoelka752/GreetyCat). Each completed task gets a reviewed commit and push. Work still pending or blocked is tracked explicitly.

There are no application startup commands yet. They will be added with the corresponding working components. To verify the documentation structure on Windows:

```powershell
python tools/verify_project_docs.py
```
