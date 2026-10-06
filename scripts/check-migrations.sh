#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:=/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home}"
: "${PG_JDBC_JAR:?Set PG_JDBC_JAR to the current project PostgreSQL driver jar}"
exec "$JAVA_HOME/bin/java" --class-path "$PG_JDBC_JAR" scripts/MigrationCheck.java
