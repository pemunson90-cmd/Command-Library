# cc-research-10: R-10 LLM provider APIs

Path: `coordination/tasks/cc-research-10.md`. Written by the orchestrator. This card plus the files under Brief are everything the agent receives (GAUNTLET.md 5.7).

| Field | Value |
|---|---|
| Role | researcher |
| Component | research (Gate 1) |
| Orchestrator | claude-code |
| Model tier | standard (Sonnet, D-10) |
| Branch | none. Researchers write one file in the main checkout; the orchestrator commits it to `main` |
| Base commit | b1c922eaa96c25209691253c9849066b89a71846 |
| Steps | 1/1: write the research file |
| Status | filed by Codex; scope facts documented or explicitly UNVERIFIED |

## Question
For each cloud LLM provider and each OpenAI-compatible local server, what are the auth, streaming, model listing, tool calling, and pricing facts the AI deck lab needs?

## Feeds
GAUNTLET.md 2.H (AI deck lab providers, model listing, streaming, tool calling, cost estimates).

## Scope (must cover)
- Cloud providers: Anthropic, OpenAI, Google Gemini, Mistral, xAI, DeepSeek, Groq, OpenRouter. Add any other provider with a public API that a user could reasonably bring a key for, and say why.
- Per provider, from its official API reference: base URL; auth header; chat endpoint and request shape; streaming transport and event format; model listing endpoint (exists or not, response fields); tool or function calling support and format; whether an OpenAI-compatible endpoint exists and its gaps; whether pricing is exposed through the API (for example OpenRouter model pricing) or only on a web page; token counting endpoint; rate-limit headers; error format.
- Terms: any clause restricting use from a third-party client app with a user-supplied key. Quote it.
- Local OpenAI-compatible servers: Ollama, LM Studio, llama.cpp server, vLLM, KoboldCpp, LocalAI. Per server: default port, base path, model listing endpoint, streaming support, tool calling support, auth if any.
- A comparison table across all rows above.
- Recommend the minimum set of API dialects the app must implement to cover every provider (for example OpenAI Chat Completions compatible, plus native dialects where compatibility loses features), with the evidence.

## Brief (files to read, nothing else)
- `coordination/agents/researcher.md` (role brief, source of truth)
- this card
- `coordination/templates/RESEARCH.md`
- `docs/gauntlet/GAUNTLET.md`: sections 2, 3, 10 and Appendix A, for context only

## Working rules
- Write only `docs/gauntlet/research/R-10-llm-providers.md`. Create it early and extend it section by section, so partial work survives an interruption. Do not commit; the orchestrator commits it to `main`.
- Do not change branches, stage, commit, or run any git command that changes state.
- Bash is for measurement only (`curl -sI` for sizes, `curl ... | gzip -dc | wc -l` for counts, `jq` on small JSON). Stream through pipes. Never save bulk files or any other file to disk.
- Network etiquette: send a descriptive `User-Agent` (for example `CommandLibraryGauntlet/0.1 (research)`) and `Accept` header to Scryfall, stay well under its rate limit, and download bulk data only from the bulk file hosts.
- Every claim carries its source URL and access date (today, from `date -u +%F`). Anything not confirmed by a source is written UNVERIFIED with what was tried. Never infer an endpoint, field, format, price, or capability.
- Terms and licensing get their own findings, quoting the relevant clauses.
- No em dashes anywhere in the file or the return message.

## Acceptance
- RESEARCH.md template sections present: question, findings, options, recommendation, risks, confidence.
- Every Scope item is answered or marked UNVERIFIED with what was tried.
- Every finding has a source URL and access date.
- Zero em dashes.

## Output
- `docs/gauntlet/research/R-10-llm-providers.md`
- Handoff: none. A researcher writes only its research file (role brief). The orchestrator tracks status in this card and commits the file. A crash before commit loses only this card's run, which restarts from scratch.
