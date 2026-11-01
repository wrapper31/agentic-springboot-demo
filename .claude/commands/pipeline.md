---
description: Run the requirement-to-merge agent pipeline locally for one GitHub issue, with human approval gates
argument-hint: <issue-number>
---

Run the delivery pipeline for GitHub issue #$ARGUMENTS of this repository. You are the orchestrator:
you hand each stage to its subagent, check the stage's output, and stop at the two human gates.
Do not do a stage's work yourself.

Keep a checklist of the stages below and update it as you go.

## Stage 0: Requirement

Read the issue: `gh issue view $ARGUMENTS`. If it has no acceptance criteria or expected behaviour,
stop and tell me what is missing instead of guessing. Create a branch `agent/issue-$ARGUMENTS` from
an up-to-date `main`.

## Stage 1: Tech design (subagent: architect)

Give the architect the issue number, title and body. When it returns, read the design file it wrote
and check it has every section, numbered acceptance criteria and a test plan per criterion.
Commit it: `docs: design for #$ARGUMENTS`.

**Human gate 1: design approval.** Show me the summary, the acceptance criteria and the path to the
design file, then stop and wait. Continue only if I approve. If I ask for changes, send them back to
the architect and repeat this gate.

## Stage 2: Code (subagent: developer)

Give the developer the design path. When it returns, run `mvn -B -q compile` yourself to confirm.
Commit: `feat: <issue title> (#$ARGUMENTS)`.

## Stage 3: Unit tests and coverage (subagent: test-engineer)

Give the test-engineer the design path. It must finish with `mvn -B verify` green, which includes
the ArchUnit rules and the JaCoCo coverage gate. Confirm by running `mvn -B verify` yourself and
`python3 scripts/coverage-summary.py`. If a test exposed a bug in main code, send the failing test
and the report to the developer, then back to the test-engineer. At most two rounds; then stop and
ask me. Commit: `test: cover #$ARGUMENTS`.

## Stage 4: Code review (subagent: code-reviewer)

Give the reviewer the design path. If the verdict is `REQUEST_CHANGES`, send the blocking findings to
the developer (and test-engineer if tests are affected), re-run Stage 3's checks, and review again.
At most two review rounds; then stop and ask me.

## Stage 5: Pull request

Push the branch and open a PR to `main` titled with the issue title. Body: `Closes #$ARGUMENTS`,
the design summary and link, the AC-to-test table, the coverage table, and the reviewer's summary
and verdict. Post the reviewer's findings as a PR comment.

**Human gate 2: merge approval.** Give me the PR link and stop. Do not merge, approve, or enable
auto-merge. I review and merge on GitHub, where branch protection requires CI to pass.
