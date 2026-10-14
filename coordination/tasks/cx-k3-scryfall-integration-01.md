# Scryfall metadata integration — TAKEN

Parent: orch-kotlin-k3-offline-data-01. Candidate worker branch: component/scryfall-integration-20261014, based on latest agent/cx-thread-recovery-20261013. Claim through coordination/CHAT-TASKS.md before writes; lock scryfall-integration.lock.

Consume component/scryfall-rulings-metadata-20261013 exact0db3298cc752a8433c72416a0f75cfd0fdb45d45. Inspect the production decoder,13-test fixture and component handoff, verify recorded source/test hashes, then bring ONLY scoped source/test/component handoff onto the recovery baseline. Do not merge that branch's old global coordination/model/life changes. Preserve existing source review limitations: structural unregistered decoder, no live publisher compatibility or download authority claim.

Owned deliverables:
- android-app/feature/catalog/src/main/kotlin/com/architectpsg/commandlibrary/feature/catalog/ScryfallRulingsMetadataCodec.kt
- android-app/feature/catalog/src/test/kotlin/com/architectpsg/commandlibrary/feature/catalog/ScryfallRulingsMetadataCodecTest.kt
- coordination/handoffs/cx-k3-scryfall-rulings-metadata-01.md
- this task, your dedicated lock and your board row.

Verification locally from android-app: bash gradlew --no-daemon --console=plain :feature:catalog:testDebugUnitTest :feature:catalog:lintDebug. Read actual XML totals/failures/errors/skips and git diff --check. No emulator, live fetch, new dependency, network transport/installer/DI change or broader refactor. If source hashes differ or a production bug appears, report it before expanding scope.

## Latest continuation delta

TAKEN by codex-scryfall-integration-20261014 on component/scryfall-integration-20261014, recovery base07fdbd0. Dedicated lock reserves only the named decoder/test/handoff and this task/board row. No implementation begun. Next: publish claim, verify upstream hashes, consume the three scoped files and run catalog tests/lint.
