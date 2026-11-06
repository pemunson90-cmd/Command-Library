# Resume-state compaction proposal

## Latest continuation delta

Source: `agent/cx-thread-recovery-20261013` at
`acdb085eb05d6a88e81e526c6b7c9f5a84c6b4a7` (read back 2026-11-06).
Main `b7e4e6fcd13386a46014c6888904a5a88afed2e1` is not the live product pointer.
Root `marlow-d31-20261030` still holds the recorded active lease. Expiry is
not release. This isolated proposal does not change live state, lease, locks,
mirrors, product code, or ownership and does not accept any product gate.

The staged `proposal/coordination/state.json` reduces required resume input
from 53,770 to 2,581 bytes (95.2%). The pending ordinary artwork candidate is
retained verbatim; all 151 verification entries and every original state byte
are preserved in the SHA-256-addressed snapshot under `proposal/coordination/history/`.
Every other live field is unchanged. The historical snapshot is evidence, not
current instructions. Its hash and entry count are linked from the candidate.
Existing schema version and verification-object shape remain compatible.

## Owner integration

1. Re-read current branch/head, live state/task, ownership and pending gate.
   Do not integrate this frozen state over newer work. Confirm which verification
   keys are still current; retain more keys with repeated `--keep` options as needed.
2. Generate a fresh proposal outside the source checkout:
   `python3 scripts/compact_resume.py --output /path/to/new-proposal --keep ordinary_artwork_candidate`
   The helper refuses an existing output or output inside the checkout. It never
   writes the live repository. The staged example is reviewable without running it.
3. As authorized orchestrator, copy the generated history snapshot to its exact
   repository-relative location before replacing the live state with the candidate.
   Verify the archived bytes/hash and reconcile any state changes before applying.
   Retain all existing history snapshots. A later compaction preserves any previous
   archive pointer inside its full snapshot, forming a lossless history chain.
4. Record the actual continuation delta, use `scripts/checkpoint.py` for the real
   checkpoint, then run `python3 scripts/check_continuity.py` and
   `python3 -m unittest discover -s scripts -p 'test_*.py'` before publishing.

## Verification

Local continuity checker passed. All 36 script tests passed, including byte-exact
history preservation, unchanged live source, explicit key selection, unknown-key
rejection, existing-output protection, source-checkout protection, staged candidate
continuity validation and a subsequent checkpoint round trip. Negative visual
capture fixtures intentionally print missing synthetic captures; the suite passed.
Android/runtime/device tests were not run: this is additive coordination tooling
and a staged data proposal, with no product changes or product acceptance claim.

No deletion, cleanup, takeover, merge, deployment or workflow dispatch performed.
