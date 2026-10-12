# Task cx-settings-01: correct reviewed settings defects

Status: completed and integrated with reviewed remediation; tested source4f20764, hosted run38199662988. Settings lock released. K2 app/runtime integration remains pending.

Branch agent/cx-settings-01, worktree /workspace/Command-Library-cx-settings. Exclusive writes android-app/feature/settings/** and coordination/handoffs/cx-settings-01.md. Core/build/CI/coordination state belong to root. No original branch overwrite.

Fix only the bounded findings: symmetric maximum16MiB encoded/read store bound including trailer; reject prospective oversized state before a successful commit and preserve prior file/revision. Strict UTF-8 encoding/decoding reports malformed input rather than replacing characters, particularly consent ResourceUri/identities. Invalid in-memory string/size yields typed input failure; malformed persisted data yields corruption and remains untouched. Add meaningful repository restart/file-preservation regressions for oversized cumulative state, malformed persisted UTF-8 with valid CRC, and unpaired surrogate input.

Avoid uncertain API24 Math clock dependency with safe integer division/remainder arithmetic valid on API23; test zero, positive/negative/pre-epoch boundaries. Run hosted NewApi-fatal lint/assemble/JVM tests. Existing scope-specific consent, atomic revisions, cause-chain corruption handling and noBackup installation ID behavior remain real; do not weaken protections to make tests pass.

Document initial cache0/rulings-off as temporary K2 operational adaptation while those services are absent, never final product defaults. SPEC A03/A05/K01 retained-rulings/online-art obligations remain required with explicit migration/transition before those consumers ship. No user approval question is needed for this documented limited foundation state. No image or network placeholder is added.

Save/commit scoped fixes and tests, push only your branch, write READY-FOR-REVIEW handoff with exact commands/results or not_run. Root triggers observed hosted workflow and independently rechecks before integration. No recursive agents or standalone source research.
