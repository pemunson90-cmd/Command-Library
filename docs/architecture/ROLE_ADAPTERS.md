# Tool-neutral execution roles

Latest CONTINUE/D17 governs all providers. coordination/agents role briefs remain source. Existing .claude/agents are adapters, not authority to alter shared files or model availability.

| Role | Allowed outputs | Review independence |
|---|---|---|
| orchestrator | shared state/queue/leases/config/CI/registries and merges under single lease | Doesn't self-approve authored code |
| architect | one named docs/architecture/<component>/ARCHITECT.md | independent contract audit before code |
| implementer | locked packages/<component>/** on agent/<task-id>, tests and assigned task handoff | separate reviewer; no shared edits |
| researcher | one named report and dated evidence; no bulk/credential leakage | unknowns stay explicit |
| reviewer | named review report/section only, read-only code | never reviews own produced work |
| test-writer | named test paths inside same ownership assignment | coordinates lock with implementer, not concurrent overlapping writes |
| UI auditor | named screen/evidence audit report only | DESIGN_SYSTEM+screens/tasks/goldens and actual reader evidence |

Use strongest actually available architect/reviewer; inexpensive suitable models for routine tasks. If Sonnet/Haiku/tool agents unavailable, record actual alternative, never advertise unconfigured settings. A general GPT/Claude may resume as orchestrator through README using available GitHub/filesystem tools. If no writer capability, produce explicit unpublished review/patch and leave publisher task. Native subagents only when explicitly requested by task/gauntlet; one component per architect invocation; no recursive spawning.

Cross-account switch is sequential user opening available model/account and saying continue with repo access; no model can guarantee automatic background access to another account. Ready files remove project re-explanation, not platform access restrictions.
