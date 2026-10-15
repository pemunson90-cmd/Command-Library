# cx-campaign-offline-prompts-ui-01 attempt01 — SOURCE FROZEN for root review

Owner: /root/implement_offline_prompts_ui. Canonical authority: root task packet and root coordination/locks/campaign-offline-prompts-ui.lock. Worktree /workspace/command-library-offline-prompts-ui-01; branch agent/campaign-offline-prompts-ui-20261015; parent/base 6a910a134bf138dd472d477bc53ca2b02fba7b51; contract offline-prompts-v1-20261015 / ff33dad5ed129fdf86cc9fe9210b56c40312ddcd6316e50368d632371b85b0c0.

Only the five newly owned paths were written. No existing/shared/core/build/coordination authority file was edited. Initial top/branch/HEAD/clean/relevant GIT environment checks matched packet. No commit has been made; root exact authorization is required after independent source review.

## Implementation

Frozen root signatures are unchanged: OfflinePromptController(scope, dispatcher, prompts, currentDeck), open(deck,snapshot), observe(deck), setSnapshot(snapshot), hide(), copyPrompt(copy); OfflinePromptDialog(controller,onCopy,onUseCurrentData). State belongs to retained root owner; observing the same deck does not reopen, render, copy, refresh or lose session inputs. Reopening the same saved deck preserves input fields and per-mode session overrides while explicitly adopting the passed snapshot. Changed/departed deck frames hide and revoke operations; another deck starts with private notes excluded.

Four modes join templates by enum templateId/shippedVersion, never title/order. All input fields are kept intact when admitted; strict UTF-8 checks bound UTF-16 length before scanning and reject malformed surrogates. Rejected fields block actions until that same field is corrected or its template explicitly reset. Session template edits remain per-mode; default notes excluded. Explicit render produces actual complete artifact preview; Copy reads only current controller artifact and reports platform boolean success/failure. No clipboard service is accessed here.

Every edit/source/mode/privacy/override/hide/deck transition cancels and revokes async work. Identity tickets and live currentDeck checks fence render/review/detail completion and every meaningful action. Native CancellationException propagates. Typed port errors become existing static resource Problems, never diagnostic text. Returned deck/revision/template/version/mode/snapshot/override/raw reply/reference-source frames are checked before acceptance. Unknown legality/color/budget/ownership/cost is explicit, with a dedicated acquisition COST Unknown tag; no zero or quantity substitution. Surprise angle is only the engine stated permitted angle or Not stated. Only FOUND single-source references can request prompts.card at the originating snapshot; original returned CardRecord is validated and rendered by existing CardDetail. Review identity guards stale reference callbacks. No provider/runtime/network/Apply/settings/persistence adapters were added.

All required tags are present; additionally offline-prompt-angle and offline-prompt-cost. offline-prompt-preview is directly on actual artifact.rendered Text. Externalized resources, existing ActionRow/problemText/CardDetail helpers, 48dp controls and scrollable surfaces are used. Root retains actual entry/ShellControllers/AppGraph/clipboard/navigation/refresh and app test ownership.

## Evidence and limits

Source-only self-review completed. Python XML parser accepted the new resources; all offline UI string references resolve and owned sources have clean line endings/trailing whitespace. Ten faithful OfflineDeckPrompts controller test cases cover explicit mode/private/override mapping, live current-artifact copy and platform outcomes, field-specific rejection, UTF-8 boundaries, noncooperative latest-result fences for render/review/detail, deck/revision/foreign-response refusal, review statuses, original detail success/unavailable handling, and native cancellation/static errors. No fake production adapter was introduced.

Runtime: **not_run**. Builds/compiler/lint/tests/network: **not_run**, per lane restriction. Actual app/recreation/clipboard/query/device verification remains root assembled focused gate; no passing/runtime/full-AI claim is made.

Root confirmed result.template remains the unmodified shipped template from templates() even with overrides; controller exact equality matches this clarification. No ABI change or unresolved interface proposal.

## Frozen source SHA-256

