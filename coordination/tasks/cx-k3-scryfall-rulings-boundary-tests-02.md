# Scryfall metadata rejection-boundary tests

Status: active. Owner: codex-scryfall-boundary-tests-20261014.
Branch: `component/scryfall-rulings-metadata-20261013`; base `e45d573c19196eb8e060318c370f68675dd39272`.

User assigned a small disjoint task while two Codex sessions run. Ownership is limited to the test, component handoff, this card and its dedicated lock. The lock is also visible in the main local checkout for the other session. No global lease takeover or global-pointer updates.

Scope: fill meaningful rejection-boundary coverage for the already reviewed metadata decoder, without changing production behavior or source policy. Check UTF-8 limits, nested JSON, exact configurable byte counts, malformed field shapes and Gregorian/offset timestamp handling. Use synthetic fixtures only.

Verification: one focused `:feature:catalog:testDebugUnitTest --tests '*ScryfallRulingsMetadataCodecTest'` run; no emulator, live request, broad build or CI dispatch. Update component handoff, publish completion, and release the dedicated lock. Stop if a production defect requires broadening ownership.

## Latest continuation delta

Claimed at 2026-10-14T00:24:47Z before test edits. Existing seven decoder tests inspected; UTF-8 byte boundaries, configurable ceilings and pre-epoch/Gregorian timestamp cases lack direct coverage. Verification: not_run for this task. Next: add bounded fixtures and run the focused test class.
