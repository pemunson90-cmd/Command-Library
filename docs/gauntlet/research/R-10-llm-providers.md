# R-10 LLM provider APIs

Research and access date: 2026-10-11. Author: Codex orchestrator. Status: filed with explicit verification limits.

## Question

Which auth, streaming, discovery, tool, cost and terms contracts cover eight cloud providers and six local servers?

## Findings

### Evidence boundary

Official documentation was retrieved through browser tools or indexed official pages. No credentials, charged API calls or running local-server probes were used. Shell network access failed at the configured proxy. UNVERIFIED means the cited source/probe did not establish the fact, never that the feature does not exist. All source URLs in the register were accessed 2026-10-11; IDs attach that date and source to table findings. Documentation is not runtime validation.

### Cloud comparison

Paths are relative to the base. CC means model/messages plus optional stream and JSON Schema function tools in the Chat Completions dialect. A compatible endpoint does not promise every OpenAI feature.

| Provider | Base URL; auth | Generation request | Streaming | Models and fields | Tools/compatibility | Evidence |
|---|---|---|---|---|---|---|
| Anthropic | https://api.anthropic.com/v1; x-api-key and anthropic-version | POST /messages: model/messages/max_tokens, separate system field | SSE message_start, content_block_start/delta/stop, message_delta, message_stop; ping/errors | GET /models: id/display_name/created_at/capabilities/token limits, cursor pagination | Native tools name/input_schema and tool use/result content blocks. Current OpenAI-compatible route UNVERIFIED from A1 | A1-A3 |
| OpenAI | https://api.openai.com/v1; Bearer | POST /responses: model/input; CC separate | Typed Responses events; complete current event inventory UNVERIFIED from O1 excerpt | Models listing present in reference; exact path/envelope/account visibility UNVERIFIED from O1 retrieval | Responses function tools and tool-result items differ from nested CC functions | O1-O2 |
| Gemini | https://generativelanguage.googleapis.com/v1beta; x-goog-api-key | Quickstart now POST /interactions: model/input. Also /models/{model}:generateContent with contents | Interactions stream true plus ?alt=sse, typed step deltas; GenerateContent has streamGenerateContent. Complete framing UNVERIFIED | Models API exists; exact field/pagination inventory UNVERIFIED from G3 excerpt | Native function tools; exact tool result mapping and current OpenAI-compatible route/gaps UNVERIFIED from G1-G3 | G1-G4 |
| Mistral | https://api.mistral.ai/v1; Bearer | POST /chat/completions: CC | Data-only SSE, terminal [DONE] | GET /models: id/capabilities/context limits/aliases. Exact live envelope UNVERIFIED; docs examples need contract check | CC tools/tool_choice, native server-tool extensions. Universal optional-parameter parity UNVERIFIED | M1-M2 |
| xAI | https://api.x.ai/v1; Bearer | POST /chat/completions: CC; Responses also documented | CC choices/delta; complete Responses events UNVERIFIED | GET /models: data id/created/owned_by. GET /language-models richer metadata/pricing | Function tools; model-specific restrictions and full OpenAI parity UNVERIFIED | X1-X4 |
| DeepSeek | https://api.deepseek.com; Bearer | POST /chat/completions: CC; Responses also documented | CC SSE choices deltas and [DONE]; Responses failure events documented | GET /models indexed official reference: id/context/output limits/modalities/efforts/protocol capabilities. Full envelope UNVERIFIED, opening failed | CC tools; exact thinking/tool combinations and Responses parity UNVERIFIED | D1-D4 |
| Groq | https://api.groq.com/openai/v1; Bearer | POST /chat/completions: CC; reference also exposes Responses | CC streaming; exact terminal/error events UNVERIFIED from retrieved excerpts | Models methods documented; full listing envelope/capability fields UNVERIFIED from Q1 | Tools/functions supported; unsupported parameters need provider overrides, not assumed equivalence | Q1-Q3 |
| OpenRouter | https://openrouter.ai/api/v1; Bearer | POST /chat/completions: CC plus router options | Streaming; exact heartbeat/provider-error/terminal contracts UNVERIFIED from R2 excerpt | GET /models: data id/name/context_length/architecture/pricing/supported_parameters; pagination metadata | Functions depend on model/route; supported_parameters assists discovery; router options are extensions | R1-R2 |

