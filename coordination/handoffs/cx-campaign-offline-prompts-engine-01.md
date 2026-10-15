# Offline prompts engine attempt01 — SOURCE02 freeze

Base: `6a910a134bf138dd472d477bc53ca2b02fba7b51`.
Branch: `agent/campaign-offline-prompts-engine-20261015`.
Worktree: `/workspace/command-library-offline-prompts-engine-01`.
Contract: `offline-prompts-v1-20261015`, core SHA-256 `ff33dad5ed129fdf86cc9fe9210b56c40312ddcd6316e50368d632371b85b0c0`.

Implemented `LocalOfflineDeckPrompts(query, clock, validate)` against the frozen core ABI. Four external v1 templates join by enum IDs/version. Session override is used only for rendering: result.template remains the exact unmodified shipped templates() entry; result.overrideUsed and copied overrideBodySHA256 identify the actual body. This records the root cross-consumer clarification without changing public contracts.

Mandatory JSON includes source-qualified raw/Oracle/printing identities, language, entry IDs, boards, quantities, format, budget, deck revision and explicit user/options/context. Unknown/Absent serialize only their state. Notes/description/primer require explicit includePrivateNotes. No settings/profile/provider URL/diagnostic serialization. No mutation, provider, acquisition, Apply or history adapter.

Mandatory context is strict UTF8 <=2MiB, before optional lookups. User question/constraints/body <=32KiB each. Explicit catalog references are bounded; supported enrichment requires one exact English Scryfall generation. <=50 distinct exact NAME/CANONICAL/PageRequest(2) lookups, deduplicated by requested name. Enrichment requires unique verified source/generation/language/Oracle ID. Optional blocks are omitted whole at <=128KiB per entry and <=512KiB rendered total; result/artifact counts and entryId omission disclosure remain explicit. The mandatory deck is never clipped. Original card data is bounded independently before appending; transient review lookups never retain 50 full CardRecords.

Review accepts strict UTF8 <=256KiB, <=50 balanced [[name]] references, <=512 UTF8 bytes/name. Only marked references are scanned. FOUND/AMBIGUOUS/NOT_FOUND/UNAVAILABLE remain distinct; inconsistent source/provenance cannot become FOUND. Results retain only exact source-qualified Oracle IDs and canonical names. card() rejects a snapshot mismatch before I/O, then resolves again at the originating snapshot and requires the same Oracle ID. Format legality/color/budget/ownership/acquisition cost remain explicitly Unknown; Inline(n) is acquisition cost in all templates. Surprise angle comes only from exact permitted declaration lines.

Bounded internal implementation choices: <=10000 deck entries, <=128 deck catalog references, <=4096 UTF8 bytes/identity, <=128 bytes catalog source/generation, <=32 bytes catalog language, <=32 optional faces, <=256 optional keywords, bounded decimal precision/scale <=1024. A malformed/oversized mandatory input is typed INVALID_INPUT, never partial success. Ordinary optional query failures become disclosed omissions. Cancellation propagates before/after ports and during bounded scans.

Verification: initial clean branch/base/relevant Git environment checked; source self-review against frozen ABI and master packet completed. 17 meaningful JUnit tests authored with faithful injected LocalCardQuery/Clock/validator ports. Tests/compilers/build/lint/runtime/app/native execution: `not_run` by explicit worker packet; root owns the one coherent assembled gate. No success or API-compile claim is made here. No shared paths edited; no commit made pending independent SOURCE review and exact root authorization.

SOURCE01 lineage: independent root review reported one HOLD: raw equals(ignoreCase=true) disagreed with the prepared catalog NAME normalization policy. All other architecture/source dispositions were retained. SOURCE01 manifest remains at `/tmp/cl-offline-prompts-engine-01/source-freeze.sha256` and its preserved alias `source-freeze-01.sha256`, SHA-256 `2c350524969b5d0dc588e64c6b3a32745f61e859de55b04a0a773a80f8660754`; SOURCE01 handoff SHA-256 `a241af32f0dc2bd6916b4a01a4e5c0986bf02ba656242007f24d61058c8f339c` is preserved at `/tmp/cl-offline-prompts-engine-01/handoff-source01.md`.

