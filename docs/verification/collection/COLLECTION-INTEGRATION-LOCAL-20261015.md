# Collection app integration — local verification, 2026-10-15

The real collection journal, owned-quantity allocator and reviewed presentation are now composed through AppGraph. A card's current qualified printing opens an eligible-finish add form; collection edits use explicit preview/confirm, stored revisions, grouped undo and frozen uncertain-ack retry. Deck commit acknowledgements invalidate allocation even when a subsequent head reload fails or another deck is selected. No provider price or complete collection acceptance is claimed.

## Exact source and review

Root continuation parent f378484; integrated worker commits942e2ad/f378484, backend461f7af. Final staged Android tree `b6b9a485bc7ccbacfcd15d07585f9d9b624a9aa2`. Root retains shared contracts/schema/DI/navigation and final merge authority. Independent actual Astra high source and evidence reviews ACKed composition, durability, controller/UI and final correction. Reviewers inspected source/log/XML; they did not run additional builds.

## Executed verification

Pinned local JDK17.0.16, Gradle9.6/AGP9.4.1, existing offline dependency cache and SDK37; no upgrades. Sole serialized Gradle slot, max-workers2.

R1 executed collection, pricing and decks JVM tests, storage and shell debug unit tests, and app tests filtered to InventoryRoomIntegrationTest, DeckSchemaRoomIntegrationTest and EnglishCatalogAccessIntegrationTest. Also shell lintDebug and app/shell/storage compileDebugAndroidTestKotlin. Command task names: `:feature:collection:test :feature:pricing:test :feature:decks:test :data:storage:testDebugUnitTest :feature:shell:testDebugUnitTest :app:testDebugUnitTest --tests '*InventoryRoomIntegrationTest' --tests '*DeckSchemaRoomIntegrationTest' --tests '*EnglishCatalogAccessIntegrationTest' :feature:shell:lintDebug :app:compileDebugAndroidTestKotlin :feature:shell:compileDebugAndroidTestKotlin :data:storage:compileDebugAndroidTestKotlin`, with `--offline --console=plain --max-workers=2`.

R1 SUCCESS7m33s, **683 tests/85 XML, zero failures/errors/skips**: collection32, pricing25, decks34, storage333, shell247, app12. All six test tasks executed. Lint zero errors, one inherited profile plural warning. All three Android-test Kotlin compile tasks executed. R1 Android tree `d3400f924457334cf5894a67411742e978853a8f`.

Actual host Room evidence covers fresh/schema9/11/12→13 preservation, inventory merge→stored deck allocation→undo→historical replay→reserved-ID rejection, independent database connection/stale CAS, and cancellation during post-open capability inspection closing the unpublished database before successful retry. No nested capability transaction or guessed parameter limit is used.

Review caught a vacuous pending-preview absence assertion after root plural-resource polishing. R1 does not establish that assertion. R2 failed8s at test compilation only: mistakenly used unavailable rule.activity; repaired to existing context.resources. R3 executed `:feature:shell:testDebugUnitTest --tests '*CollectionUiTest'`, SUCCESS30s, **4 tests zero failures/errors/skips**. It proves the exact pluralized preview exists before confirmation and disappears while reconciliation is pending. R1→R3 differs only in that test file; production is identical. Four cases are reruns, not four additional unique tests.

Final test SHA8487cfc20214351f23a6d26993e007048554568b7a3b0b0086b091297e26e416; R3 log SHA7045fb8ec5278e21a7e6ddd55d28d1eb48abaf0f8a6e9b6ef4805b72a7d05bba. R1 log SHAa0ce05cf1cfdfe63d318d7ee541b035d6f0368be9b8645fa4c40a42c6d5586f2. Local evidence `/tmp/cl-inventory-projection-01/evidence/collection-integration-r{1,2,3}.log`; archived R1 XML in collection-integration-r1-xml, counts in collection-integration-r1-summary.json, each exact tested-tree manifest adjacent. Prior worker failure/fix lineage remains in coordination/handoffs/cx-campaign-collection-presentation-01.md and the backend handoff.

## Unrun and retained scope

Host Compose/SQLite/Room is not Android instrumentation, process-kill or physical Fold7 evidence. Native test Kotlin compilation is not native execution. Authentic publisher identity, full S12/C01/B10 finish editing/mixed bulk/CSV, durable invalid drafts/autosave, prices, complete v1, accessibility/device/performance/canonical release remain open. Existing source/consent/GameService holds and finished-APK hold remain. Frozen historical native run is unchanged. No main merge or release performed.

Next: named immutable deck checkpoint/restore design and independent contract review, preserving package graphs and existing edit/undo/duplicate consumers before additive migration or fan-out.

## Additional mixed-bulk storage oracle, 2026-10-15

One new actual AppGraph/Room workflow executed against accepted1bed55c plus InventoryRoomIntegrationTest addition: three distinct unresolved/raw lots, preview of two complete replacements, exact zero quantity/condition/location/finish while preserving identity/notes/acquisition and third lot, one atomic apply, independent connection read, one grouped undo, historical replay without overwriting undone head, second connection reopen. SUCCESS42s,1test1XML,0failures/errors/skips. This validates storage behavior, not catalog finish eligibility or the still-active new bulk presentation. Evidence /tmp/cl-deck-versions-integration-01/evidence/bulk-room-oracle-attempt1-{log,source.sha256,counts.json,xml/}; tested source pin retained. Sole postrun source change clarifies that preview assertion does not execute cancellation; no executable changes, finalpin bulk-room-oracle-final-source.sha256. Existing source/native/physical/fullS12 holds remain.
