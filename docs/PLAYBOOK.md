# Playbook: bringing coding agents to restricted Spring Boot services

This repository is the pilot. It shows, on a small but realistically layered service, everything a
team needs to let an AI coding agent work safely in a complex Spring Boot microservice. This playbook
is how to repeat it on your real, access-restricted services.

## What the pilot proved

| Asset | Where | Why it matters |
|---|---|---|
| Project instructions | [`CLAUDE.md`](../CLAUDE.md) | The agent learns the architecture, layering rules, conventions and test commands up front instead of guessing from code. |
| Executable architecture rules | [`ArchitectureTest`](../src/test/java/com/example/orders/ArchitectureTest.java) | Layering is enforced by the build, so an agent (or a person) cannot quietly call a repository from a controller. It caught a real leak while it was being written. |
| Fast, realistic test loop | Testcontainers + LocalStack | MongoDB, Kafka and DynamoDB run for real in tests, so "tests pass" means something. |
| Codebase MCP server | [`tools/codebase-mcp`](../tools/codebase-mcp/README.md) | The agent asks for endpoints, Kafka topology, data models and type usages instead of reading every file. Read-only, no network, no LLM. |
| Session bootstrap | [`.claude/hooks/session-start.sh`](../.claude/hooks/session-start.sh) | Cloud agent sessions start with Docker running and dependencies cached. |
| End-to-end task | [Issue #3](https://github.com/wrapper31/agentic-springboot-demo/issues/3) → [PR #4](https://github.com/wrapper31/agentic-springboot-demo/pull/4) | A feature touching every layer (REST, service, MongoDB, DynamoDB audit, Kafka event) implemented from an issue, with tests at each layer and CI green. |

## Rollout, one service at a time

### 0. Decide where the agent runs (with your security team)

Restricted code usually means the agent runs where the code already is: on a developer's machine
or an approved build host, using Claude Code locally. Settle these before anything else:

- **Model access path.** Direct Anthropic API under your enterprise agreement, or Claude through
  Amazon Bedrock / Google Vertex AI if data must stay in your cloud account. Claude Code supports all three.
- **What the agent may do.** Commit a `.claude/settings.json` with permission rules: allow build and
  test commands, deny reading secrets (`.env`, credential files), deny pushing to protected branches,
  deny anything that touches production. Developers approve everything else interactively.
- **What leaves the machine.** File contents the agent reads go to the model. The codebase MCP server
  itself sends nothing anywhere.

### 1. Make the service agent-ready (about a day)

1. Run `/init` in Claude Code to draft `CLAUDE.md`, then edit it by hand. The valuable parts are the
   ones only your team knows: which layer may call which, how state changes must flow, naming and
   error-handling conventions, and the exact commands for a fast test loop. Use this repo's file as the template.
2. Encode the layering as ArchUnit rules. Start with what is already true (run the rules, fix or
   explicitly allow existing violations), then let the build hold the line.
3. Make sure one command runs meaningful tests without shared environments: Testcontainers for
   MongoDB and Kafka, LocalStack for DynamoDB. Agents iterate on test output; slow or flaky loops
   are the biggest drag on agent quality.
4. Add the SessionStart hook if you will use cloud sessions.

### 2. Point the codebase MCP server at it

```bash
mvn -B -f tools/codebase-mcp/pom.xml package
claude mcp add codebase -- /path/to/agentic-springboot-demo/tools/codebase-mcp/run.sh /path/to/your-service
```

Check the answers against what you know (`project_overview`, `list_kafka_topics`). If your services
use patterns the server does not know yet, extend it; good next tools for enterprise estates are
Feign/WebClient outbound calls (a service-to-service map), `@Scheduled` jobs, `@Transactional`
boundaries and per-endpoint security rules. For cross-service questions, point `--codebase.root`
at a folder containing several service checkouts: every `src/main/java` is scanned.

Publish the jar to your internal artifact repository so every team uses the same version.

### 3. Climb the task ladder

Start where mistakes are cheap and review is easy, and move up as the numbers justify it:

1. **Questions**: "where is X handled", "what publishes to topic Y", onboarding walkthroughs.
2. **Tests**: add missing tests for existing behaviour.
3. **Small fixes**: bugs with a clear reproduction.
4. **Small features**: one service, every layer, like issue #3.
5. **Cross-service changes**: a new event and its consumers, an API version bump.

Write issues the way #3 is written: the behaviour wanted, the status codes and events expected, and
what "done" includes (tests, README). Acceptance criteria are what make agent output reviewable.

### 4. Measure, then scale

Track per service: share of agent PRs merged, review comments per PR, time from issue to merged PR,
and CI failures caused by agent changes. When a service looks good, copy its `CLAUDE.md` shape and
ArchUnit rules to the next one, and keep a shared template. Review is unchanged: agent PRs go
through the same CODEOWNERS and CI as anyone else's.

## Two separate tracks

This pilot is about agents that **help developers build** the services. Putting agentic features
**inside** the services (a Spring Boot endpoint that calls an LLM with tools over your domain) is a
different track: there, Spring AI or LangChain4j inside the service, calling your existing service
layer as tools, is the natural fit. The same MCP pattern applies, so tools you build once can serve
both tracks.

## Checklist per service

- [ ] Model access path and data handling approved
- [ ] `.claude/settings.json` permission rules committed
- [ ] `CLAUDE.md` with layering, conventions and test commands
- [ ] ArchUnit layering rules in the build
- [ ] One-command test loop with Testcontainers / LocalStack
- [ ] Codebase MCP server registered and spot-checked
- [ ] First tasks chosen from the bottom of the ladder, with acceptance criteria
- [ ] Metrics tracked from the first agent PR
