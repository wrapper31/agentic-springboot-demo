---
name: Feature requirement (agent pipeline)
about: A requirement written so the agent pipeline can design, build, test and review it
title: ''
labels: ''
---

<!-- Add the `agent-pipeline` label when this is ready for the agents to pick up. -->

## What and why

<!-- The behaviour wanted, in business terms, and who needs it. -->

## Expected behaviour

<!-- Be concrete. These become the acceptance criteria. -->
- API: method, path, request, success status and body, error statuses
- State: which order statuses this applies to, and the new status
- Events: what is published to `orders.events` (or consumed), with which payload
- Audit: what the DynamoDB history should show

## Done means

- [ ] Tests at every layer touched (service unit test, `@WebMvcTest`, integration test for Kafka/state changes)
- [ ] `mvn -B verify` green, including ArchUnit rules and the JaCoCo coverage gate
- [ ] README API / Kafka tables updated

## Out of scope

<!-- What the agents should not do. -->