### Prices, counting, rate limits and errors

Unknown cost is not zero. Estimates need model/version, source timestamp, currency/unit and cache assumptions. Published pricing is not evidence of a price API. No rates were copied into a permanent model list.

| Provider | Pricing exposure | Token counting | Rate headers | Errors and remaining gaps | Sources |
|---|---|---|---|---|---|
| Anthropic | Price API UNVERIFIED from models reference | POST /messages/count_tokens documented | Exact names/units UNVERIFIED from A1-A4 | Exact HTTP envelope/streamed error types UNVERIFIED from retrieved portions | A1-A4 |
| OpenAI | Usage tokens documented; catalog-price API UNVERIFIED | POST /responses/input_tokens returns response.input_tokens/input_tokens | Exact set UNVERIFIED from O1/O2 | Complete error envelope and partial-stream failures UNVERIFIED from excerpts | O1-O2 |
| Gemini | Price API UNVERIFIED from G3 | models/{model}:countTokens documented for GenerateContent; Interactions parity UNVERIFIED | Exact names/units UNVERIFIED | Generation and Interactions error shapes UNVERIFIED; require separate codecs | G1-G4 |
| Mistral | Price fields/API UNVERIFIED from model cards | Dedicated count endpoint UNVERIFIED from M1/M2 | Exact names/units UNVERIFIED | Exact envelope UNVERIFIED from retrieved reference sections | M1-M2 |
| xAI | Language-model API prices prompt/completion/cache tokens in USD cents per 100 million tokens | Dedicated count endpoint UNVERIFIED from X1-X4 | Exact names/units UNVERIFIED | usage.cost_in_usd_ticks documented; exact tick conversion UNVERIFIED from X4 excerpt | X1-X4 |
| DeepSeek | Official price page; machine-readable model price fields UNVERIFIED | Token-usage guidance supports estimation; dedicated HTTP endpoint UNVERIFIED | Exact names/units UNVERIFIED | Cache accounting documented; full HTTP/stream error schema UNVERIFIED | D1-D5 |
| Groq | Price API UNVERIFIED from Q1 | Dedicated count endpoint UNVERIFIED | x-ratelimit-limit/remaining/reset-requests refer to daily requests; token counterparts to per-minute tokens; retry-after seconds on 429 | 429 documented; other envelopes UNVERIFIED from Q1-Q3 | Q1-Q3 |
| OpenRouter | API string pricing prompt/completion plus other dimensions. Response-field units UNVERIFIED from R1 excerpt; filter dollars/million is not proof of those units | Dedicated count endpoint UNVERIFIED | Exact names/units UNVERIFIED | Models API error code/message documented; generation/routed/stream variants UNVERIFIED | R1-R2 |

For every UNVERIFIED cell above, the attempted probe was browser retrieval/search of the listed official reference, without a credentialed request. Resolve precise schemas/units at implementation with versioned fixtures before advertising verified integration.

### Terms and BYOK

Proposed client connects directly with the user's own key, stored locally under R-12. No shared app key resale or intermediary credential broker. These short excerpts establish constraints, not blanket approval for every account or use. API agreements must be distinguished from consumer-chat terms.

