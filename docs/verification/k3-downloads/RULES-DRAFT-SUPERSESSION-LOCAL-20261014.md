# Rules draft supersession

Changed rules drafts now invalidate the search epoch, cancel pending keyword/search/page/detail reads and clear prior results, selection, cursor, fallback and input errors. Exact draft text and installed/import status remain intact; editing performs no lookup. Explicit submission invalidates pending work before validating even blank/oversized input. Existing submit/search/corpus pinning and known-keyword behavior are preserved.

Bounded internal presentation change under D-17/D-28, using the existing clearSearch/epoch mechanism. No public contract, schema, acquisition, source permission or root binding change. Added three regression methods covering completed selection/cursor/status, pending keyword, and pending page/detail followed by invalid submission. Earlier card-keyword UI coverage remains included.

Actual local command: `:feature:shell:testDebugUnitTest --tests '*RulesControllerTest' --tests '*CardKeywordUiTest' :feature:shell:compileDebugAndroidTestKotlin`. SUCCESS35s,40tasks6executed34up-to-date; twoXML24tests (19controller/5Compose),0failure/error/skip. Log `/tmp/rules-draft-supersession.log`; XML copied to `/tmp/rules-draft-supersession-xml`. Native test Kotlin compiles; no device execution or new whole-suite claim.

The preceding combined1233test/129XML boundary and frozen9c1573b/run38657175303 predate this bounded change. Keep their evidence scoped to those sources; do not update the frozen branch or trigger another device run for this edit.
