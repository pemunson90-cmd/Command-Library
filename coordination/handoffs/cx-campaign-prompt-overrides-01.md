# Prompt override SQL producer attempt01

## Latest continuation delta

Authored only the three new paths granted by the canonical root `campaign-prompt-overrides.lock`, on `agent/campaign-prompt-overrides-20261015` in `/workspace/command-library-prompt-overrides-01`, exact base `23a7f3f643f6ec0a578ecc0b199bd88c97000bdd`. Initial cwd/top/branch/HEAD/status were verified; initial worktree was clean and neither GIT_DIR nor GIT_WORK_TREE was set. Read AGENTS/CONTINUE and the whole canonical root task packet including READY, dispatch and TAKEN. Inherited coordination is not current lock authority.

`SqlLocalPromptOverrides(db, clock)` implements the frozen public port using `OfflinePromptText.utf8Size/bind` and each mode's actual shippedVersion. Validation uses the engine's three empty mandatory bindings before any database call; there is no parser/model/envelope/framework copy. Stable explicit mode tags, raw strict UTF8 format1, type/length/hash-shape preflight before BLOB fetch, strict decoding/checksum, current-version grammar verification, different-template-version preservation, unsupported newer wire format, retained reset rows, CAS before no-op, overflow before clock/write, affected-row checking, cancellation propagation and per-mode SQL predicates are present. There is no automatic adoption/retry or rollback claim after possible commit.

Ten bounded tests were authored against existing actual `HostLifeDatabase(initialize=false)` plus `Migration16To17`: engine grammar/invalid Unicode/oversize before I/O; exact 32768-byte Unicode round trip; all-four-mode/reopen/reset/ABA/old-row conservation; no-op/CAS/clock; future-template review and explicit adoption/current corruption/invalid UTF8; future wire rejection and conservation; metadata corruption preflight and checksum failure; overflow; affected-count rollback; cancellation before I/O/during write/after commit reload. Test-only request observation delegates to the actual host adapter; no production substitute was introduced. These do not claim Android Room/UI/release acceptance.

Source inspection verified frozen table/contract/parser compatibility, existing test dependency on feature:ai, scoped SQL projections/predicates, typed outcomes, and exact owned path inventory. `git diff --check --no-index /dev/null <source-or-test>` emitted no whitespace diagnostics (exit1 denotes new-file diff). Compilation, Gradle, lint, all tests, network and child agents: **not_run** under the dispatch restriction. No shared existing edits, commits, push, reset, clean, stash, process termination or foreign-lock writes occurred.

Source SHA256: `29bcc29e9990c23e0a6bef79c685d9497f954f19918f41a8225f31eb0b107f67`.
Test SHA256: `186da4611ec160f4dab188c91fd9ce3b2e8a9a45b75337410c39f82e17633fa5`.
Exact three-file SHA256 manifest (including this handoff) is frozen outside git at `/tmp/cl-prompt-overrides-worker-01/SHA256SUMS`; the root receives its full contents. No self-referential handoff hash is embedded here.

Next action: root independent source review of these exact bytes; worker awaits review and explicit exact-three-path commit permission. Root owns integration, the sole build slot, assembled feature verification, real Room16→17 preservation and advanced UI save/reopen/reset acceptance. No blocker or shared-contract change is proposed.
