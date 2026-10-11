#!/usr/bin/env python3
"""Reject absent/placeholder/platform-bound core before the real compiler runs."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
guide = (root / "docs/architecture/KOTLIN_CONTRACTS.md").read_text()
ports = set()
for row in guide.splitlines():
    if row.startswith("| ") and len(row.split("|")) >= 5:
        ports.update(re.findall(r"`([A-Za-z][A-Za-z0-9]*)(?:<[^`]+>)?`", row.split("|")[2]))
paths = list((root / "android-app/core").glob("*/src/main/**/*.kt"))
assert paths, "Real Kotlin core source is missing; an empty module is not compile evidence"
source = "\n".join(path.read_text() for path in paths)
for port in sorted(ports):
    assert re.search(r"\binterface\s+" + re.escape(port) + r"\b", source), f"Missing public port: {port}"
for path in paths:
    content = path.read_text()
    assert not re.search(r"^import\s+(?:android\.|androidx\.|io\.ktor\.|okhttp3\.)", content, re.M), f"Platform type in pure core: {path}"
    assert not re.search(r"\b(?:TODO|NotImplementedError)\b", content), f"Placeholder in core: {path}"
print(f"Core source present: {len(paths)} files, {len(ports)} declared public ports; real compile and tests follow")
