#!/bin/bash
# Fails the agent pipeline if a stage changed files outside its lane.
# Agent instructions say the same thing; this makes it a rule instead of a request.
# Usage: scripts/pipeline-guard.sh developer|test-engineer|fixer
set -euo pipefail

stage="${1:?stage}"
changed=$(git status --porcelain --untracked-files=all | awk '{print $NF}')
[ -z "$changed" ] && exit 0

case "$stage" in
  developer)     allowed='^(src/main/|README\.md$)' ;;
  test-engineer) allowed='^src/test/' ;;
  fixer)         allowed='^(src/main/|src/test/|README\.md$)' ;;
  *) echo "unknown stage $stage" >&2; exit 2 ;;
esac

# Files no agent may touch, whatever its lane.
protected='^(pom\.xml$|src/test/java/com/example/orders/ArchitectureTest\.java$|\.github/|\.claude/|CLAUDE\.md$|scripts/)'

status=0
while read -r f; do
  [ -z "$f" ] && continue
  case "$f" in .pipeline/*|target/*|*/target/*) continue ;; esac
  if [[ "$f" =~ $protected ]]; then
    echo "::error file=$f::$stage changed a protected file"; status=1
  elif [[ ! "$f" =~ $allowed ]]; then
    echo "::error file=$f::$stage changed a file outside its lane"; status=1
  fi
done <<< "$changed"
exit $status
