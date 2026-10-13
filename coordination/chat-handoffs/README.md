# Regular-chat research handoffs

User policy (2026-10-11): use the weakest capable models and defer work to ordinary ChatGPT/Claude chat when cloud tools add no value. Pure API/terms research, source-format surveys and design alternatives belong here. Cloud retains repository integration, CI, authentic byte/probe verification, code, tests and build-dependent checks.

Do not duplicate completed source observations. Existing reports are inputs; refresh only an unanswered item or changed source. Current K1 helpers were already running narrow build-readiness checks when this policy arrived; capture their output once. Avoid broad cloud research agents afterward.

Paste one packet into regular chat with browsing if needed. Ask for its exact answer format and sources. Return the answer as a public Markdown attachment/file; executor checks material claims needed by implementation and records acceptance. Ordinary chat never claims CI/tests/byte measurements without evidence. No credentials, signing sheets or paid accounts needed.

Deferred packet: [data/provider/terms research](app-source-contracts.md). Still required before its consumers ship, not prerequisite for K1 capability readiness. Windows/sync design stays deferred until those milestones.

## Research routing — regular chat first

Pat's explicit policy (2026-10-13): when missing research is needed, Codex posts a short, self-contained, copy/paste research packet directly in Codex chat for Pat to take to regular ChatGPT/Claude chat, which is cheaper than cloud Codex research. Also save the packet under `coordination/chat-handoffs/` so it survives an environment change. Do not merely point Pat to a file; include the usable request in chat.

Each packet states the exact question and decision it will unblock, relevant requirements/library versions and small source excerpts, existing findings to avoid repeating, authoritative sources to check (official guidance and relevant licensed open-source code), and the requested answer format: recommendation, citations, compatibility limitations and unresolved facts. Never include secrets, private corpus payloads or signing material. Keep the scope bounded; request multiple independent questions together when that reduces back-and-forth.

Default standalone web/API/terms/source-format/design research to this route, rather than spending cloud Codex calls or spawning research agents. Read-only Claude research assignments can also be taken to regular chat; Claude Code is not required for browsing-only work. Continue independent implementation/checks while Pat obtains answers; pause only the work that depends on the missing evidence. Reuse existing research first, so Pat is not sent redundant requests. On return, Codex checks material claims against the exact repository/API versions and records acceptance before implementation; a chat recommendation is not runtime verification. Cloud tools remain appropriate for repository inspection, integration, code/tests/builds, authentic data measurements and security/durability review that require the actual code. Use the smallest capable model for those bounded tasks and retain required stronger final reviews.
