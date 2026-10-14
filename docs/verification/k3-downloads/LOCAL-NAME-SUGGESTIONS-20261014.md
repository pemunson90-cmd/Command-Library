# Local name autocomplete

Main card search now debounces valid name drafts250ms and asks the existing local indexed CatalogLookup for at most6 rows. Suggestions remain separate from submitted search pages. Edits/field changes/reset/explicit submit cancel and invalidate old work; publication verifies ticket and known active generation. Generation changes clear cached rows, and selection only opens a still-current actual returned card. Oracle-text mode and commander pickers retain explicit submission. No network/import/schema/public-contract change.

Cards UI exposes externalized suggestion/loading/error text and48dp options; selecting opens the actual card while preserving the draft. Unknown or failed reads never invent a card.

Focused gate SUCCESS53s: SearchControllerTest19, ShellUiTest16 and NameSuggestionsUiTest1, three XML/36tests0failure/error/skip; native shared test Kotlin compiles. New cases exercise debounce/rapid edits, cancellation on submit, generation rejection/cache invalidation, unsupported/invalid draft suppression, truthful errors, and large-font actual suggestion→detail/no import. Existing shell regressions pass. Retained /tmp/local-name-suggestions-xml, log /tmp/local-name-suggestions.log. Synthetic UI/controller ports validate presentation; earlier actual SQLite tests establish the reused name-prefix index, not genuine full-corpus performance. No repeat whole/emulator gate; prior1113 boundary and native r3 exclude this unit.

Plan docs/superpowers/plans/2026-10-14-local-name-suggestions.md. Full AST execution/FullLean/context/full-source/performance remain incomplete. Local only while configured GitHub token is rejected; no PR/merge/release/finished APK.
