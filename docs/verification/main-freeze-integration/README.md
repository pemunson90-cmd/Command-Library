# Frozen development integration — merge HOLD

Current status: MERGED after Pat's explicit approval, exact guarded PR21 merge `be663b2b48a83c35f52586a3f804ca16a0fbf2cc`. Root acceptance and live-branch routing: [ROOT-MERGE-20261014.md](ROOT-MERGE-20261014.md). Android v1 remains incomplete; no APK delivery.

The following preparation report is retained as historical evidence. Its old HOLD and next-action text is superseded by that acceptance record; use the named live continuation branch for current assignments.

## Historical preparation report

Authorized by Pat's relayed orchestrator ACK on 2026-10-14 for isolated PR preparation and testing only. Final merge remains HOLD for the live orchestrator's review of the exact candidate. This is an internal development checkpoint; no unfinished APK delivery or complete-v1 acceptance is authorized.

## Scope and immutable inputs

- Approved source freeze: `5a888461a4af5185054152420d46b0e2bfd24bd4`.
- Target main at preparation: `6f841cc4f42c84e1db6bd3fb1a0c0b7b4d3a5e31`.
- Branch: `component/main-freeze-integration-20261014`.
- Isolated checkout: `/workspace/main-freeze-integration`.

Android product/tests/fixtures, workflows and scripts are byte-identical to the approved freeze. Architecture/specification/verification documents and durable rulings are carried from that freeze as checkpoint evidence. No newer codec3/source-factory/history work was included. All existing worktrees and the live recovery branch were preserved.

## Coordination exclusion

No `coordination/**`, `CONTINUE.md`, `AGENTS.md`, `CLAUDE.md`, `CLAUDE-PARALLEL.md`, `README.md` or `docs/gauntlet/STATE.md` changes are included. Their base-main state was not adopted as live ownership; the recovery orchestrator remains sole owner. This PR does not update global pointers, acquire/release another session's locks, import its TAKEN board or create a competing lease. Frozen evidence documents may reference component task/handoff paths that exist on the recovery/freeze branch rather than main; consult the immutable source ref for those historical details. Do not treat their old next actions as current assignments.

Before merge the live orchestrator must approve how main's continuation metadata is reconciled. Preserve its current checkout/lease/locks/newer work. Never automatically copy frozen global metadata or replace the live continuation branch with this PR.

## Verification

Actual local command: `bash android-app/gradlew -p android-app --no-daemon --console=plain check :app:assembleDebug assembleDebugAndroidTest`: BUILD SUCCESSFUL in5m8s;563tasks executed. Fresh87XML reports:908JVM tests,0failures/errors/skips. All7 instrumented-module test APKs compiled. Current schema export is unchanged. Continuity passed;31 Python script tests passed (missing-capture messages are expected negative fixtures). `git diff --cached --check` and SDK36.0.0 `zipalign -c -P 16 4` passed. Internal debug APK SHA256 `41d491c2105bffe3aaf263bf29393c793744926af804683d595b4845d8c4e15e`. No APK delivery is authorized.

Hosted PR gate is not_run at commit creation; report its exact run/head/merge tree separately after PR creation rather than treating historical schema-native results as a pass for this candidate. No final release, physical Fold7, authentic variant-source or operational GameService claim follows from these checks.

## Review and remaining boundaries

Independent configured Astra-high risk review APPROVE for frozen PR preparation on staged tree `babb45ef3ac3f42c0bf734f5677c88b38be1365c`; no source blockers within the requested scope. Reviewer performed read-only source/diff/schema/fixture checks; behavioral/native/hosted checks not_run by reviewer. This evidence document is the only added content beyond that reviewed tree. Native-exclusive71e8cc1/20a9283 are deliberately not cherry-picked: recovery event/random DTOs supersede them, and CLLP private framing is incompatible with CLIF codec. Recovery's fixed10000-or-tail batching differs from the old variable-batch DTO. Compatibility ports require separate ownership/review, not this frozen integration.

Schema4→5→6, payload/projection invariants, idempotent creation bytes/hash, revision+sequence CAS, undo/random history and after-commit publication require acceptance scrutiny. Direct hostSQLite evidence does not establish Android Room concurrency. Writer emitted output is capped, but ByteArrayOutputStream backing capacity can exceed the limit (including the 4MiB+64KiB checkpoint case), and temporary UTF-8 scratch allocation occurs before remaining-frame checks. This is not equivalent to the old custom writer allocation guarantee. Reviewer classified it as a documented limitation, not a required rewrite for this frozen unbound integration; any stronger requirement remains root-owned.

## Next and recovery

The integration commit retains both main and freeze parents. After excluding85 mutable paths, its pre-evidence tree exactly reconstructed reviewed/tested `babb45ef3ac3f42c0bf734f5677c88b38be1365c`; no product changed during ancestry preparation. Open one draft PR after local verification; use the existing PR JVM/build plus default API36 and continuity gates. No duplicate manual emulator/matrix dispatch. Retained debug artifacts are internal tests only. Root reviews exact head/tested merge tree and keeps final merge on HOLD until authorized. If main moves, inspect differences before proceeding; stop at schema/codec/ownership conflicts.

Before acceptance failure leaves main untouched. Keep candidate and logs for scoped remediation. After schema6 opens data, a binary downgrade to schema4 is unsafe: preserve data and prefer a reviewed forward fix; no destructive fallback or automatic user-data removal. Repository reverts remain separately authorized and must preserve data compatibility.
