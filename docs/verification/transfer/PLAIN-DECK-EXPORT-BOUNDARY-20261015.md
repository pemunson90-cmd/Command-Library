# Plain-list export producer and actual codec/Room compatibility

Status: bounded producer SOURCE/RUNTIME ACCEPTED. Campaign reviewer SOURCE02 ACK covers controller source; inventory reviewer independently verified actual execution, scope and all655 committed/live pins. No export UI/picker acceptance.

Exact tested source `b545f411ec691e12d354f2a39eddcd401bc3c84b`, Android tree `1d9e7a1ccbbf2b8ae790aebbcdc71dc7f8147460`. Local focused gate executed **30 tests / 2 XML / 0 failures / 0 errors / 0 skips** in1m18. All655 tracked Android source hashes stayed unchanged. Log SHA256 `795d346306a89014bae52848e8ff6eb666e10d9a54de52bf655b9dfac322813c`.

Evidence: `/tmp/cl-plain-deck-export-controller-root-01/evidence/producer-compatibility-attempt1/{source.json,summary.json,gradle.log,test-results}`. Runner `/tmp/cl-plain-deck-export-controller-root-01/run-producer-compatibility-gate.sh` records pinned environment and invokes shell `*PlainDeckExportControllerTest`, app `*PlainDeckExportBoundaryIntegrationTest`, and both shell/app instrumentation Kotlin compilation targets. No native tests executed, no emulator/APK delivery.

## Implementation and source ownership

Exact isolated return `9086017f511471f4db6276365e54f37f5e32057b` on accepted70989c0 → root `436be32`, exactly four new controller/state/test/handoff paths; parent/live blobs/pins/clean original verified. Canonical claim released. Campaign reviewer independently SOURCE02 ACKed controller `7d9c560f11c357c367d294889960107b083894cef7fa7a69de4c8e17feb2d5ba`, state `e649f32832551154ecd3c59c11781f8c09822c8c8c13e21807a9388a7b83d02f`, tests `fba50a3d69e0bdf753d89fd223ac6e36ba392d38fe32aa13c2561f9e6f7c3b0a` and handoff `d9acc7765864ee0cf47e3f3b9fbbbbaf3bdfc89a1208ab157732e19ad24a293c`.

Root retained all shared files. `1f97aff` appends optional internal real-writer seams to ShellViewModel/ShellControllers, actual factory resolver/IO binding and fresh private per-controller UUID nonce; independent composition SOURCE ACK. It compiles in this gate. The actual ViewModel/factory/controller-to-real-writer path has not been executed by these tests; that belongs to the next UI/app gate.

## Executed scope

**29 controller cases** use faithful library/codec/validator/writer ports and owned coroutine test scopes. They execute qualified saved-ID/revision checks; validation/render failures; complete immutable preview bytes/losses and attribution/bounds; exact preview reference authority; superseded preparation; opaque per-instance picker tokens and stale/duplicate/null callbacks; permanently revoked hidden-picker authority; read-only frozen-source semantics after approval; honest typed output outcomes; direct native cancellation; and non-evicting destination history.

The hard ownership cases gate a noncooperative writer body and its separate cleanup: cancellation makes the Job inactive while the controller stays CANCELLING/owned, with replacement/discard/successor picker/write blocked until actual unwinding. These are faithful-port closure oracles, not native-provider interruption evidence. URI reuse A→B→A, even after NotStarted, is refused;32-attempt and16,384-UTF16-unit bounds fail before additional writer calls. Exact URI comparison cannot detect provider aliases or undo documents created by the OS picker.

**One actual AppGraph/Room compatibility case** validates and stores an independent eight-entry source across all seven boards, renders through the real bound codec, compares an independently specified229-byte UTF8 golden and all81 ordered loss pairs (13 global+68 entry), parses the exact golden back to fixed board/name/quantity tuples totaling18copies, and checks full source/inventory/settings/catalog equality. A separate Room connection with throwing clock/ID sources reads the unchanged original saved snapshot.

The shared app-test-only `PlainDeckExportBoundaryFixture` contains literal independent source/bytes/losses for future UI tests; it is not a production adapter or a dynamically generated expectation. Oracle artifact lineage `/tmp/cl-plain-deck-export-app-oracle-01/manifest.json`, SHA5638e41317a8e288d95ecda6422f7d1c3b6bf7eab447e9d522be9df66d0613c6, readbase70989c0. Golden SHA8936a20754902ebcb73f0ca4628a67ba2b1bdbb86da7177ff75e26e72d32e022. Source fixture requires no package/profile/catalog graph invention. Independent inventory source review verified the literal transcription, actual graph/model constructors, byte and loss oracles.

## Prebuild finding and repair

SOURCE01 admitted a zero-byte successful render, which could authorize truncating a destination to an invalid empty artifact. Source review held it before any compile/test run. SOURCE02 restricts bytes to1..MAX_BYTES, adds zero-byte rejection/no-preview/no-picker/no-write coverage, and makes the oversized-loss fixture independently nonempty. Empty saved decks remain valid because the real codec emits its format marker. Original snapshots/manifests and focused repair diff remain under `/tmp/cl-plain-deck-export-controller-01/evidence/`; no intentional API-absence/red run or historical runtime failure is claimed. This gate is the first actual producer run.

## Acceptance limits and next dependency

Complete bounded internal producer and application-owned all-board text compatibility only. Existing selected-document writer's15 real-provider/fault cases were accepted separately atcbff7ac; they were not rerun here and are not additional cases in30. No actual export dialog, CreateDocument workflow, controller-to-real-writer execution, native permission handling/process kill, remote durability, full B08/I01, common/vendor target syntax, board selection, CSV/native backup, Fold7/TalkBack/200%/performance or finished APK acceptance.

Next consumers must inherit this exact tested ABI/pins: source revision and complete loss preview before explicit destination acknowledgement; root one saved opaque picker ticket; no auto-IO on composition/restore; frozen bytes without reload/render upon URI; hidden ticket revocation; exact ownership until actual cleanup; typed partial-output recovery via an explicit different destination. Root keeps shared composition/navigation/integration/final merge authority.
