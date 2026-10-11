# Exclusive ownership and shared registry

Component owned roots: packages/<component>/** for all20 component IDs in registry.json. Distinct prefixes cannot overlap. Shell presentation is packages/shell/**, not apps/**. Vault/inference native plugin code remains inside its package; launcher/native application registration is shared.

Orchestrator-only shared roots: apps/command_library/**, root pubspec/lock/workspace/build configuration, database/registry/**, config/**, .github/workflows/**, tool/**, coordination/** and docs/gauntlet/**. Future session scripts are scripts/**, also orchestrator-only. Docs architecture authored by named architect task only; shared registry/CI/interface inventory root-owned.

Source migrations/query tables each feature requests exact registration; storage executes registry but its package lock does not grant database/registry writes. App bootstrap/DI/routes/native launchers consume package exports; shell lock doesn't grant app entry edits. No shared-file edit disguised inside broad component glob.

A worker base branch agent/<task-id> starts at verified main/base/dependency commits, own worktree if available; snapshot-only writer limited to documentation unless safe isolated editing/publication is independently established. Lock claim names this prefix, branch and base. Root validates changed paths plus globintersection and only merges tests/reviewer-approved changes sequentially. Ambiguous path belongs to root until a specific exclusive assignment is committed.

Code author doesn't review its own diff. Tool-neutral role briefs/task cards govern provider-specific agent adapters; exactly named output(s), no recursive undocumented delegation. Default max3 component workers when available, but serialize during unresolved transport; one orchestrator lease. These defaults are execution choices delegated by D17, not proof D08 was previously ruled explicitly.
