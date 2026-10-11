# Lock file format

Path: `coordination/locks/<component>.lock` (one file per component, so claims never merge-conflict).
Claim: the orchestrator commits the lock file to the integration branch and pushes. A rejected push because the file already exists means the claim failed.
Release: the holding orchestrator deletes the file after merge.

```
component: <component>
orchestrator: claude-code | codex
task: <task-id>
branch: agent/<task-id>
base_commit: <full sha>
claimed: <YYYY-MM-DD>
```
