# Lesson: an agent pipeline from requirement to merge

In this lesson you wire up a team of AI agents that takes a requirement (a GitHub issue) and delivers
it as merged code: tech design, implementation, unit tests with a coverage gate, code review, human
approval, merge. You use `order-service` (REST, Kafka, MongoDB, DynamoDB) as the worked example, so
everything you see is the shape of a real enterprise microservice.

You will run the same pipeline two ways:

- **Locally**, inside a Claude Code session, with `/pipeline <issue>`. Best for learning: you watch
  every hand-off and approve each gate in the chat.
- **In GitHub Actions**, by labelling an issue. Best for teams: the gates are GitHub Environment
  approvals and every step leaves an audit trail.

Time: about 2 hours. You need Java 21, Maven, Docker, the GitHub CLI and Claude Code.

---

## 1. The pipeline at a glance

```
 GitHub issue (requirement)
        │
        ▼
 ┌──────────────┐   docs/designs/issue-N-*.md
 │ 1. architect │──────────────────────────────┐
 └──────────────┘                              ▼
                                   ╔══════════════════════╗
                                   ║ HUMAN GATE 1: design ║
                                   ╚══════════════════════╝
        ┌──────────────────────────────────────┘
        ▼
 ┌──────────────┐  src/main/**     ┌──────────────────┐  src/test/**
 │ 2. developer │─────────────────▶│ 3. test-engineer │──────────────┐
 └──────────────┘                  └──────────────────┘              ▼
                                           ┌─────────────────────────────────────────┐
                                           │ mvn -B verify  (deterministic gate)      │
                                           │ unit + @WebMvcTest + ArchUnit +          │
                                           │ Testcontainers + JaCoCo coverage limits  │
                                           └─────────────────────────────────────────┘
                                                             │ pull request
                                                             ▼
                          fix round ◀── REQUEST_CHANGES ┌──────────────────┐
                        (developer +                    │ 4. code-reviewer │ read-only,
                         test-engineer, max 1)  ──────▶ │   (JSON verdict) │ structured output
                                                        └──────────────────┘
                                                             │ APPROVE
                                                             ▼
                                                  ╔═════════════════════╗
                                                  ║ HUMAN GATE 2: merge ║
                                                  ╚═════════════════════╝
                                                             │
                                                             ▼
                                                     squash-merge to main
```

| Stage | Who | Writes | Done when |
|---|---|---|---|
| Requirement | human | GitHub issue ([template](../.github/ISSUE_TEMPLATE/feature-requirement.md)) | it has concrete expected behaviour |
| Tech design | [`architect`](../.claude/agents/architect.md) | `docs/designs/` only | design approved by a human |
| Code | [`developer`](../.claude/agents/developer.md) | `src/main/**`, `README.md` | compiles, existing tests pass |
| Unit tests + coverage | [`test-engineer`](../.claude/agents/test-engineer.md) | `src/test/**` | every acceptance criterion has a test, `mvn -B verify` green |
| Code review | [`code-reviewer`](../.claude/agents/code-reviewer.md) | nothing (read-only) | verdict `APPROVE` |
| Approve and merge | human | the merge | branch protection satisfied |

## 2. Five ideas that make this work

Read these before the labs; every file in the pipeline exists because of one of them.

1. **One agent, one job, one lane.** Each agent has a short definition in `.claude/agents/` with its
   own instructions and its own tool list. The reviewer has no `Edit` or `Write`, so it cannot "fix
   while reviewing" and blur accountability. The developer may not touch tests, so the tests are
   written against the design, not against the code.
2. **Instructions are requests; checks are rules.** Agents are told to stay in their lane, and
   `scripts/pipeline-guard.sh` then fails the run if they did not. They are told not to lower the
   coverage limits, and the guard protects `pom.xml`. Tell, then verify.
3. **Deterministic gates carry the weight.** `mvn -B verify` runs unit tests, web-layer tests,
   the ArchUnit layering rules (`ArchitectureTest`), Testcontainers integration tests against real
   MongoDB, Kafka and DynamoDB (LocalStack), and the JaCoCo coverage check. An agent cannot argue
   with a red build. The AI review is for what tests do not catch: design drift, missing edge cases,
   compatibility.
