# R-00 Claude Code tooling

Researcher: read-only doc verification. All pages fetched 2026-10-11 from code.claude.com/docs (current). The locally installed CLI reports 2.1.296, and the docs reference versions up to v2.1.295, so the docs and the local binary are in the same era. Where a detail is not stated in the docs it is marked UNVERIFIED.

## Question

What do the current official Claude Code docs say about: (1) project subagent definition files and every frontmatter field, (2) worktree isolation for subagents, (3) background subagents, (4) subagents spawning subagents, (5) hooks (locations, events, JSON shape, blocking, matchers, hooks in subagent frontmatter), and (6) limits that apply when running on the web / in cloud sessions?

## Findings

### 1. Project subagent definition files

- Location: project subagents live in `.claude/agents/` (markdown files). User-level ones live in `~/.claude/agents/`. Both directories are scanned recursively, and identity comes only from the `name` frontmatter field, not from the filename or subfolder. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- Project subagents are discovered by walking up from the current working directory, so every `.claude/agents/` between there and the repository root is scanned. If nested directories define the same `name`, the definition closest to the working directory wins. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- Scope priority when names collide: managed settings (1, highest), `--agents` CLI flag (2), `.claude/agents/` (3), `~/.claude/agents/` (4), plugin `agents/` (5, lowest). Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- File format: YAML frontmatter between `---` markers at the very top of the file, then the system prompt as Markdown. The body is the whole system prompt; the subagent gets that plus basic environment details (working directory), not the Claude Code system prompt. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- Only `name` and `description` are required. Multi-word field names are camelCase and must match exactly; an unrecognized field is silently ignored with no error. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- Every documented frontmatter field (the complete table on the page):
  - `name` (required): unique id, at most 256 characters, cannot contain `:` or start with `-`. Hooks receive it as `agent_type`. Filename need not match.
  - `description` (required): when Claude should delegate to this subagent.
  - `tools` (optional): allowlist.
  - `disallowedTools` (optional): denylist, removed from the inherited or specified list. An entry with a specifier such as `Bash(git push *)` still removes the whole tool.
  - `model` (optional): see below.
  - `permissionMode` (optional): `default`, `acceptEdits`, `auto`, `dontAsk`, `bypassPermissions`, `plan`, or `manual` (alias of `default`). Ignored for plugin subagents. The parent's mode can override it (parent in `bypassPermissions`, `acceptEdits`, or auto mode wins).
  - `maxTurns` (optional): cap on agentic turns; output is marked partial at the limit and the subagent can be resumed (partial marking needs v2.1.246+).
  - `skills` (optional): skills preloaded in full into context at startup (first 32 distinct names).
  - `mcpServers` (optional): server names or inline definitions (stdio, http, sse, ws). Ignored for plugin subagents. Inline servers from a project agent file load only after the folder is trusted.
  - `hooks` (optional): lifecycle hooks scoped to this subagent. Ignored for plugin subagents.
  - `memory` (optional): `user`, `project`, or `local` persistent memory directory.
  - `background` (optional): `true` keeps the subagent in the background even if Claude asks for foreground.
  - `omitClaudeMd` (optional): `true` launches without user/project/local CLAUDE.md (v2.1.271+).
  - `effort` (optional): `low`, `medium`, `high`, `xhigh`, `max` (available levels depend on the model).
  - `isolation` (optional): `worktree`.
  - `color` (optional): `red`, `blue`, `green`, `yellow`, `purple`, `orange`, `pink`, `cyan`.
  - `initialPrompt` (optional): auto-submitted first user turn when the agent runs as the main session agent (`--agent` or the `agent` setting).
  - `experimental` (optional): map; only `cacheTtl: 5m | 1h` is honored (v2.1.248+).
  Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- `model` accepted values: aliases `sonnet`, `opus`, `haiku`, `fable`; a full model ID such as `claude-opus-5-5` or `claude-sonnet-5` (same values as `--model`); or `inherit`. If omitted, resolution order is: per-invocation `model` parameter, then frontmatter `model`, then `CLAUDE_CODE_SUBAGENT_MODEL`, then the main conversation's model. `CLAUDE_CODE_SUBAGENT_MODEL_FORCE=1` makes the env var override everything. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- `tools` syntax: either a comma-separated string (`tools: Read, Grep, Bash`) or a YAML list. If omitted, the subagent inherits every tool available to subagents (built-in plus MCP tools from the main conversation, minus a short fixed exclusion list such as `AskUserQuestion`, `EnterPlanMode`, `ScheduleWakeup`, `Workflow`). If both `tools` and `disallowedTools` are set, `disallowedTools` is applied first and a tool in both is removed. If no entry resolves to a usable tool, the Agent tool usually refuses to launch the subagent with an error naming the entries. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- `tools` also accepts MCP server patterns (`mcp__<server>` or `mcp__<server>__*`); `disallowedTools` additionally accepts `mcp__*`. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- Files that are silently skipped (no error in the session, reason only in the `--debug` log): no `name` (treated as documentation), opening `---` not on the first line, a `name` starting with `-` or containing `:` or over 256 characters, a `name` with no `description`, or YAML that does not parse. `claude plugin validate .claude/agents` checks a directory for unparseable frontmatter (v2.1.233+). Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- Hot reload: Claude Code watches `~/.claude/agents/` and `.claude/agents/` and picks up edits within seconds, except when the `agents` directory did not exist at session start, for `--add-dir` directories, or with `--disable-slash-commands`. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- Alternative definition route: `claude --agents '<json>'` (session only, not saved). JSON fields are `prompt` plus `description`, `tools`, `disallowedTools`, `model`, `permissionMode`, `mcpServers`, `hooks`, `maxTurns`, `skills`, `initialPrompt`, `memory`, `effort`, `background`, `omitClaudeMd`, `isolation`; `color` and `experimental` are ignored there. A JSON file path is accepted in non-interactive mode (v2.1.281+). Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- Invocation: natural language, `@"name (agent)"` mention, or whole session via `claude --agent <name>` / `agent` setting. The Agent tool input schema in the SDK reference is `description`, `prompt`, `subagent_type`, `model`, `effort`, `run_in_background`, `name`, `isolation` (`"worktree" | "remote"`), plus deprecated `team_name` and `mode`. Source: https://code.claude.com/docs/en/agent-sdk/typescript (accessed 2026-10-11)
