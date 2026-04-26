#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

COMPOSE_FILE="${COMPOSE_FILE:-$REPO_ROOT/docker-compose.drr-1thread.yml}" \
KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka41-drr-1thread}" \
"$SCRIPT_DIR/start-broker.sh"