4. **Hand-offs are artifacts, not chat.** The design is a committed file; the review is JSON validated
   against a schema (`--json-schema`), so the workflow can branch on `verdict` instead of parsing
   prose. A later stage, or a human, reads exactly what the earlier stage produced.
5. **Humans own the irreversible decisions.** Agents never approve or merge. The two gates are the
   two points where a wrong call is expensive: before code is written (cheap to change direction) and
   before it reaches `main`.

---

## Lab 0: put a coverage gate on the build (15 min)

The pipeline can only enforce coverage if the build does. This repo now has JaCoCo in `pom.xml`:

```xml
<coverage.line.minimum>0.90</coverage.line.minimum>
<coverage.branch.minimum>0.60</coverage.branch.minimum>
```

The `check` goal runs in `verify` and fails the build below those ratios. They were set at the
**measured baseline** (93.4% lines, 60.0% branches when this lesson was written), which is the right
way to introduce a gate on an existing service: it stops coverage getting worse without blocking
every build on day one. You then ratchet it up.

```bash
mvn -B verify
python3 scripts/coverage-summary.py      # per-package table, the same one the pipeline posts on PRs
open target/site/jacoco/index.html       # red lines = never executed by any test
```

**Exercise 0.** Branch coverage is weak in `service` and `config`. Let the test-engineer agent fix it,
then ratchet the gate:

```bash
claude "Use the test-engineer subagent to cover the untested branches in OrderService and \
OrderController (failed payment, cancel happy path, findByCustomer, 409 mapping). No design file: \
the existing behaviour is the spec."
mvn -B verify && python3 scripts/coverage-summary.py
# raise coverage.branch.minimum in pom.xml to just under the new number, commit both together
```

Notice the agent has the `Bash` tool but its instructions forbid touching `pom.xml`. You raise the
limit yourself: the people who own the gate are the people who move it.

---

## Lab 1: read the agents (15 min)

Open the four files in `.claude/agents/`. For each, find:

- the **frontmatter**: `name`, `description` (how the orchestrator decides when to use it) and
  `tools` (least privilege; the architect and developer get the read-only codebase MCP tools from
  `tools/codebase-mcp`, the test-engineer does not need them, the reviewer gets no write tools);
- the **inputs** it expects (issue, design file, diff);
- the **rules** that keep it in its lane;
- the **finish line**: what it must report back. A crisp finish line is what lets the orchestrator
  verify a stage instead of trusting it.

Then read `.claude/commands/pipeline.md`. It is the orchestrator: a prompt that calls the agents in
order, checks each output, and stops at the two gates. It does no stage work itself.

**Exercise 1.** Ask Claude Code: `What can the code-reviewer agent not do, and why is that useful?`
Then try `Use the code-reviewer subagent to fix the typo in README.md`. It should refuse or report
that it cannot edit files. That is the lane working.

---

## Lab 2: run the pipeline locally (45 min)

### 2.1 Write the requirement

Create an issue with the [feature requirement template](../.github/ISSUE_TEMPLATE/feature-requirement.md).
The worked example used throughout this lesson:

> **Refund a paid order**
>
> Customer support needs to refund orders that were paid but cannot be fulfilled.
>
> Expected behaviour
> - API: `POST /api/orders/{id}/refund` with body `{"reason": "..."}` (reason required, max 200
>   chars). `200` with the order on success; `404` unknown order; `409` if the order is not `PAID`;
>   `400` if reason is missing.
> - State: `PAID -> REFUNDED`. No other status can be refunded.
> - Events: `ORDER_STATUS_CHANGED` on `orders.events` with status `REFUNDED`.
> - Audit: a `REFUNDED` row in the DynamoDB history with the reason as detail.
>
> Done means: tests at every layer, `mvn -B verify` green with the coverage gate, README tables updated.
>
> Out of scope: calling a payment provider; partial refunds.

```bash
gh issue create --title "Refund a paid order" --body-file refund.md
```

Why this requirement: it touches every layer (API, service rule, a new status, MongoDB, the DynamoDB
audit, a Kafka event) and has unhappy paths, so each stage has real work and real ways to go wrong.

### 2.2 Run it

```bash
claude
> /pipeline <issue-number>
```

What to watch for at each stage:

