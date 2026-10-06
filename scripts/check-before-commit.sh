#!/usr/bin/env bash
# Business-independent checks; no service startup or database writes.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -n "${MAVEN_HOME:-}" ]]; then
  maven_command="$MAVEN_HOME/bin/mvn"
elif command -v mvn >/dev/null 2>&1; then
  maven_command="$(command -v mvn)"
else
  maven_command="/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn"
fi
if [[ ! -x "$maven_command" ]]; then
  echo 'FAIL: Maven not found; set MAVEN_HOME or install Maven.' >&2
  exit 1
fi

echo '[1/3] Whitespace/conflict checks'
git diff --check
git diff --cached --check
echo '[2/3] Controller routes and API documentation'
python3 scripts/check-api-docs.py
echo '[3/3] Clean compile, unit tests and executable package'
"$maven_command" --batch-mode clean verify -DskipTests=false -Dmaven.test.skip=false -DfailIfNoTests=true
echo 'PASS: pre-commit checks completed.'
