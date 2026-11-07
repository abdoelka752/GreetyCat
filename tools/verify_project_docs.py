"""Verify the initial documentation checkpoint without third-party dependencies."""

from pathlib import Path
import hashlib
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = (
    "README.md", "AGENTS.md", "docs/AUDIT.md", "docs/GAME_RULES.md",
    "docs/REFERENCE_ANALYSIS.md", "docs/UNKNOWN_MECHANICS.md",
    "docs/ARCHITECTURE.md", "docs/IMPLEMENTATION_PLAN.md",
    "docs/VALIDATION_PLAN.md", "docs/TASKS.md", "docs/STATUS.md",
    "docs/KNOWN_ISSUES.md", "docs/directives/ANDROID_MASTER.md",
    "docs/directives/HYBRID_MASTER.md",
)


def main() -> int:
    errors = []
    for name in REQUIRED:
        path = ROOT / name
        if not path.is_file() or not path.stat().st_size:
            errors.append(f"Missing or empty: {name}")
    if errors:
        print("\n".join(errors))
        return 1

    for path in ROOT.rglob("*.md"):
        if any(part in {".git", ".venv", "node_modules"} for part in path.parts):
            continue
        text = path.read_text(encoding="utf-8")
        for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", text):
            if "://" in target or target.startswith("#"):
                continue
            linked = path.parent / target.split("#", 1)[0]
            if not linked.exists():
                errors.append(f"Broken local link in {path.relative_to(ROOT)}: {target}")

    board = (ROOT / "docs/TASKS.md").read_text(encoding="utf-8")
    plan = (ROOT / "docs/IMPLEMENTATION_PLAN.md").read_text(encoding="utf-8")
    for index in range(1, 22):
        task = f"T{index:03d}"
        if not re.search(rf"^\| {task} \|", board, re.MULTILINE):
            errors.append(f"Task missing from board: {task}")
        if index > 1 and task not in plan:
            errors.append(f"Task missing from file plan: {task}")

    references = (ROOT / "docs/REFERENCE_ANALYSIS.md").read_text(encoding="utf-8")
    for name in ("ANDROID_MASTER.md", "HYBRID_MASTER.md"):
        digest = hashlib.sha256((ROOT / "docs/directives" / name).read_bytes()).hexdigest()
        if digest not in references:
            errors.append(f"Directive hash missing or incorrect: {name}")

    if errors:
        print("FAIL\n" + "\n".join(errors))
        return 1
    print("PASS: required documents, 21 tracked tasks, local links and directive SHA-256 provenance")
    return 0


if __name__ == "__main__":
    sys.exit(main())
