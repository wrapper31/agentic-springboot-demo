#!/bin/bash
# Posts the code-reviewer agent's structured review (JSON in $REVIEW) as a PR comment.
# A comment rather than a GitHub review: the workflow token opened the PR and cannot review its own PR,
# and the approval that counts is the human's on the merge-approval environment.
# Usage: REVIEW='{"verdict":...}' scripts/post-review.sh <pr-number> <round>
set -euo pipefail

pr="${1:?pr number}"
round="${2:-1}"
: "${REVIEW:?REVIEW must hold the reviewer JSON}"
out="${RUNNER_TEMP:-/tmp}/review-$round.md"

{
  echo "### AI code review, round $round: \`$(jq -r .verdict <<< "$REVIEW")\`"
  echo
  jq -r .summary <<< "$REVIEW"
  echo
  if [ "$(jq '.findings | length' <<< "$REVIEW")" -gt 0 ]; then
    echo "| Severity | Where | Finding |"
    echo "|---|---|---|"
    jq -r '.findings[] | "| \(.severity) | `\(.file)\(if .line then ":\(.line)" else "" end)` | \(.comment | gsub("\\|"; "\\|") | gsub("\n"; " ")) |"' <<< "$REVIEW"
  else
    echo "No findings."
  fi
} > "$out"

gh pr comment "$pr" --body-file "$out"
cat "$out" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
