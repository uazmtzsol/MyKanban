#!/usr/bin/env bash
# ============================================================
#  Personal Kanban - portable launcher for Unix/Linux/macOS
#  Looks for a Java 21+ runtime next to this script (jre/),
#  else falls back to java on PATH. All data (SQLite database,
#  undo history, settings) stays in data/ on this drive.
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="$SCRIPT_DIR/data"

if [ -x "$SCRIPT_DIR/jre/bin/java" ]; then
    JAVA_CMD="$SCRIPT_DIR/jre/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_CMD="java"
else
    echo "Personal Kanban needs a Java 21+ runtime." >&2
    echo "Install one (e.g. apt install openjdk-21-jre) or place a JDK at ./jre/." >&2
    exit 1
fi

mkdir -p "$DATA_DIR"

exec "$JAVA_CMD" -Dpk.data.dir="$DATA_DIR" -jar "$SCRIPT_DIR/personal-kanban.jar" "$@"
