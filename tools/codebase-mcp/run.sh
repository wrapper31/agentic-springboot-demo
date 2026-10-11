#!/bin/bash
# Launches the codebase MCP server over stdio, building it first if needed.
# Usage: tools/codebase-mcp/run.sh [path-to-codebase]   (defaults to the current directory)
# stdout is the MCP channel, so all build output goes to stderr.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
jar="$here/target/codebase-mcp.jar"
root="${1:-$PWD}"

if [ ! -f "$jar" ] || [ -n "$(find "$here/src" "$here/pom.xml" -newer "$jar" -print -quit)" ]; then
  mvn -B -q -f "$here/pom.xml" package -DskipTests 1>&2
fi

exec java -jar "$jar" "--codebase.root=$root"
