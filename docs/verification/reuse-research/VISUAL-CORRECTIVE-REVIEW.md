Independent focused source review: APPROVE
Candidate: c377542648ec14bedbf3e0e17a9c202a4cb64fd9
Corrective source: 373d258f0978948d3038e23055740f4f2f307b6f, handoff candidate 8babcb2a49d665d2a458fb14edc8b7fcc02163db, plus root's two-line DpRect dimension correction.
Scope: Task2 professional visual foundation and prior BLOCK review 7426ec7; unrelated root compatibility/CI changes excluded. Read-only candidate review.

Prior blockers resolved in source
- Commander counters resolve source slots across all seats, visibly/spoken distinguish opposing same-name partners by source owner/name/slot ID, and preserve exact AdjustCounter victim/definition/source IDs, including own commander damage.
- Hidden counters are filtered before rendering values, labels or adjustment controls; supplied GameSnapshot is retained unchanged.
- Acceptance assertions now check Oracle text, stats, language/reserved and actual provenance; opposing partners/own IDs, hidden absence/retention, minimum/maximum directions, completed state, and target width/height plus complete unclipped visible bounds. Existing retained CardDetail fields/actions remain intact.
- Explicit shell.R import corrects prior wildcard resource ambiguity. Test-only VISUAL_CAPTURE_PATH marker prints actual absolute path after successful PNG write. Three views by three sizes, two themes and two font scales produce 36 distinct intended PNG names. No GameService stand-in, runtime fixture registration, production synthetic data, engine/model or route change was added.

Native compile blocker and correction
- Parent reported exact 8bab hosted run 38343947831 passed JVM but native compilation failed at VisualSystemAcceptanceTest.kt:128-129; no instrumentation/render execution occurred.
- Independently fetched Google Maven Compose BOM2026.09.00 and its ui-unit/ui-test 1.12.1 source jars in memory. getUnclippedBoundsInRoot returns DpRect; width/height are unimported androidx.compose.ui.unit extensions whose getters are right-left and bottom-top.
- c377542 uses these equivalent edge differences directly. Verified exact test source differs from 8bab only by those two substitutions; source assertions and capture contract are preserved. Shell production UI/resource trees are identical to 8bab.

Checks actually run
- git diff --check 7426ec7..8bab and c377542^..c377542: PASS.
- Exact 8bab head, corrective locked-path ownership, resource XML name uniqueness, 12 configurations/36 unique intended capture names: PASS.
- Exact c377542 source comparison against 8bab and exact pinned Compose API inspection: PASS.
- Native compile/instrumentation/PNG capture/render inspection: NOT RUN by reviewer. Parent's failed 8bab hosted result is identified above, not claimed as reviewer execution.

Acceptance limit / next action
This approves bounded corrective source, not native or aesthetic acceptance. Root must run exact c377542 hosted compile and instrumentation, retain 36 complete PNGs and 36 executed cases without skips, verify actual hosted capture paths/collector behavior, and inspect renders before integration/visual checkpoint. TalkBack/physical Fold7/hinge checks remain unproven. No repository file was edited by reviewer.