- UNVERIFIED: the docs do not state a maximum count of custom subagent files; they only state a startup warning when combined descriptions exceed 15,000 tokens. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)

### 2. Worktree isolation for subagents

- Requested two ways: (a) frontmatter `isolation: worktree` in the subagent file (permanent for that subagent), or (b) the Agent tool's per-call `isolation: "worktree"` parameter (Claude passes it when asked, for example "use worktrees for your agents"). Source: https://code.claude.com/docs/en/worktrees (accessed 2026-10-11) and https://code.claude.com/docs/en/agent-sdk/typescript (accessed 2026-10-11)
- Location: the default worktree location is `.claude/worktrees/<name>/` at the repository root (documented for `--worktree`; subagent worktrees "use the same base branch as `--worktree`" and sit in "the default location under `.claude/worktrees/`"). The exact per-subagent directory name is UNVERIFIED. Source: https://code.claude.com/docs/en/worktrees (accessed 2026-10-11)
- Branch: the worktree is branched by default from the repository's default branch on the remote (`worktree.baseRef: "fresh"`, usually `main`), NOT from the parent session's `HEAD`. Setting `worktree.baseRef` to `"head"` in settings branches from the current local `HEAD` (documented as the choice "when isolating subagents that need to operate on in-progress work"). `baseRef` cannot be a branch name. If no remote or `origin/HEAD` is available, it falls back to local `HEAD`. Source: https://code.claude.com/docs/en/worktrees (accessed 2026-10-11) and https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- Branch name for a subagent worktree: UNVERIFIED. The docs state `worktree-<name>` only for `--worktree` sessions. The SDK Agent result exposes `worktreeBranch` and `worktreePath` when a worktree is kept, so the parent can read them from the result. Source: https://code.claude.com/docs/en/agent-sdk/typescript (accessed 2026-10-11)
- Cleanup: "Each subagent gets a temporary worktree that Claude Code removes automatically when the subagent finishes without changes; a worktree with changes stays on disk" until a periodic sweep can remove it without losing work. The sweep removes subagent and background-session worktrees older than `cleanupPeriodDays` (default 30 days), but leaves any worktree that still holds changed/untracked files or unpushed commits, or whose submodules hold work. Whether a worktree holding only committed (but unpushed) work counts as "changes" at subagent finish is UNVERIFIED; the sweep clearly keeps unpushed commits. Source: https://code.claude.com/docs/en/worktrees (accessed 2026-10-11)
- While a subagent runs, Claude Code holds a `git worktree lock` on its worktree and releases it on finish; to remove a kept one run `git worktree remove` (add `--force` for uncommitted changes; `git worktree unlock` first if locked). Source: https://code.claude.com/docs/en/worktrees (accessed 2026-10-11)
- Enforcement: a subagent with `isolation: worktree` has Bash/PowerShell commands checked so they cannot resolve to the main checkout; Edit/Write/NotebookEdit on main-checkout paths are blocked; git redirects into the main checkout (`git -C`, `--git-dir`, `GIT_DIR`, `GIT_WORK_TREE`, `cd` into main) are blocked; commands whose git target cannot be verified from the text are refused. The checks do not track files written by `cp` or shell redirects. Source: https://code.claude.com/docs/en/worktrees (accessed 2026-10-11)
- Other documented limits: `ExitWorktree` is not available to subagents already in their own working directory; a subagent in a worktree takes CLAUDE.md and `.claude/rules/` from the main conversation, not from the worktree root (default location); `.worktreeinclude` copies matching gitignored files (for example `.env`) into each new worktree; git LFS pointer files appear when LFS was installed with `git lfs install --local`; creation fails if `.claude`, `.claude/worktrees`, or the worktree dir is a symlink; `git commit` works inside a worktree with the sandbox on (shared `.git`). Source: https://code.claude.com/docs/en/worktrees (accessed 2026-10-11) and https://code.claude.com/docs/en/tools-reference (accessed 2026-10-11)
- Custom VCS: a `WorktreeCreate` hook replaces the git logic (fires for `--worktree`, subagent `isolation: "worktree"`, and background sessions); any non-zero exit fails creation. Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11)
- Agent teams edge case: a named subagent spawned in a session with agent teams enabled becomes a teammate and runs in the main working directory unless the call itself passes `isolation`; an `isolation` value in the frontmatter does not prevent that. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)