SOURCE02 changes only OfflineReplyReferences.kt, LocalOfflineDeckPromptsTest.kt and this handoff. Verified policy directly at ScryfallCodec.normalize and CatalogQueryValidation: NFKC → lowercase(Locale.ROOT) → trim. lookup now compares normalized bounded copies of requested and original names: strict raw UTF8 <=512 bytes before normalization, strict normalized UTF8 <=4096 bytes afterward. Query/rawName/original names/source-qualified exact Oracle IDs remain unchanged. Added equivalent fullwidth/composed/decomposed/trim cases in both normalization directions, including render/review/detail preservation, and rejection of genuinely different returned names, dropped A- prefix or dropped multiface suffix. Existing source/generation/Oracle fences remain intact. Tests/builds/compilers/lint/runtime remain `not_run`; independent narrow SOURCE02 review pending.

Frozen SOURCE02 source hashes (handoff hash is recorded separately in the external 11-path SOURCE02 manifest to avoid self-reference):

```
2c71ddfa1de0bdedf632bf92aee74ff92a64779457a25c0a46e25d5cdbbbe07f  android-app/feature/ai/src/main/kotlin/com/architectpsg/commandlibrary/feature/ai/LocalOfflineDeckPrompts.kt
ff9a1dd872207989614a196e1ecf06376d53ead3d0098ca39a3e7a46ad2817db  android-app/feature/ai/src/main/kotlin/com/architectpsg/commandlibrary/feature/ai/OfflineDeckContext.kt
c3d5e466cde3f7403cd94e683694f73cbd82ccccd7713645bc343aa8b8fabdb8  android-app/feature/ai/src/main/kotlin/com/architectpsg/commandlibrary/feature/ai/OfflinePromptText.kt
1e0e44c0b2c8236eb33c0398fc217ee54734784acd000e5b27f3ddbd66ba6f26  android-app/feature/ai/src/main/kotlin/com/architectpsg/commandlibrary/feature/ai/OfflineReplyReferences.kt
7a2ba53bdfb3108c07a6767ebe5e5748768127f740baccf22f225896608aae9f  android-app/feature/ai/src/main/resources/prompts/v1/chat.txt
93a2ccc97be64d72388d5a7ca5d142f01dfe419d1c168c491f7c01fa74f24d0b  android-app/feature/ai/src/main/resources/prompts/v1/roast.txt
3fa7dbb0351b22900515f189e7ea78e638a4295a4d504a718646054b7e3d17ce  android-app/feature/ai/src/main/resources/prompts/v1/surprise.txt
e55c943a4be19a3dd800a3f23cfe3796a5bcb0a6811521da62f589fe9731e2e3  android-app/feature/ai/src/main/resources/prompts/v1/upgrade.txt
d8a326deac5385be63ad3e8f6bda894c09c87a93c4a0cd0a1a6613a55ff83318  android-app/feature/ai/src/test/kotlin/com/architectpsg/commandlibrary/feature/ai/LocalOfflineDeckPromptsTest.kt
7eedddb1a3315c861d6f0435d5afc5c7fbfcc2a77f0ff30f53f596ec38dafc8b  android-app/feature/ai/src/test/kotlin/com/architectpsg/commandlibrary/feature/ai/OfflinePromptTextTest.kt
```

## Latest continuation delta

Repaired the single independent SOURCE01 name-normalization HOLD using the actual catalog policy and added two focused tests (17 total authored). Preserved original SOURCE01 pins/handoff; froze SOURCE02 exact 11 paths. Runtime remains not_run; no commit. Next: root narrow independent SOURCE02 review, exact scoped commit authorization, then root combined assembled gate.
