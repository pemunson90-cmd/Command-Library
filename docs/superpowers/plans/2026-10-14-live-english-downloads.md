# Live English catalog download and install

Status: independent public/security/composition design APPROVE; batch1 source approved and six focused real-host cases pass; controller/UI batch2 next. Parent: orch-kotlin-k3-offline-data-01. Root owns app/shell/shared files. Pat delegates implementation and D-28 batches verification at meaningful risk boundaries.

## Result and scope

Provide working English-card download/install controls over the existing AndroidCatalogServices, without automatic network activity or replacing readable installed data on failure. Keep complete English traversal provenance honest: it is not an immutable upstream snapshot. Other required feeds, Full/Lean retention, rulings, rules and complete K3/v1 remain open. GameService remains unbound.

## Composition boundary

- Add an optional, complete English-catalog access capability at the tail of ShellPorts. It opens an immutable session containing the real DownloadService/DownloadRunner, trusted English request factory, and a verified-job install operation. Shell depends only on existing core-model types; no implementation-package imports or nullable sub-ports.
- Open lazily on entry to Downloads, not app startup. Root AppGraph binds this capability over its existing database/settings/ConsentCoordinator. Cache only successful initialization under one mutex; cancellation/failure cleans up newly owned resources and can retry. Existing offline destinations remain usable if download initialization fails.
- Instantiate AndroidCatalogServices with the actual OkHttpNetworkClient, physical WireDispatchGuard forwarding the factory authorizer, acquisition429 metadata enabled and default production HTTPS/TLS behavior. Reuse the accepted network module/version; add the permitted app bootstrap dependency and INTERNET permission. Existing catalog manifest already supplies ACCESS_NETWORK_STATE/private artifact provider.
- Installation resolves catalogSource(jobId), then delegates to the real bulk importer. VERIFIED_ARTIFACT imports obtain actual bytes from the factory's pinned binding (LocalBulkCatalog ignores caller chunks in this mode); pass an empty caller flow deliberately, never fabricate artifact bytes/URI/authority or accept arbitrary source input from UI. Preserve typed failure/unknown/unavailable and cancellation.

## User flow and lifecycle

- First open initializes access, locally recovers interrupted work, then lists bounded real job pages. No approval or transfer occurs merely by opening a screen or recovering. Truthful loading/error/retry/empty states.
- Create a trusted English plan and show provider, actual URL, language, finite transfer budget and unknown expected size honestly. Only explicit confirmation calls settings.approve for the exact intent, then revision-checked start and the structured runner. Wi-Fi choice remains enforced by the existing engine, not UI alone.
- Keep per-job coroutine ownership and observation in the shell ViewModel scope. Support real status/progress, pause, explicit cancel, retry/resume with fresh consent as needed, and verified install. Navigation does not itself cancel work; no unsupported background-OS execution promise. Process restart uses persisted recovery; completed artifacts require explicit install unless completing the currently approved download/install flow.
- Preserve original row revisions on controls, reject double submission, retain actionable errors/drafts, and avoid stale list/observer results replacing newer revisions. Pause/cancel must remain usable during a running transfer. Installed catalog remains readable during download/validation/install failure; successful atomic install refreshes actual catalog status/search generation.
- Scrolling labelled 48dp controls, externalized English strings, keyboard/back/large-font/adaptive behavior. Show only functioning supported source actions.

## Implementation batches and evidence

1. [x] Review the public/lazy-root/security composition. Implement access/session types, real app binding and verified install delegation together with focused concurrency/cancellation/failure/no-wire-before-consent checks.
2. [ ] Implement scoped download controller and screen/navigation together with meaningful consent/CAS/pause/resume/cancel/recovery/atomic-install/error/large-font cases. Reuse real existing acquisition tests; do not duplicate them per button.
3. [ ] At this integration/security boundary, run one combined local check and affected packaging tests, actual root composition/pinned-artifact probe where host supports it, and final independent review. Device-only behavior gets one justified combined modern native run when available, never a fabricated pass. No genuine full-corpus/device/Fold7/release claim without actual evidence; no unfinished APK delivery.


Review requirements applied: dedicated lazy mutex; recover exactly once before publishing a successful session, not on every screen entry; clean only newly owned transport on initialization failure/cancellation. Exact persisted-intent consent, authoritative revisions and independently runnable pause/cancel, same-session pinned install and acknowledged catalog refresh remain required in the controller/UI batch. Owner close disables only this access transport and never shared storage/settings.


Batch1 evidence: docs/verification/k3-downloads/LIVE-ENGLISH-ACCESS-LOCAL-20261014.md. Scoped host directory metadata/descriptor/sync/close bridge is distinct from Android runtime/regular-artifact verification. Native wrapper remains unexecuted; source/UI/full-corpus/physical acceptance is not inferred. Downloads opens within Catalog rather than adding a sixth crowded root navigation item; this preserves existing working root routes.