### 3. Background subagents

- How started: (a) frontmatter `background: true`; (b) Claude chooses it (the Agent tool `run_in_background` parameter); (c) Ctrl+B on a running task; (d) default behavior: where fork mode is on (default in interactive sessions) every subagent Claude spawns runs in the background and the `run_in_background` parameter is removed; where fork mode is off (non-interactive `-p` and the Agent SDK by default) Claude runs a subagent in the background by default and in the foreground when it needs the result. `CLAUDE_CODE_DISABLE_BACKGROUND_TASKS=1` forces foreground. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- How results come back: as a completion notification in a later turn, marked as an automated event; Claude waits for it before reporting. In the SDK the Agent result is `status: "async_launched"` with an `agentId` and an `outputFile`. In `claude -p`, the run stays open until background subagents finish (idle-wait cap 10 minutes by default, `CLAUDE_CODE_PRINT_BG_WAIT_CEILING_MS`). A subagent's final report is scanned and may get a backslash or a `[harness: ...]` marker line prepended. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11), https://code.claude.com/docs/en/agent-sdk/typescript (accessed 2026-10-11), https://code.claude.com/docs/en/headless (accessed 2026-10-11)
- Permission prompts: a background subagent surfaces every permission prompt in the main session, naming the subagent; approve, or press Esc to deny that one call without stopping the subagent. A durable answer (for example a session-long grant) applies to the whole session. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- Tools in the background: a background subagent KEEPS every MCP tool but only these built-ins: Read, Grep, Glob, LSP, Bash, PowerShell, Edit, Write, NotebookEdit, WebFetch, WebSearch, TodoWrite, Skill, ToolSearch, EnterWorktree, ExitWorktree, Monitor, TaskStop, SendMessage, Artifact (plus SubagentHandback). Every other built-in is removed even if listed in `tools`, with no error unless nothing is left. So "MCP tools unavailable in background" is NOT what the docs say. `AskUserQuestion` is removed from every subagent. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- Other limits: 20 concurrent subagents by default (`CLAUDE_CODE_MAX_CONCURRENT_SUBAGENTS`, spawn fails with `Concurrent subagent limit reached`); a background subagent can be resumed via `SendMessage`; a subagent you stop with `x` in `/tasks` does not auto-resume. In non-interactive `-p` runs the exact handling of a background subagent's permission prompt when nobody can answer is UNVERIFIED (docs only say a hook `ask` is denied in `-p` when no one can answer). Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)

### 4. Can subagents spawn subagents?

- YES by default. Documented statement: "By default, a subagent can spawn subagents of its own, up to three layers below the main conversation." At the depth limit Claude Code withholds the `Agent` tool from every subagent except a fork (whose `Agent` call returns an error). Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- Control: set `CLAUDE_CODE_MAX_SUBAGENT_SPAWN_DEPTH` (via the `env` block of settings) to the number of layers wanted; `1` turns nesting off. To stop one specific subagent from spawning, omit `Agent` from its `tools` list or add it to `disallowedTools`. Note that omitting `tools` entirely inherits `Agent` (while depth allows). Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- Version history (older docs and older binaries differ): v2.1.172 to v2.1.216 nested up to five layers; v2.1.217 to v2.1.218 default depth 1; v2.1.219 and later default three. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- The `Agent(type1, type2)` allowlist syntax in `tools` applies only to an agent running as the main thread via `--agent`; inside a subagent definition any type list in the parentheses is ignored. A fork cannot spawn further forks. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)
- Non-interactive nuance: in `-p` and the SDK, a launching subagent does not wait for its nested background subagents, so a late nested result is reported to the main conversation. Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)

### 5. Hooks

