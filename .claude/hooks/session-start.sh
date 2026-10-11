#!/bin/bash
# Prepares a Claude Code cloud session: Docker for Testcontainers, Maven dependencies cached.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

# Integration tests start MongoDB, Kafka and LocalStack via Testcontainers, which needs a Docker daemon.
if ! docker info >/dev/null 2>&1; then
  if command -v dockerd >/dev/null 2>&1; then
    (sudo -n dockerd >/tmp/dockerd.log 2>&1 &) || true
    for _ in $(seq 1 30); do
      docker info >/dev/null 2>&1 && break
      sudo -n chmod 666 /var/run/docker.sock 2>/dev/null || true
      sleep 1
    done
  fi
fi
sudo -n chmod 666 /var/run/docker.sock 2>/dev/null || true
if docker info >/dev/null 2>&1; then
  echo "Docker is running"
else
  echo "Docker is not available; OrderFlowIntegrationTest will fail. Run: mvn -B test -Dtest='!OrderFlowIntegrationTest'" >&2
fi

# Download all plugins and dependencies and compile, so later builds work from the cached ~/.m2.
mvn -B -q dependency:resolve-plugins dependency:resolve test-compile

# Pre-pull the Testcontainers images so the first integration test run is fast.
if docker info >/dev/null 2>&1; then
  for image in mongo:7.0 apache/kafka:3.8.1 localstack/localstack:3.8 testcontainers/ryuk:0.12.0; do
    docker pull -q "$image" >/dev/null 2>&1 || echo "Could not pre-pull $image" >&2
  done
fi
