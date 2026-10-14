# Search draft supersession

An actual query edit now cancels submitted work, invalidates its request ticket and clears submitted results/detail/cursor/problems while retaining the exact draft and active-catalog status. Editing performs no lookup/download. Explicit invalid submissions also cancel prior work. First/cursor page publication and stale-cursor notification require the current ticket, including repeated identical query submissions. Existing snapshot/cursor/identity handling remains intact.

Focused SearchControllerTest12/one XML passed14s, zero failures/errors/skips. Two new cases cover typing during a suspended lookup (old token cancelled, no late result, no automatic lookup, explicit submit works) and editing completed paginated results (detail/cursor cleared, exact draft preserved). Existing controller regressions pass. Log /tmp/search-edit-supersession.log. No repeated whole suite/device run; the preceding1113 whole integration gate predates this small lifecycle delta. Full indexed search/local syntax/autocomplete remain incomplete.

Plan docs/superpowers/plans/2026-10-14-search-edit-supersession.md. Local publication remains blocked by GitHub rejecting configured credentials.
