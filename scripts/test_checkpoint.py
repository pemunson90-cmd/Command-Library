"""A UTC day change must leave all resume pointers and append-only notes coherent."""
import datetime as dt
import json
from pathlib import Path
import tempfile
import unittest
from checkpoint import record


class CheckpointTests(unittest.TestCase):
    def test_day_rollover_preserves_notes_and_moves_all_pointers(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for folder in ("coordination/tasks", "coordination/handoffs", "docs/gauntlet"):
                (root / folder).mkdir(parents=True)
            (root / "coordination/tasks/next.md").write_text("Task card")
            (root / "coordination/state.json").write_text(json.dumps({"stage": "K1"}))
            (root / "coordination/ORCHESTRATOR.json").write_text("{}")
            (root / "coordination/QUEUE.md").write_text("Checkpoint-ID: old\n| next | pending |\n")
            (root / "coordination/JOURNAL.md").write_text("Earlier work\n")
            for day, status in ((11, "active"), (12, "released")):
                lease = json.loads((root / "coordination/ORCHESTRATOR.json").read_text())
                lease["model_family"] = "Codex orchestrator" if day == 11 else "Claude Code"
                (root / "coordination/ORCHESTRATOR.json").write_text(json.dumps(lease))
                record(root, checkpoint=f"cp-day-{day}", task="next", step=day,
                       action="Read evidence", summary=f"Evidence day{day}", status="in_progress",
                       lease_status=status, base="a" * 40,
                       now=dt.datetime(2026, 10, day, 0, 1, tzinfo=dt.timezone.utc))
            state = json.loads((root / "coordination/state.json").read_text())
            lease = json.loads((root / "coordination/ORCHESTRATOR.json").read_text())
            self.assertEqual(state["checkpoint_id"], lease["checkpoint_id"])
            self.assertEqual(state["updated_utc"], "2026-10-12T00:01:00Z")
            self.assertEqual(state["active_tasks"], [])
            for file in ("coordination/handoffs/CURRENT.md", "coordination/QUEUE.md", "docs/gauntlet/STATE.md"):
                self.assertIn("cp-day-12", (root / file).read_text())
            self.assertIn("Earlier work", (root / "coordination/JOURNAL.md").read_text())
            self.assertTrue((root / "coordination/events/2026-10-11.jsonl").is_file())
            self.assertTrue((root / "coordination/events/2026-10-12.jsonl").is_file())
            journal = (root / "coordination/JOURNAL.md").read_text()
            self.assertIn("| codex | cp-day-11", journal)
            self.assertIn("| claude-code | cp-day-12", journal)
            event = json.loads((root / "coordination/events/2026-10-12.jsonl").read_text())
            self.assertEqual(event["actor"], "claude-code")
