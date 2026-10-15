# Plain-list file import: actual app integration

Status: NOT ACCEPTED YET. Reviewed implementation integrated; focused output-and-import-attempt5 is running. This report records actual failed runs without relabeling them repaired.

## Implemented scope

Actual deck-library entry → one saved-ticket OpenDocument launcher → ContentResolver/provider stream → existing bounded input source → real plain-list codec/controller/graph validator/LocalDeckLibrary/Room → library refresh and saved notice. No automatic deck selection or replacement of an existing editor. Hidden clean editors permit import; visible or dirty/recovery editor barriers block admission before UUID/input construction. Picker cancellation preserves the prior frame/read; accepted replacement alone supersedes it.

The dialog preserves every physical row, blank/error and exact raw card name. Preview names a new deck, preserves duplicate entries/boards/quantities and discloses unspecified printing/finish/language, with no downloads. Immutable exact preview authority, retained unknown-ack recovery and known-ack read-only recovery remain guarded. This implements a bounded application-owned text-file journey; paste UI, export UI, vendor/CSV transfer, full backup/restore and full B08/I01 remain open.

## Actual run lineage

Counts are tests / failures / errors / skips. Runs overlap; do not add them into a distinct-test total. Instrumentation Kotlin compilation is not device execution.

| Run | Exact source | Actual counts | XML | Outcome |
| --- | --- | --- | --- | --- |
| attempt1 | `6622ba9092e001913bd72fbd133ad4340e82e7b0` | 0 / 0 / 0 / 0 | 0 | Shell production compile error; no tests executed. |
| attempt2 | `3d202db78b37d00886f9fa5fd04dcf0dfdcce64c` | 5 / 2 / 0 / 0 | 3 | Two actual import tests failed raw-row visibility after nearest-inner-scroller targeting; prior three app checks passed. |
| diagnostic-attempt3 | `e19ef6e096e8b842306cb03e916adf617bdc49ee` | 5 / 1 / 0 / 0 | 1 | Five UI tests executed; one full saved-message matcher mismatch. App/native targets not reached. |
| assembled-attempt4 | `a3f4f62f21ab16a6deb79e75547ffc112b99e6b5` | 44 / 1 / 0 / 0 | 6 | All physical rows passed. One final detail-heading visibility failure; all other 43 checks passed. Both instrumentation Kotlin targets compiled. |

Evidence root: `/tmp/cl-plain-deck-import-app-integration-01/evidence/`. Each directory contains source SHA/tree/all tracked Android pins and command-script SHA, complete log, exit status, fresh XML and unchanged-source readback. Diagnostic attempt3 also has a scope-note correcting its inherited broad metadata: its narrow runner executed only five UI tests before failing, not the broader campaign scope. The diagnostic app tree printer never ran; no tree capture is claimed.

## Repairs and independent review

Root composition `6c27c80` independently SOURCE ACK: admit hidden clean retained sessions, retain dirty/recovery barriers, and remove pre-picker parse cancellation. UI return `f8f8ff6` → `81db865` exact four new files SOURCE ACK; actual app return `048fe014` → `4eb2843` exact two new files SOURCE03 ACK. Original isolated branches/worktrees remain clean; claims released. Source01/02 prebuild fixture snapshots remain under `/tmp/cl-plain-deck-import-app-tests-01/evidence/`; they were not runtime failures.

Compiler repair `8234a42` explicitly narrows the already guarded saved head. Test repair `a3f4f62` targets the outer list by actual Long physical-row key and asserts the complete formatted saved message. Installed Compose1.12.1 bytecode independently established that the former descendant scroll targets its nearest nested scroll ancestor. Exact text AND visibility assertions remain. Final heading repair `cbff7ac` targets the loaded heading rather than aligning its multi-element item's top; exact name, ancestor and visibility remain asserted.

Independent campaign review verified gate4's complete source pins: the only changed existing Android file at `cbff7ac` is this one-line app-test repair; the only new Android files are the unbound selected-document writer and its tests. Existing producer/controller/input/UI/resources/build/dependencies are unchanged. Gate4's passed checks therefore remain separate bounded evidence. The next focused gate runs both actual import journeys plus the 15 new writer cases and both instrumentation Kotlin compile targets. It does not rerun or rebrand the historical 44-test failure.

## Adjacent selected-document output producer

Exact isolated return `616abf39d42ec0762843253f220828a8a32bccf8` → `2029b71`, exactly three new paths, unchanged frozen pins, original clean retained, claim released. Independent inventory reviewer SOURCE ACK. Complete internal writer uses real ContentResolver `wt`, immutable bounded bytes, one IO ownership scope, bounded chunks, flush and exactly-once close. Outcomes distinguish not-started from possibly-modified; provider open starts uncertainty even if it returns null/throws. Cancellation propagates after cleanup and consumers must retain bytes/require explicit new destination; no automatic retry/delete/rollback or remote-durability promise. It has no production consumer or export UI binding.

Fifteen authored cases include actual instance-owned provider/PFD truncation/exact bytes and same-path deterministic open/write/flush/close/cancellation faults. Runtime result is pending in attempt5; authored tests are not passing-test evidence.

## Limits

Host Robolectric actual registry/provider/configuration recreation/Room evidence does not establish native DocumentsUI grants, process kill, physical Fold7, TalkBack, 200% layout, performance or release readiness. No on-device tests or finished APK delivery. Full transfer/service acceptance, professional release and existing source-completeness/GameService holds remain. Publication remains blocked by the previously verified invalid GitHub token; no unchanged auth retry or arbitrary main merge is authorized.