- `c4207fa0400e247bc398303cc989c4c496f0137d17cdc2331f7e6d7ca80e8466` — `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/OfflinePromptController.kt`
- `7dea57b87e6e77012187c276feb928307c5395614c0245e68e47f788c071d16e` — `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/decks/OfflinePromptDialog.kt`
- `02768ef9343e113fa1e3417fba5ca99ba9935d4397c72b5ab2dbc8506f84b5f4` — `android-app/feature/shell/src/main/res/values/offline_prompt_strings.xml`
- `78f633cf66f2e64d680e0c9ae27441d1b873e4c3aa4c690c6a67da8c92d90852` — `android-app/feature/shell/src/test/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/OfflinePromptControllerTest.kt`

The handoff itself is the fifth owned file; its exact SHA-256 is transmitted with the complete five-file return manifest outside this file to avoid a self-referential hash. Leave all five files frozen and uncommitted pending root independent source review and exact commit authorization.


## SOURCE02 narrow repair — frozen for independent root review

Owner transferred explicitly by root to /root/repair_offline_prompts_ui in the canonical root lock; original child runtime is no longer live. Verified exact worktree/top, branch and HEAD/base 6a910a134bf138dd472d477bc53ca2b02fba7b51, exactly the five assigned untracked files, and all five SOURCE01 hashes against /tmp/cl-offline-prompts-ui-recovery-01/source01/manifest.json before editing. The preceding SOURCE01 freeze/hashes remain historical evidence, not the current return pins.

SOURCE01 independent review found HOLD: overrides were stored per mode but the rejected OVERRIDE flag was global. Oversized CHAT override → switch ROAST → reset or edit ROAST → switch CHAT could clear CHAT rejection and render its previous admitted value. This finding supersedes SOURCE01's claim that rejection could only be cleared by the same field when applied to mode-specific overrides.

SOURCE02 changes only controller, its existing rejection test and this handoff. Rejected question/constraints/reply remain field-specific. Rejected overrides now retain their own mode set; the existing state.rejected interface projects only the selected mode's override rejection alongside global rejected fields. Correction/reset clears only the current mode's override rejection. Other valid modes can render independently, while returning to the rejected mode stays blocked. Existing invalidation/cancellation, live deck, source, privacy, copy and review fences remain in place; frozen callable signatures and core ABI ff33dad5ed129fdf86cc9fe9210b56c40312ddcd6316e50368d632371b85b0c0 are unchanged.

Extended the existing rejectedFieldCannotBeBypassedByAnotherEditModeOrRender test without adding a test framework or test case. Both correction/reset paths establish a previous CHAT override, reject an oversized replacement, successfully render independent ROAST after reset and edit, and assert that each switch back to CHAT remains INVALID_INPUT with no request or result until explicit CHAT correction/reset. The preexisting question and reply rejection checks remain intact.

Verification: source diff inspection, exact allowed-path status, unchanged SOURCE01 dialog/string pins, unchanged ABI hash and whitespace/line-ending checks only. Runtime/tests/build/compiler/lint/network: **not_run** under root-only Gradle restriction. No runtime success or observed red/green claim. No commit or shared-file edits. Root independent narrow SOURCE02 review and assembled focused runtime gate remain pending.

### SOURCE02 current source SHA-256

- `85d3bb3007d39c5da85fbfb9216eaea6f6546d791ec1ebe08aa6200a3342ccbf` — `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/OfflinePromptController.kt`
- `7dea57b87e6e77012187c276feb928307c5395614c0245e68e47f788c071d16e` — `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/decks/OfflinePromptDialog.kt`
- `02768ef9343e113fa1e3417fba5ca99ba9935d4397c72b5ab2dbc8506f84b5f4` — `android-app/feature/shell/src/main/res/values/offline_prompt_strings.xml`
- `d8ffb1dfd63ec671e2fda970143d9ae6d28c7842dde25e4c740b6f0abc3de9fb` — `android-app/feature/shell/src/test/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/OfflinePromptControllerTest.kt`

Complete current five-file manifest (including this handoff hash): /tmp/cl-offline-prompts-ui-recovery-01/source02.sha256. SOURCE01 historical snapshot remains untouched. All five owned files are frozen and uncommitted pending root review.
