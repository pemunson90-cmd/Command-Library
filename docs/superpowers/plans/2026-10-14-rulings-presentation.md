# Two-stage ruling downloads and offline card rulings

> Execute inline; D-17 delegates routine design and D-28 batches verification. Root owns shell/app/catalog changes. Required independent source/consent/presentation integration review at the completed boundary.

Status: implemented and frozen-source review ACK;23focused checks pass44s/native counterpart+app compile. Broad gate pending. Evidence `docs/verification/k3-downloads/RULINGS-PRESENTATION-LOCAL-20261014.md`.

Goal: make the reviewed/bound ruling backend usable through real download controls and local card details, without hidden execution or a second acquisition owner.

Design/spec: K3 task, existing public ruling contracts, accepted source-admission and root-access plans. Keep existing English UI/approval/worker semantics. Source language is not asserted; ruling publication is a literal day, distinct from metadata update and retrieval instants. No live-feed/device/final-app evidence is inferred from synthetic controller/UI fixtures.

## Decisions

Extend the existing DownloadsController/DownloadsScreen, preserving its common real jobs/runner/revision/consent/pause/resume/cancel/observation and install queue. A complete optional ruling access+reader pair enables the additional controls. Root supplies both facades from one owner; controller rejects differing services. View-level source classification chooses English search versus ruling metadata(TEXT/NONE) or ruling file(JSONL/GZIP) from provider/scope/source representation; it grants no authority. Actual source admission/import remains the trusted root implementation.

“Check ruling download” plans fixed metadata and shows its own exact approval. Completion only exposes “Review rulings file”; it never installs metadata or starts/plans a full file automatically. That explicit action admits the completed metadata job and plans the exact dated file for a second visible approval with actual URL/size/date and language-not-asserted copy. A newly completed approved file installs through the verified ruling importer; manual install/retry remains available. Old ruling corpus remains readable on failed/cancelled verification. English auto-install remains unchanged.

Add a pure RulingsController and CardRulings panel in card details. Query the selected Oracle only, ten rows per page, replacing each page rather than accumulating unbounded text. Provide next page/reload-from-first, actual no-corpus versus installed-empty versus typed failure/stale states. Show complete literal comments, publisher label and literal publication day; display separate actual corpus source/update/retrieval metadata. No timezone coercion, English claim or freshness guarantee. Changing/leaving selection cancels reads and rejects stale replies; status/page generation mismatch fails visibly for reload. No request/import occurs through the reader. The no-corpus action navigates to existing Catalog/Downloads management rather than hiding a fetch in the reader.

## Files and execution

- [x] Add synthetic controller/shared UI ruling fixtures without source/TLS claims; preserve existing English fixture behavior.
- [x] Extend DownloadsController with ruling plan/file-plan/install/status dispatch and metadata no-auto-install. Wire the complete optional pair in ShellControllers.
- [x] Extend DownloadsScreen with the new stage controls, type-specific approval/copy and nullable ruling language handling; retain existing Back,48dp and adaptive behavior. All copy externalized.
- [x] Add RulingsController and CardRulings panel; wire through CardsScreen/CardDetail/ShellRoot with lifecycle cancellation and generation-bound page validation.
- [x] Batch focused shell controller/shared Compose tests and compile native counterparts after implementation. Checks: distinct two approvals, dismissed file plan never runs, metadata never installs, failed/stopped ruling install preserves/read-refreshes old corpus, common service mismatch rejects; absent/empty/corrupt/stale data, rapid Oracle switches/late responses, bounded next page, full literal comments/publisher/day, narrow large-font controls and nested Back. Existing English tests remain regression coverage.
- [ ] Independent completed-unit review, fix material findings, checkpoint exact tests/limits and continue. Larger/native checks only at the next justified integration boundary; no unfinished APK delivery.

Root wiring source6809f42 is already independently ACK with6actual root cases passed27s. Source1a4f43b whole1096/lint/allAPKs/ZIP proof remains the prior boundary, not evidence for this new UI.