| Stage | What good looks like | Common failure and what the pipeline does |
|---|---|---|
| Architect | ACs like "AC-3: refunding a `CREATED` order returns 409 and publishes nothing"; reuses `transition(...)`; notes existing Mongo documents still load | Vague ACs → you reject at gate 1 with specifics ("add the 400 case") |
| **Gate 1** | You read ~1 page and approve | Asking for changes here costs minutes, not a rewrite |
| Developer | `REFUNDED` appended to `OrderStatus`, `refund()` in `OrderService` guarded on `PAID`, `RefundOrderRequest` DTO with `@NotBlank @Size(max=200)`, `ApiExceptionHandler` unchanged (409 already mapped) | Calls a repository from the controller → `ArchitectureTest` fails the build |
| Test engineer | `OrderServiceTest`: refund paid, reject created/cancelled; `OrderControllerTest`: 200/400/404/409; integration test: paid → refund → audit row + Kafka event | Coverage below the gate → `verify` fails, it adds tests; finds a bug → reports it back to the developer |
| Reviewer | Findings with file and line, e.g. "refund reason not trimmed before audit" (suggestion) | A blocking finding → one fix round, then re-review |
| **Gate 2** | PR shows design link, AC-to-test table, coverage table, review summary; CI green | You merge on GitHub, or comment and send it back |

**Exercise 2.** Run it a second time with a deliberately vague issue ("Allow refunds"). See where the
pipeline stops (stage 0 should ask for expected behaviour) and how the design differs when it does
not. This is the most important lesson about agents: output quality is bounded by requirement quality.

---

## Lab 3: run it in GitHub Actions (30 min)

