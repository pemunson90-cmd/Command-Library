# Search edits supersede submitted work

Authority: SPEC A08 requires query cancellation/typing to supersede stale results. Current SearchController.onQueryChange only clears an error; submitted work can continue while the input has changed. Existing query-string guard also cannot distinguish two submissions of the same query.

Bounded local change: on an actual edit, cancel pending work and clear submitted search/result/detail/cursor/errors, retaining active-catalog status and the exact draft input. Each request/reset/edit advances a controller-owned ticket. First/cursor page completion and stale-cursor notification must prove that ticket current before publication; cancellation alone is not the publication authority. Editing does not automatically submit or download. Retry/search-again behavior after a submitted search remains intact.

Verify with focused controller cases: editing during a suspended request emits no stale results and requires explicit submit; editing after results clears selection/cursor without a new lookup; same-query resubmission remains protected. Keep catalog generation/cursor stale checks and existing controller regression cases. Native UI compile only at next meaningful batch, no extra emulator per edit. No contracts/schema/consent/source changes. Begin only after the current private-artifact/keyword boundary finishes, preserving its exact compiled inputs.

This fixes one A08 lifecycle gap; full indexed Oracle-text/local syntax/autocomplete and full-corpus performance remain separately incomplete.