| Provider | Clause or failed verification | Client implication | Evidence |
|---|---|---|---|
| OpenAI | Agreement permits “integrate the Services into Customer Applications” and prohibits “buy, sell, or transfer API keys” | Integration contemplated; no resale/transfer. Exact account applicability still needs verification | T1 |
| Anthropic | “Services under these Terms are not for consumer use” | API/end-user products contemplated, but personal consumer access is not API eligibility. Distributed BYOK blanket authorization UNVERIFIED | T2 |
| Gemini | “professional or business purposes, not for consumer use” | Personal-use eligibility unresolved. Unpaid-service data treatment and regions also matter; do not substitute consumer terms | T3 |
| Mistral | Applicable API BYOK clause UNVERIFIED: terms route inaccessible; search returned consumer agreement | Consumer terms do not prove API permission | T4 |
| xAI | Consumer page directs APIs to separate enterprise agreement; API BYOK clause UNVERIFIED from this retrieval | Do not apply consumer key clauses to APIs | T5 |
| DeepSeek | API third-party-key clause UNVERIFIED: attempted docs terms route failed; platform page is not an agreement | Verify applicable service agreement before public support claim | T6 |
| Groq | API BYOK clause UNVERIFIED: general terms opened without establishing cloud-client key provision | Website terms alone do not establish permission | T7 |
| OpenRouter | “confidentiality and security of all API keys”; account owner responsible for charges | Secure storage; downstream restrictions UNVERIFIED. OpenRouter key differs from broker upstream BYOK | T8 |

### Local comparison

Hosts/ports are configurable. Tools depend on model/template/parser, not merely a server accepting a tools field. No installed binary was probed.

| Server | Port and base evidence | Models | Stream | Tools | Auth | Evidence |
|---|---|---|---|---|---|---|
| Ollama | Docs localhost:11434/v1; current binary default independently UNVERIFIED | /v1/models; created means modification time, owned_by username/library | CC and Responses streaming | Function tools; Responses non-stateful only, despite contradictory stateful checkbox in docs | Local sample key ignored; cloud auth differs | L1 |
| LM Studio | Docs example localhost:1234/v1; current installed default UNVERIFIED | GET /v1/models | CC/Responses documented; exact events UNVERIFIED | Function tools documented; model suitability conditional | Optional API Tokens and Require Authentication setting | L2-L4 |
| llama.cpp | Current master default 9931 and /v1; Docker explicitly overrides to 8080, which is not current default | Exact list path/envelope UNVERIFIED from retrieved README portions | Chat/Responses routes documented; exact SSE fragments UNVERIFIED | Model/template-specific; exact flags/parser matrix UNVERIFIED from excerpt | No-key example; configured auth syntax UNVERIFIED from excerpt | L5 |
| vLLM | Current quickstart localhost:8000/v1; current default independently UNVERIFIED; historical v0.8.3 default 8000 | Exact current listing envelope UNVERIFIED from L6 | OpenAI Chat server; exact framing UNVERIFIED from excerpt | Suitable parser/template; enable-auto-tool-choice documented | Historical api-key option; full current modes UNVERIFIED | L6-L8 |
| KoboldCpp | README default 5001, example localhost:5001/v1 | Exact path/envelope UNVERIFIED; interactive /api probe unavailable | Compatible interface; exact SSE UNVERIFIED from excerpt | Tools/agents documented; exact OpenAI/fragment schema UNVERIFIED | Current default/enforcement UNVERIFIED from README excerpt | L9 |
| LocalAI | CLI address default :8080; /v1 example base | /v1/models in auth/error examples | Official SSE chat examples | Functions/tools documented, backend-dependent | Optional LOCALAI_API_KEY/API_KEY or auth mode; exact deployed enforcement unprobed | L10-L13 |

### Dialect recommendation and additional candidate

Design recommendation: four initial dialect adapters, CC with provider overrides, OpenAI Responses, Anthropic Messages, and Gemini native with separate GenerateContent/Interactions codecs. Share HTTP/SSE framing, never assume native event or tool-result schemas match. Evidence: A1-A3/O1/G1-G4 and generation references above. Normalize text, tool fragments, completed calls, usage, waits and errors internally. Assemble and validate tool JSON before use; deck changes become reviewable patches. Models and capabilities refresh from provider discovery; allow manual model IDs where listing remains unknown.

Additional reasonable cloud candidate: Ollama cloud, explicitly distinguished in L1. Its direct base/auth/list/pricing/terms and stream/tool differences remain UNVERIFIED beyond that documentation; local compatibility is not cloud certification. Keep configurable CC endpoints so new vendors need no architectural rewrite, without inventing unresearched vendor support.

### Source register

All access dates 2026-10-11; failed and indexed-only probes disclosed above.