- Settings file locations: `~/.claude/settings.json` (all projects), `.claude/settings.json` (project, committable), `.claude/settings.local.json` (project, gitignored), managed policy settings, plugin `hooks/hooks.json`, skill frontmatter, subagent frontmatter. Hooks from settings files, managed policy, and plugins also fire inside subagents; the input then carries `agent_id` and `agent_type`. Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11)
- Event list (complete table): SessionStart, Setup, UserPromptSubmit, UserPromptExpansion, PreToolUse, PermissionRequest, PermissionDenied, PostToolUse, PostToolUseFailure, PostToolBatch, Notification, MessageDisplay, SubagentStart, SubagentStop, TaskCreated, TaskCompleted, Stop, StopFailure, TeammateIdle, InstructionsLoaded, ConfigChange, CwdChanged, DirectoryAdded, FileChanged, WorktreeCreate, WorktreeRemove, PreCompact, PostCompact, PreModelSwitch, PostModelSwitch, Elicitation, ElicitationResult, SessionEnd. Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11)
- Git commit: there is NO git-commit-specific event. The documented route is PreToolUse (or PostToolUse) on the `Bash` tool, optionally narrowed by a handler-level `if` field using permission-rule syntax such as `"Bash(git commit *)"`. The hooks reference contains no git-commit event, example, or hook name (searched the full page for "git commit", "pre-commit", and "PreCommit": zero matches). Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11) and https://code.claude.com/docs/en/hooks-guide (accessed 2026-10-11)
- JSON config shape: three nesting levels. `{"hooks": {"<Event>": [ {"matcher": "<pattern>", "hooks": [ {"type": "command", "command": "...", ...} ] } ] } }`. Handler types: `command`, `http`, `mcp_tool`, `prompt`, `agent`. Common handler fields: `type`, `if`, `timeout` (seconds; default 600 for command), `statusMessage`, `once` (honored only in skill frontmatter). Command-hook fields: `command`, `args` (exec form, no shell), `async`, `asyncRewake`, `shell`, `onFailure` (`continue` default or `block`, v2.1.295+). All matching hooks run in parallel. Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11)
- Input: command hooks get JSON on stdin with common fields `session_id`, `transcript_path`, `cwd`, `permission_mode`, `hook_event_name`, `agent_id`/`agent_type` (inside subagents), plus event fields (`tool_name`, `tool_input`, `tool_use_id` for tool events). Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11)
- How a hook BLOCKS: exit code 2 is the blocking error; on events that can block, Claude Code stops the action and the hook's stderr is the reason (shown to Claude). Exit 0 is success (JSON on stdout is parsed). Any other exit code (including 1) is a non-blocking error and the action proceeds, unless `onFailure: "block"` is set. Exit 2 wins even if JSON is printed. Timeouts and crashes do not block by default. Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11)
- Per-event exit-2 effect: PreToolUse blocks the tool call; SubagentStop prevents the subagent from stopping; Stop prevents Claude from stopping; UserPromptSubmit blocks the prompt; TaskCompleted prevents completion; PostToolUse, PostToolUseFailure, SubagentStart, SessionStart, and Notification cannot block (stderr shown only); PermissionRequest ignores exit 2 (use a decision object). Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11)
- JSON decision fields (exit 0 with JSON on stdout): PreToolUse uses `hookSpecificOutput.permissionDecision` (`allow`, `deny`, `ask`, `defer`) plus `permissionDecisionReason` and optional `updatedInput`; precedence is deny over defer over ask over allow; the old top-level `decision`/`reason` is deprecated for PreToolUse. Stop, SubagentStop, PostToolUse, UserPromptSubmit use top-level `{"decision": "block", "reason": "..."}`. Universal fields: `continue` (false stops Claude entirely), `stopReason`, `systemMessage`. For SubagentStop, `decision: "block"` keeps the subagent running and delivers `reason` as its next instruction; `hookSpecificOutput.additionalContext` gives non-error feedback. Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11)
- Stop/SubagentStop loop protection: the input has `stop_hook_active` (true when already continuing because of a stop hook); an 8-consecutive-continuation cap ends the turn (adjustable with `CLAUDE_CODE_STOP_HOOK_BLOCK_CAP`). The page states the cap under Stop; that it applies identically to SubagentStop is inferred from "same decision control format". SubagentStop input adds `agent_id`, `agent_type`, `agent_transcript_path`, `last_assistant_message`. Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11)
- Matcher syntax: `"*"`, empty, or omitted matches all; a value of only letters, digits, `_`, `-`, spaces, `,`, `|` is an exact string or a list separated by `|` or `,` (`Edit|Write`); any other character makes it an unanchored JavaScript regex (`^Notebook`, `mcp__memory__.*`). Matcher targets by event: tool name for PreToolUse/PostToolUse; agent type for SubagentStart/SubagentStop (the frontmatter `name`; plugin names like `my-plugin:agent` take the regex path, so anchor with `^...$`); startup/resume/clear/compact/fork for SessionStart. Stop, UserPromptSubmit, TaskCompleted, WorktreeCreate and others have no matcher support (a matcher there is silently ignored). Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11)
- Handler-level `if`: permission-rule syntax, evaluated only on tool events; one rule per handler. For Bash it checks each subcommand, strips leading `VAR=value`, and looks inside `$()` and backticks. It is best-effort: when the command cannot be analyzed the hook runs anyway, and it is not a security boundary (a `Bash(git push *)` style rule does not catch `git -C . push`, `/usr/bin/git`, or `sh -c '...'`). `Bash(git commit *)` also matches bare `git commit` (a trailing space-star matches the bare command). Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11) and https://code.claude.com/docs/en/permissions (accessed 2026-10-11)
- Hooks inside subagent frontmatter: YES. The `hooks` frontmatter field uses the same format as settings. They run only while that subagent is active and are cleaned up when it finishes. All hook events are supported; common ones are PreToolUse, PostToolUse, and `Stop`, which is automatically converted to `SubagentStop` at runtime when the agent is spawned as a subagent. Not honored for plugin subagents. A project subagent's frontmatter hooks run only after the workspace trust dialog is accepted for that folder; a `-p` session does NOT count as trusted for this (hooks in settings files do get automatic trust in `-p`). Source: https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11) and https://code.claude.com/docs/en/hooks (accessed 2026-10-11)
- Worktree interaction: after Claude enters a worktree, `${CLAUDE_PROJECT_DIR}` stays at the original project root, while the hook input `cwd` follows Claude into the worktree. Hooks run in the current directory with Claude Code's environment. Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11) and https://code.claude.com/docs/en/worktrees (accessed 2026-10-11)
- UNVERIFIED: the docs do not state what `cwd` a SubagentStop hook receives for a subagent that ran with `isolation: worktree` (subagent worktree versus main session directory). The statement "cwd follows Claude" is written for sessions that enter a worktree. This needs the empirical check. Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11)
- `disableAllHooks: true` turns hooks off; managed hooks cannot be disabled from user/project settings; `allowManagedHooksOnly` (managed settings) blocks user, project, local, and plugin hooks. Command hook scripts must be executable on macOS/Linux or the hook fails (non-blocking) instead of blocking. Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11) and https://code.claude.com/docs/en/sub-agents (accessed 2026-10-11)

