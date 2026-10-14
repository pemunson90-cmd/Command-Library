# New-game creation input contract — 2026-10-13

Independent Astra approved design and exact source: GameCreationContracts.kt SHA256 `b0aa0622ef0179c699f7cf0762b0bff6ef609dddcfcbb38fe9bc3329b062aba0`; tests `83b387e2b5e1ccf50795f15677d7fce01fe99e80e6b42d6510c255fd071764ab`. No blocking finding; reviewer builds/tests not_run.

The new immutable input requires1–6 unique active seats with contiguous order, globally unique commander-slot identities, explicit unique LIFE/commander role bindings and matching initial life. Paused initial clocks must have the correct NONE/SHARED/per-seat representation and untouched finite balances. Unresolved art/decks, negative custom starting life, multiple commanders, custom OTHER counters and list order independent of seat order remain valid. Existing legacy constructors are unchanged.

Root API-absence compile RED16s; declaration-only behavior RED12cases/8failed invalid-input assertions17s; implemented constructor checks and GREEN `:core:model:test :feature:life:test`19s,44model+81life cases, zero failure/error/skip. This adds no consumer or changed existing signature; the preceding whole-app state-unit check is separate evidence, not a claimed rebuild of this source.

The constructor certifies only its stated input invariants. It does not certify variants, import/resume, persistence, idempotence, generated IDs or completed UI. Those remain actual service/schema/codec gates in the larger design. Next: profile/history/ports contract unit, then action/provenance contracts before codec and transactional service implementation.
