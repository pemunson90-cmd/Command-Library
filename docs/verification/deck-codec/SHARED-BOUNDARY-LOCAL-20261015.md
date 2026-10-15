# Shared deck domain/codec boundary — local evidence 2026-10-15

Design r1 and exact model/reducer source reviewed ACK by native gpt-6-astra high. Required rename/Replace regression also ACK, test SHA256 93276fa1c8b74214ea9c7dde73c731dc126058dc04b0baa7973528a6aeccb682. Independently parsed fixed CLDK1 golden:125bytes, wire SHA256 d72b3abd9d223ce8e0bbc91fbbce384022e3a5ac79e0783894281c84af5f04a8, no trailing bytes.

Configured local Java17/Gradle9.6/SDK37 offline shared boundary command: core:model:test core:common:test feature:decks:test feature:links:test app:compileDebugKotlin feature:shell:compileDebugAndroidTestKotlin app:compileDebugAndroidTestKotlin. SUCCESS39s,127tasks25executed; /tmp/cl-deck-contract-r1/evidence/r1.log. Added one reviewer-requested test afterward; feature:decks:test SUCCESS12s,8tasks2executed; r2.log. Production code unchanged between checks.

Final scoped XML readback:

- core/model: 14 XML, tests/failures/errors/skips [85, 0, 0, 0]
- core/common: 5 XML, tests/failures/errors/skips [24, 0, 0, 0]
- feature/decks: 2 XML, tests/failures/errors/skips [19, 0, 0, 0]
- feature/links: 2 XML, tests/failures/errors/skips [35, 0, 0, 0]

No codec implementation, Room schema10, persistent repository, UI workflow or native execution claimed. Existing schema9 and dependency pins unchanged. Next isolated producers consume exactly this accepted domain and golden; root retains schema/DI/shared ownership.