### 6. Cloud sessions (Claude Code on the web) limits

- Hooks in cloud: the repo's `.claude/settings.json` hooks and permission rules DO load, but only in a session with one repository. In an Anthropic-hosted multi-repo session, no repository's hooks, permission rules, or `env` load (only `enabledPlugins` and `extraKnownMarketplaces` are read). `~/.claude/settings.json` and `.claude/settings.local.json` are not read. Organization server-managed settings apply (except in Claude Tag sessions). Source: https://code.claude.com/docs/en/cloud-environments (accessed 2026-10-11) and https://code.claude.com/docs/en/settings (accessed 2026-10-11)
- `$CLAUDE_CODE_REMOTE` is `"true"` in remote web environments and unset in the local CLI; hooks run in both, so scope with that variable. Source: https://code.claude.com/docs/en/hooks (accessed 2026-10-11) and https://code.claude.com/docs/en/cloud-environments (accessed 2026-10-11)
- SessionStart hooks in the cloud: a `command` hook is cancelled after 600 seconds by default; they need network access for installs (fail with network level None); the order is setup script first, then SessionStart hooks. Source: https://code.claude.com/docs/en/cloud-environments (accessed 2026-10-11)
- Subagents: "Subagents work the same way they do locally"; subagents defined in the repo's `.claude/agents/` are picked up automatically; `~/.claude/agents/` (user level) is not available (live on your machine, commit to the repo instead). Source: https://code.claude.com/docs/en/claude-code-on-the-web (accessed 2026-10-11) and https://code.claude.com/docs/en/cloud-environments (accessed 2026-10-11)
- Worktrees in cloud sessions: no cloud-specific restriction or support statement found on the cloud pages; the worktree docs do not mention cloud. UNVERIFIED whether `isolation: worktree` works unchanged in a cloud session (it needs a git repository, which a cloud clone is). Source: https://code.claude.com/docs/en/claude-code-on-the-web (accessed 2026-10-11)
- Background agents in cloud: background work (including subagents and shell commands) is NOT restored when an expired VM is reopened; conversation history is. Idle sessions pause after a few minutes without activity and the VM can later be reclaimed. A session also counts as inactive while waiting for an MCP connector approval. Source: https://code.claude.com/docs/en/claude-code-on-the-web (accessed 2026-10-11) and https://code.claude.com/docs/en/cloud-environments (accessed 2026-10-11)
- Branch push restrictions: in Anthropic-hosted environments all GitHub operations go through a proxy. It rejects branch deletions and pushes of anything other than a branch (for example tags). It does NOT limit which branches a push can update; use GitHub branch protection or rulesets for that. The proxy also scopes API access to the repositories attached to the session and rejects GitHub GraphQL (`gh pr` and `gh issue` fail with 403; use `gh api repos/{owner}/{repo}/...`). No branch-name prefix rule is documented. Source: https://code.claude.com/docs/en/cloud-environments (accessed 2026-10-11)
- Git state: a cloud session clones the repository's remote at the current branch (not the local checkout), so unpushed local commits are absent; non-GitHub hosts can be bundled but cannot push back. Source: https://code.claude.com/docs/en/claude-code-on-the-web (accessed 2026-10-11)
- Other limits: commands run with the Bash defaults (2 minute foreground default, up to 10 minutes; a timed-out command moves to the background and gets up to 30 more minutes); shared rate limits with the account; ZDR/HIPAA organizations cannot use cloud sessions; agent teams are off unless `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1`. The Agent tool also accepts `isolation: "remote"`, which dispatches a task to a cloud session (`remote_launched` result). Source: https://code.claude.com/docs/en/cloud-environments (accessed 2026-10-11), https://code.claude.com/docs/en/claude-code-on-the-web (accessed 2026-10-11), https://code.claude.com/docs/en/agent-sdk/typescript (accessed 2026-10-11)

