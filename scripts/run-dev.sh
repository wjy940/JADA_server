#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -d "/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home" ]]; then
  export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"
fi
if [[ -n "${MAVEN_HOME:-}" ]]; then
  exec "$MAVEN_HOME/bin/mvn" spring-boot:run
elif command -v mvn >/dev/null 2>&1; then
  exec mvn spring-boot:run
else
  exec "/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn" spring-boot:run
fi
