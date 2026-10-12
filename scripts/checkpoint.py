#!/usr/bin/env python3
"""Keep every live continuation pointer on one real UTC checkpoint before commit."""
import argparse
import datetime as dt
import json
from pathlib import Path
import re
import subprocess


def record(root, *, checkpoint, task, step, action, summary, status, lease_status,
           base, now=None, actor=None):
    now = now or dt.datetime.now(dt.timezone.utc)
    timestamp = now.isoformat(timespec="seconds").replace("+00:00", "Z")
    state_path = root / "coordination/state.json"
    state = json.loads(state_path.read_text())
    lease_path = root / "coordination/ORCHESTRATOR.json"
    lease = json.loads(lease_path.read_text())
    if actor is None:
        family = lease.get("model_family", "").lower()
        actor = ("claude-code" if family.startswith("claude") else
                 "codex" if family.startswith("codex") else
                 "gpt" if family.startswith("gpt") else "unknown-model")
    if not re.fullmatch(r"[a-z][a-z0-9_-]{0,63}", actor):
        raise ValueError("Checkpoint actor must be a short single-line identifier")
    queue_path = root / "coordination/QUEUE.md"
    queue = queue_path.read_text()
    if not re.fullmatch(r"[0-9a-f]{40}", base):
        raise ValueError("Checkpoint needs a verified forty-character base SHA")
    if not (root / f"coordination/tasks/{task}.md").is_file():
        raise ValueError("Next task card is missing")
    if f"| {task} |" not in queue:
        raise ValueError("Next task is not in queue")
    if now.tzinfo is None or now.utcoffset() != dt.timedelta(0):
        raise ValueError("Checkpoint clock must be UTC")
    state.update(checkpoint_id=checkpoint, verified_base_head=base,
                 next_task=task, next_step=step, first_action=action,
                 status=status, active_tasks=[task] if lease_status == "active" else [],
                 updated_utc=timestamp, last_completed=summary)
    lease.update(checkpoint_id=checkpoint, status=lease_status, task_id=task,
                 heartbeat_utc=timestamp,
                 expires_utc=(now + dt.timedelta(minutes=45) if lease_status == "active"
                              else now).isoformat(timespec="seconds").replace("+00:00", "Z"))
    for path, value in ((state_path, state), (lease_path, lease)):
        path.write_text(json.dumps(value, indent=2) + "\n")
    header = f"Checkpoint-ID: {checkpoint}"
    current = (f"# CURRENT\n\n{header}\n\nNext task: {task}\n"
               f"Next step: {step}. {action}\n\nUpdated UTC: {timestamp}\n"
               f"Status: {status}; lease: {lease_status}.\n"
               f"Verified base: {base}.\n\n{summary}\n\n"
               "Read task card and latest gate report for commands/evidence. "
               "Regular-chat packets: coordination/chat-handoffs/README.md. "
               "No unrun application/device check is a pass.\n")
    (root / "coordination/handoffs/CURRENT.md").write_text(current)
    (root / "docs/gauntlet/STATE.md").write_text(
        f"# STATE\n\nCheckpoint: {checkpoint}. Updated UTC: {timestamp}.\n\n"
        f"Stage: {state['stage']}. Status: {status}. Next: {task} step{step}.\n"
        f"{action}\n\n{summary}\n\nRead CURRENT, machine state and task card.\n")
    queue = re.sub(r"Checkpoint-ID:[^\n]*", header + f". Next: {task} step{step}; {status}.",
                   queue, count=1)
    queue_path.write_text(queue)
    event = {"utc": timestamp, "actor": actor, "checkpoint": checkpoint, "base": base,
             "task": task, "step": step, "status": status, "summary": summary}
    event_dir = root / "coordination/events"
    event_dir.mkdir(exist_ok=True)
    with (event_dir / f"{now.date()}.jsonl").open("a") as file:
        file.write(json.dumps(event) + "\n")
    with (root / "coordination/JOURNAL.md").open("a") as file:
        file.write(f"\n{timestamp} | {actor} | {checkpoint} | containing commit | {summary}\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    for flag in ("checkpoint", "task", "action", "summary", "status"):
        parser.add_argument("--" + flag, required=True)
    parser.add_argument("--step", type=int, required=True)
    parser.add_argument("--lease", choices=("active", "released"), required=True)
    parser.add_argument("--base")
    parser.add_argument("--actor", help="Explicit author identifier; otherwise derived from recorded lease model_family")
    args = parser.parse_args()
    base = args.base or subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=args.root, text=True).strip()
    record(args.root, checkpoint=args.checkpoint, task=args.task, step=args.step,
           action=args.action, summary=args.summary, status=args.status,
           lease_status=args.lease, base=base, actor=args.actor)


if __name__ == "__main__":
    main()