## Example: verified subagent file

Path: `.claude/agents/gauntlet-implementer.md`. Every field below appears in the documented frontmatter table (verified-by-doc). The `name` and `description` are required; the rest are optional choices.

```
---
name: gauntlet-implementer
description: Implements one scoped change in an isolated git worktree, commits it, and reports the branch and commit hash. Use proactively for parallel, non-overlapping edits.
tools: Read, Grep, Glob, Edit, Write, Bash
model: sonnet
isolation: worktree
maxTurns: 40
---

You implement exactly the task you were given, inside your own worktree.
Before finishing: run the project's checks, stage and commit your work with a
clear message, and report the worktree path, branch name, and commit hash.
Do not edit files outside your worktree.
```

Notes on this file (all verified-by-doc unless marked):
- `tools` is a comma-separated string and deliberately omits `Agent`, so this subagent cannot spawn further subagents (verified-by-doc: omit `Agent` from `tools`).
- With `isolation: worktree` the worktree branches from the repository default branch unless `worktree.baseRef` is `"head"` (verified-by-doc).
- Whether the commit survives is tied to the worktree retention rule: a worktree with changes is kept; a committed-only worktree is kept by the periodic sweep as "unpushed commits", but immediate-removal behavior at finish for commit-only work is UNVERIFIED.

## Example: verified hook configs

Both configs go in `.claude/settings.json` (project, committable). Settings-file hooks fire inside subagents and get automatic trust in `-p` runs, which is why they are preferred over subagent-frontmatter hooks for headless use.

### (a) Block a SubagentStop when the worktree has uncommitted changes