The workflow `.github/workflows/agent-pipeline.yml` runs the same agents with
[`anthropics/claude-code-action@v1`](https://github.com/anthropics/claude-code-action), one job per
stage group:

| Job | Stages | Gate |
|---|---|---|
| `design` | capture issue, architect, commit design to `agent/issue-N`, comment on the issue | |
| `build` | developer, guard, test-engineer, guard, `mvn -B verify`, coverage summary, open PR | `design-approval` environment (gate 1) |
| `review` | reviewer with `--json-schema`, post review, one fix round if `REQUEST_CHANGES`, re-review | fails if still `REQUEST_CHANGES` |
| `merge` | `gh pr merge --squash --delete-branch` | `merge-approval` environment (gate 2) |

### 3.1 One-time setup (repository settings)

1. **API key.** Settings → Secrets and variables → Actions → New secret `ANTHROPIC_API_KEY`.
   (Enterprise: use Amazon Bedrock or Google Vertex AI with OIDC instead; the action supports
   `use_bedrock` / `use_vertex`, so code never leaves your cloud account.)
2. **Claude GitHub App** installed on the repo (already done for this pilot), so the action can
   authenticate.
3. **Human gates.** Settings → Environments → create `design-approval` and `merge-approval`. For
   each: tick *Required reviewers* and add yourself (or your tech-lead group). Optionally tick
   *Prevent self-review*.
4. **Let the workflow open PRs.** Settings → Actions → General → Workflow permissions → *Read and
   write* and tick *Allow GitHub Actions to create and approve pull requests*.
5. **Label.** Create a label `agent-pipeline`.
6. **Branch protection on `main`** (recommended): require a pull request and the `build` check from the CI workflow.
   See 3.4 for the token detail this needs.

### 3.2 Run

Add the `agent-pipeline` label to the refund issue (or Actions → Agent pipeline → Run workflow with
the issue number).

1. The `design` job comments on the issue with a link to the design. Read it.
2. The run pauses: *Review deployments* → `design-approval` → **Approve** (or Reject and comment).
3. `build` runs the developer and test-engineer, then `mvn -B verify`. Open the run summary for the
   coverage table; the HTML report is attached as the `jacoco-report` artifact.
4. A PR appears. `review` posts the AI review as a comment, fixes blocking findings once if needed.
5. The run pauses again at `merge-approval`. Review the PR like any other, then **Approve** to merge.

### 3.3 Why the review is a comment, not a GitHub review

The workflow token opened the PR, and GitHub does not let an author review its own PR. That is fine:
the AI verdict is advice and a pipeline switch, while the approval that counts is the human one on
`merge-approval`, recorded in the deployment history with the approver's name.

### 3.4 Production hardening

- **CI on agent PRs.** PRs opened with the default `GITHUB_TOKEN` do not trigger other workflows, so
  `ci.yml` will not run on them by itself and a required `build` check will block the merge.
  For a team setup, create a dedicated GitHub App, mint a token with
  `actions/create-github-app-token`, and use it for checkout, `gh pr create` and `gh pr merge`. Then
  CI runs as for any other PR and branch protection applies unchanged.
- **Prompt injection.** The issue body is untrusted input. The workflow writes it to a file instead of
  interpolating it into shell commands, the prompt tells the agent to treat it as a requirement only,
  and the tool lists are narrow (no network tools, Bash limited to `mvn` and `git` read commands).
  Only people who can add labels (triage access) can start the pipeline.
- **Cost and runaway control.** `--max-turns` per stage, `timeout-minutes` per job, one fix round,
  `concurrency` per issue.
- **CODEOWNERS** still apply to agent PRs. So do your normal security scans.

---

## 4. Where the controls are

| Risk | Control | File |
|---|---|---|
| Agent writes code no one asked for | ACs in an approved design; reviewer checks "nothing extra" | `architect.md`, `code-reviewer.md` |
| Agent edits outside its lane | tool lists + `pipeline-guard.sh` lanes | `.claude/agents/*`, `scripts/pipeline-guard.sh` |
| Agent weakens the gates | `pom.xml`, `ArchitectureTest`, `.github/`, `.claude/` protected by the guard | `scripts/pipeline-guard.sh` |
| Layering violations | ArchUnit rules in the build | `ArchitectureTest.java` |
| Untested code | JaCoCo `check` in `verify`; ratchet only upwards | `pom.xml` |
| "It passed on mocks" | Testcontainers: real MongoDB, Kafka, DynamoDB | `TestcontainersConfiguration.java` |
| Endless agent loops | max turns, job timeouts, one fix round, then a human | `agent-pipeline.yml` |
| Bad design reaches code | human gate 1 | `design-approval` environment |
| Bad code reaches `main` | CI + AI review + human gate 2 + branch protection | `merge-approval` environment |

## 5. Taking it to your real services

1. Make the service agent-ready first: `CLAUDE.md`, ArchUnit rules, a one-command Testcontainers
   build (see [PLAYBOOK.md](PLAYBOOK.md) if it is merged, and this repo's `CLAUDE.md` as a template).
2. Add JaCoCo at the measured baseline (Lab 0). Do not start with 80% on a legacy service.
3. Copy `.claude/agents/`, `.claude/commands/pipeline.md`, `scripts/` and the workflow. Edit the agent
   files to name your layers, conventions and test styles; that is where most of the value is.
4. Start locally with `/pipeline` on small, well-written issues. Move to the Actions pipeline once
   gate 1 approvals stop needing many changes.
5. Measure: share of pipeline PRs merged without human code changes, review rounds per PR, time from
   label to merge, and how often gate 1 rejects. These tell you whether to give the agents bigger tasks.

**Going further.** The orchestration here is a prompt (`/pipeline`) or a workflow file. When you need
custom routing, retries, or to run stages in parallel across services, move the orchestrator into
code with the Claude Agent SDK (Python or TypeScript): the same agent definitions become
`agents={...}` entries, and each gate becomes a function that waits for an approval in your own
system (Jira, ServiceNow, a Slack button).

## 6. Exercises

1. **Ratchet** (Lab 0): raise the branch coverage limit after Exercise 0 and open a PR.
2. **Break a gate on purpose**: in a local `/pipeline` run, tell the developer at gate 1 to "call the
   repository directly from the controller to save time". Watch `ArchitectureTest` stop it.
3. **Add a stage**: write a `security-reviewer` agent (read-only, checks input validation, logging of
   PII, secrets) and add it to `/pipeline` between test-engineer and code-reviewer.
4. **Kafka requirement**: write an issue that adds a consumer (e.g. `shipments.events` marks an order
   `DELIVERED`) and check the test-engineer adds an idempotency test for duplicate delivery.
5. **Tighten the reviewer**: add a rule to `code-reviewer.md` from a real review comment your team
   has made more than twice. That is how the agents learn your team's standards.