- A1 https://platform.claude.com/docs/en/api/messages/create
- A2 https://platform.claude.com/docs/en/api/models/list
- A3 https://platform.claude.com/docs/en/build-with-claude/streaming
- A4 https://platform.claude.com/docs/en/api/http/messages/count_tokens
- O1 https://developers.openai.com/api/reference/resources/responses/methods/create
- O2 https://developers.openai.com/api/reference/resources/responses/subresources/input_tokens/methods/count
- G1 https://ai.google.dev/gemini-api/docs/quickstart
- G2 https://ai.google.dev/api/generate-content
- G3 https://ai.google.dev/api/models
- G4 https://ai.google.dev/api/tokens
- M1 https://docs.mistral.ai/api
- M2 https://docs.mistral.ai/api/endpoint/models
- X1 https://docs.x.ai/developers/rest-api-reference/inference/chat-completions
- X2 https://docs.x.ai/developers/rest-api-reference/inference/models
- X3 https://docs.x.ai/developers/model-capabilities/text/streaming
- X4 https://docs.x.ai/developers/cost-tracking
- D1 https://api-docs.deepseek.com/api/create-chat-completion/
- D2 https://api-docs.deepseek.com/api/create-response/
- D3 https://api-docs.deepseek.com/api/list-models/
- D4 https://api-docs.deepseek.com/quick_start/token_usage/
- D5 https://api-docs.deepseek.com/quick_start/pricing/
- Q1 https://console.groq.com/docs/api-reference
- Q2 https://console.groq.com/docs/openai
- Q3 https://console.groq.com/docs/rate-limits
- R1 https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties
- R2 https://openrouter.ai/docs/api/api-reference/chat/send-chat-completion-request
- T1 https://openai.com/policies/services-agreement/
- T2 https://www.anthropic.com/legal/commercial-terms
- T3 https://ai.google.dev/gemini-api/terms
- T4 https://mistral.ai/terms-of-service and https://legal.mistral.ai/terms/row-consumer-terms/
- T5 https://x.ai/legal/terms-of-service
- T6 https://api-docs.deepseek.com/quick_start/terms_of_service and https://www.deepseek.com/platform/
- T7 https://groq.com/terms-of-use
- T8 https://openrouter.ai/terms
- L1 https://docs.ollama.com/api/openai-compatibility
- L2 https://lmstudio.ai/docs/developer/openai-compat
- L3 https://lmstudio.ai/docs/developer/openai-compat/tools
- L4 https://lmstudio.ai/docs/developer/core/authentication
- L5 https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md
- L6 https://docs.vllm.ai/en/latest/serving/online_serving/openai_compatible_server/
- L7 https://docs.vllm.ai/en/latest/getting_started/quickstart/
- L8 https://docs.vllm.ai/en/stable/features/tool_calling/
- L9 https://github.com/LostRuins/koboldcpp/blob/concedo/README.md
- L10 https://localai.io/docs/reference/cli-reference/index.print.html
- L11 https://localai.io/docs/reference/api-errors/index.print.html
- L12 https://localai.io/docs/basics/try/
- L13 https://localai.io/docs/features/index.print.html

## Options

1. CC only: simpler but loses native Anthropic/Gemini and Responses semantics.
2. Four dialect adapters with provider capabilities and Gemini subcodecs: recommended.
3. Broker only: adds account/terms/cost dependency and fails direct-provider preference.

## Recommendation

Choose option 2; preserve all named providers in scope. Separate documented configuration from validated runtime support. Follow up UNVERIFIED schemas, units and applicable terms using contract fixtures at implementation/release gates. Use returned usage for reconciliation and source-aware estimates, not invented rates or permanent model lists.

## Risks

Personal-use eligibility remains unresolved for commercial-purpose services, especially Gemini. Defaults and model capabilities change. Partial streams cannot authorize deck mutations. Documentation retrieval is not an integration test.

## Confidence

High on multiple codecs and secure direct BYOK architecture; medium on retrieved protocol summaries; low on unprobed binaries/account availability and unresolved legal clauses. Scope items are answered or explicitly UNVERIFIED with attempted official sources.
