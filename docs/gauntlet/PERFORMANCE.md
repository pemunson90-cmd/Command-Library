# PERFORMANCE: Gate 2 proposed budgets

These numbers are acceptance targets for Pat's Gate 2 ruling, not measurements. Use release/profile builds after architecture and implementation. CI is a trend/smoke environment; physical target evidence is separate.

## Reference targets and method

Android reference class: ARM64, Android14+, 6GB physical RAM, Snapdragon778G-class CPU/storage, 60Hz display; a real device matching this class or slower must be identified in the result. Windows reference: Windows11 x64, 4-core Intel i5-8250U-class,8GB RAM, SSD,60Hz. ARM64 Windows is optional future support until separately packaged/tested, not implied by x64.

Record actual model/OS/CPU/RAM/free disk, thermal/battery/power state, package build SHA, Flutter/engine/SQLite versions/config, corpus checksum/record counts/languages and enabled packs/model. No fabricated machine equivalence. For latency measure at least30 samples, report median/p95/max; startup20 fresh-process samples. Disclose warm versus cold cache and instrumentation. Keep raw CSV/trace in CI artifacts and summarized evidence in milestone report.

Corpus: verified latest full English Oracle/printing/ruling data plus rules and30,000+ searchable canonical entries; duplicate deterministic generated user rows only for10,000-deck/100,000-inventory stress and label synthetic. Never multiply source records and call it real coverage. Document actual larger all-language optional corpus separately. Query suite50 pinned queries incl simple name, Oracle phrase, combined color/mv/legality, owned/in-deck and numeric-price filtering, broad result and rules search; stable fixture expected IDs.

| Budget | Target on reference hardware | Workload and acceptance |
|---|---|---|
| P01 cold start | p95<=2.5s to useful interactive existing local home; cached shell<=1s on resume | Existing complete corpus, no network/model warmup dependency; first-run download separately measured |
| P02 search | p95<=150ms DB execution,<=250ms keystroke-to-visible after150ms debounce |50-query full English corpus suite; count/pagination included, no network fallback hidden |
| P03 suggestions | p95<=100ms after debounce,<=20 suggestions | Prefix/Unicode/group syntax; old request canceled, stable correct result |
| P04 scrolling | p95 frame<=16.7ms at60Hz; fewer than1% frames>33.3ms |10s result list/grid over30,000+ records; cache warm and art-load run separated; virtualized offscreen rows |
| P05 full import |<=10min parse/index/validate activation, excluding download | Verified selected English corpus already local, bounded staging; record bytes/rows/sec and actual counts; no full-file RAM |
| P06 memory |<=300MiB steady RSS,<=450MiB peak during catalog import, excluding loaded LLM only | List/detail/deck/life scenarios. Record total process RSS including decoded art and native+managed peaks, file cache separately. On-device model overhead not disguised as base app memory |
| P07 recommendations | p95<=1s initial top20 for100-card Commander deck | Local constrained candidates and known budget/legality/bracket evidence; full recompute<=3s with cancel/progress |
| P08 durability | accepted committed life event durable<=100ms p95; text autosave<=500ms debounce plus<=200ms transaction p95 | Power/process interruption fixtures distinguish uncommitted draft from committed state; no success before persistence |
| P09 transfer |100,000 inventory rows<=60s;1MiB text parse<=1s; backup restore metadata phase<=60s for100MiB user archive | Streaming, bounded batches; larger archives cancellable. Catalog/image/model payloads measured separately |
| P10 local LLM | control cancel<=1s p95; model-load<=30s and first token<=10s target for chosen small reference model,>=3 output tokens/s target | Record model hash/quantization/context, threads/offload/device. Unknown/unachievable models shown with measured limits; inference may not consume >60% device RAM by planned load estimate without explicit user warning/approval |
| P11 network/UI | cancel UI acknowledges<=100ms, background request stopped<=1s; no main-thread work>50ms | Slow/dropped connections and tool-call cancel; remote TTFT not an app-controlled promise |
| P12 disk/resource | image cache default256MiB; user-adjustable64MiB-2GiB. No optional pack assumed in base app. Staging requires measured projected active+staging bytes plus20% safety | Before download show source compressed bytes and estimated disk range; unavailable estimate clearly unknown. Initial base-corpus disk target<=1.5GiB, provisional pending measured English coverage; don't discard required data to meet it |

## Streaming limits and exceptions

JSONL decoder maximum record8MiB, fail corrupt/oversized record with source context before activation; configurable validated source profile may raise it if legitimate record evidence requires. Compressed/decompressed byte/ratio checks must handle real bulk files; never use a tiny universal decompression cap that blocks full catalog. JSON-array reader streams tokens only for actual array sources. DB batch default1000 records or2MiB decoded payload, whichever first, with<=250ms batch target; tune using measurements. Bound queues and dispose payload after commit.

Restore archive default maximum2GiB user data, per-entry256MiB and manifest-declared/uncompressed limit; user preview can authorize larger legitimate archive with checked free disk, never an unbounded extractor. Reject path traversal and excessive compression. Games keep long event histories paginated, never all in a UI widget.

If a target fails, report failing samples, cause and proposed tuning/budget change at the milestone. No silent widening, dropping corpus/features or replacing actual device results with CI estimates. Physical device/sensor/reader access from phone+GitHub may require a documented manual check; list exact unmet evidence in IMPOSSIBLE only after confirming automation cannot perform it.

## Decoded artwork memory contract

Decoded-image working set target<=64MiB within P06 total RSS; transient decode/upload peaks<=96MiB and must still respect overall450MiB import/base peak. Decode to required visible pixel size, evict offscreen decoded images and cap concurrent decode work. Offline-file pack pinning does not pin decoded pixels in RAM. Profile art-heavy six-seat life and grid/detail transitions with actual loaded images, report total RSS and decoded cache peak; separately report LLM-inclusive RSS when loaded. GPU texture allocation is separately traced where platform exposes it, never used to hide process/art memory. These are proposed targets, not measured Flutter cache behavior.