Config (shape and fields verified-by-doc; the matcher value is the subagent's frontmatter `name`; the `args: []` plus `${CLAUDE_PROJECT_DIR}` form is copied from the documented exec-form example):

```
{
  "hooks": {
    "SubagentStop": [
      {
        "matcher": "gauntlet-implementer",
        "hooks": [
          {
            "type": "command",
            "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/require-clean-worktree.sh",
            "args": []
          }
        ]
      }
    ]
  }
}
```

Script `.claude/hooks/require-clean-worktree.sh` (make it executable with `chmod +x`, verified-by-doc requirement):

```
#!/bin/bash
input=$(cat)
dir=$(jq -r '.cwd' <<<"$input")
# INFERRED: assumes .cwd is the subagent's worktree. UNVERIFIED in the docs,
# see Risks. Log it during the empirical test:
# echo "$input" >> "${CLAUDE_PROJECT_DIR}/.claude/hooks/subagentstop.log"
dirty=$(git -C "$dir" status --porcelain 2>/dev/null)
if [ -n "$dirty" ]; then
  echo "Uncommitted or untracked changes remain in $dir. Commit them, then finish." >&2
  exit 2
fi
exit 0
```

Mark each part:
- Event `SubagentStop`, matching on agent type name, command hook receiving JSON on stdin: verified-by-doc.
- Exit 2 on SubagentStop "prevents the subagent from stopping" and delivers stderr as the next instruction: verified-by-doc. The equivalent JSON form (exit 0, print `{"decision": "block", "reason": "..."}`) is also verified-by-doc.
- `git status --porcelain` listing untracked as well as modified files: verified-by-doc (the PostToolUse section recommends it for that reason).
- That `cwd` equals the subagent's worktree for a SubagentStop hook: UNVERIFIED (inferred). If `cwd` is the main session directory, this script would inspect the wrong tree.
- The 8-consecutive-continuation cap bounding a subagent that never commits: documented for Stop, inferred for SubagentStop.
- Alternative placement: the same script as a `Stop` hook in the subagent's frontmatter `hooks:` block (converted to SubagentStop at runtime, verified-by-doc), but that needs workspace trust and is skipped in `-p` sessions (verified-by-doc).

### (b) Run a scan script before `git commit` via PreToolUse on Bash

Config (verified-by-doc shape; `if` filter and `onFailure` documented):

```
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "if": "Bash(git commit *)",
            "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/scan-before-commit.sh",
            "args": [],
            "onFailure": "block"
          }
        ]
      }
    ]
  }
}
```

Script `.claude/hooks/scan-before-commit.sh`:

```
#!/bin/bash
input=$(cat)
dir=$(jq -r '.cwd' <<<"$input")
cd "$dir" || exit 2
# Example path: ./scripts/scan.sh stands for the project scan script (not yet written).
if ! ./scripts/scan.sh >&2; then
  echo "Blocked: scan failed. Fix the findings, then retry git commit." >&2
  exit 2
fi
exit 0
```

Mark each part:
- PreToolUse with `matcher: "Bash"`, stdin JSON with `tool_input.command`, exit 2 blocks the tool call and Claude sees stderr: verified-by-doc.
- `if: "Bash(git commit *)"` as a per-handler filter, matching bare `git commit` and commands inside compound lines such as `npm test && git commit -m x`: verified-by-doc.
- `onFailure: "block"` so a missing or crashing script blocks instead of letting the commit through: verified-by-doc, requires Claude Code v2.1.295 or later (local binary is 2.1.296).
- Using `cwd` from the input JSON as the directory to scan (so a commit made inside a worktree scans that worktree): verified-by-doc that `cwd` follows Claude into a worktree it entered; inferred for a subagent started with `isolation: worktree`.
- The scan script `./scripts/scan.sh`: an example path, not from the docs. It must exist in the worktree (tracked files are present in a fresh worktree; untracked or ignored ones need `.worktreeinclude`).
- INFERRED coverage gap: `Bash(git commit *)` does not catch forms such as `git -C dir commit`, `git -c k=v commit`, `/usr/bin/git commit`, or `sh -c 'git commit'` (the permissions page documents this for the `git push *` analogue). For a stricter net, drop the `if` field and make the script grep `tool_input.command` for `git` followed by `commit` (inferred). The docs say the permission system or sandbox, not a hook, should enforce hard security boundaries.

## Empirical verification in this environment

Run by the orchestrator on 2026-10-11 in this cloud session (`CLAUDE_CODE_REMOTE=true`, `CLAUDE_CODE_REMOTE_ENVIRONMENT_TYPE=cloud_default`).

- **Push:** `git push -u origin ccr-b0894b79-qzhxhw` succeeded with commit f4fa915 (GAUNTLET.md verbatim). Verified.
- **Worktree isolation works in this cloud session.** A Haiku subagent launched through the Agent tool with `isolation: "worktree"` reported:
  - working directory `/home/user/Command-Library/.claude/worktrees/agent-<agentId>`
  - branch `worktree-agent-<agentId>`
  - HEAD 512cdcc (`origin/main`, "Initial commit") while the session branch was at f4fa915. This confirms the documented default: subagent worktrees branch from the remote default branch, not the parent's HEAD.
  - `git worktree list` showed the worktree as `locked` while the agent ran.
  - After the agent finished with no changes, `git worktree list` showed only the main checkout: removed automatically. `.claude/worktrees/` remains as an empty directory (now gitignored).
  This resolves three UNVERIFIED items above: cloud support, directory name, branch name.
- **Nesting is off in this environment.** The environment sets `CLAUDE_CODE_MAX_SUBAGENT_SPAWN_DEPTH=1`, and the probe subagent reported no `Agent` tool in its tool list. GAUNTLET.md section 11 ("Subagents cannot spawn subagents") holds here. Every `.claude/agents/` definition also sets `disallowedTools: Agent`, so the rule holds in an environment where the variable differs.
- **Background subagents verified.** Both Gate 0 subagents ran in the background and returned through completion notifications.
- **Hooks not loaded in this session.** This session has two repositories attached (Command-Library and Pocket-Steward). Per the cloud docs, "a session with several repositories doesn't load hooks from any repository's `.claude/settings.json`" (https://code.claude.com/docs/en/cloud-environments, accessed 2026-10-11). The `worktree.baseRef` setting would face the same limit. Hooks are not adopted at Gate 0.
- **Branch deletion is blocked by the proxy.** `agent/*` branches cannot be deleted from a cloud session; Pat deletes merged ones from the GitHub UI if they pile up. Lock release deletes a file, not a branch, so it is unaffected.
- **Push scope.** The proxy does not limit which branches a push updates (docs, line quoted in finding 6). The limit to the session branch comes from this session's instructions, not the proxy. Logged as D-09.
- **Still UNVERIFIED:** the `cwd` a SubagentStop hook receives for a worktree subagent; whether a worktree holding only commits is kept at finish. Both get tested with the first implementer at M0, in a single-repository session.

### Orchestrator decisions from this research
1. **Worktree start procedure** (works whatever `baseRef` is): an implementer, test-writer, reviewer, or ui-auditor runs `git fetch origin`, then `git checkout -B agent/<task-id> <commit>` before touching anything, where `<commit>` is the task card's base commit, or `origin/agent/<task-id>` when resuming. Written into `.claude/agents/`.
2. **Commits from agents push their own branch** (`git push -u origin agent/<task-id>`), so the work survives worktree cleanup and VM expiry.
3. **Hooks deferred to Gate 3.** The scan script does not exist before M0, and hooks do not load in multi-repo sessions. The CI placeholder scan is the authoritative check; hooks would be a local convenience on top.
4. **Recommendation to Pat:** start future gauntlet sessions with only Command-Library attached, so repo settings and hooks load.

## Recommendation

- Treat the subagent file as the documented 4-core-field shape plus `isolation: worktree` and `maxTurns`; do not rely on undocumented fields (unknown fields are silently ignored, so typos fail silently). Validate the directory with `claude plugin validate .claude/agents` and run once with `--debug` to confirm the agent loaded.
- Do not assume subagents are leaf nodes. On a current binary (2.1.219+) they can nest three layers deep. If the gauntlet needs a flat fan-out, set `CLAUDE_CODE_MAX_SUBAGENT_SPAWN_DEPTH=1` in the settings `env` block and also list tools explicitly without `Agent` (or set `disallowedTools: Agent`).
- If subagent worktrees must start from in-progress work, set `worktree.baseRef: "head"` in settings; otherwise they start from the remote default branch and will not contain the parent's unpushed commits.
- Put enforcement hooks in `.claude/settings.json` rather than subagent frontmatter, because settings hooks apply to all subagents, get automatic trust in `-p` runs, and (in cloud) load for single-repository sessions. Use `onFailure: "block"` on policy hooks so a broken script fails closed.
- For the SubagentStop gate, run the empirical test first to learn what `cwd` the hook receives for a worktree subagent; if it is the main directory, derive the worktree path another way (for example the `worktreePath` returned in the Agent result, or `git worktree list --porcelain`).
- For cloud runs, commit `.claude/agents/` and `.claude/settings.json` to the repository; user-level files do not exist there. Plan for background work being lost if the VM expires.

## Risks

- Subagent worktree base is the remote default branch by default, so a subagent can silently miss the parent's unpushed work. Mitigation: `worktree.baseRef: "head"` or push first.
- SubagentStop gate depends on an UNVERIFIED `cwd` semantic and may inspect the wrong directory (false block or false pass).
- Blocking a SubagentStop can loop; `stop_hook_active` and the 8-continuation cap exist, but a subagent that cannot commit (for example a failing pre-commit scan) can burn turns until the cap.
- Exit 1 is NOT a block; only exit 2 (or JSON `decision`/`permissionDecision`) blocks. A timed-out or crashing hook lets the action proceed unless `onFailure: "block"` (v2.1.295+) is set.
- The `if` filter and Bash rules match command text only; alternate spellings of `git commit` bypass them. Not a security boundary.
- Background subagents lose every built-in tool outside the kept list (the page names the task and cron tools as extras that only agent-team teammates keep, so they are inferred removed for ordinary background subagents) and surface permission prompts in the main session; in headless runs the handling of an unanswerable prompt is UNVERIFIED.
- Worktree cleanup is partly automatic and partly by sweep: kept worktrees consume disk and hold a git lock while the agent runs; leftover locked worktrees may need `git worktree unlock` then `git worktree remove`.
- Cloud: user-level agents and hooks do not exist there; multi-repo cloud sessions load no repo hooks; the GitHub proxy blocks tag pushes, branch deletions, and GraphQL (`gh pr`, `gh issue`); background subagents do not survive VM expiry; worktree behavior in cloud sessions is UNVERIFIED.
- Docs move quickly (behavior changed at v2.1.172, v2.1.217, v2.1.219, v2.1.251, v2.1.295). Any statement here is tied to the docs as fetched on 2026-10-11 and the 2.1.296 local binary.

## Confidence

- High: subagent file location, format, frontmatter table, `model` values, `tools` syntax and omit behavior; hook locations, event list, JSON shape, matcher syntax, exit-2 blocking semantics per event, hooks in subagent frontmatter; default nesting depth of three; background subagent tool filtering; cloud GitHub proxy push restrictions. These are stated directly on the fetched pages.
- Medium: `isolation: worktree` base branch and cleanup (stated, but the commit-only retention edge and the per-subagent branch name are not); applicability of the 8-continuation cap to SubagentStop; behavior of `if: "Bash(git commit *)"` against unusual commit spellings (inferred from the `git push` analogue).
- Low / UNVERIFIED: `cwd` of a SubagentStop hook for a worktree subagent; subagent worktree branch name and directory name; worktree support inside cloud sessions; unanswerable permission prompts for background subagents under `-p`. These require the empirical step.
