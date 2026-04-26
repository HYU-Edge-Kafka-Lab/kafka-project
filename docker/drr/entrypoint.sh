#!/usr/bin/env bash
set -euo pipefail

KAFKA_HOME="${KAFKA_HOME:-/opt/kafka}"
CONFIG_FILE="${KAFKA_CONFIG_FILE:-/etc/kafka/server.properties}"
LOG_DIR="${KAFKA_LOG_DIR:-/var/lib/kafka/data}"

mkdir -p "$LOG_DIR"

if [[ ! -f "$LOG_DIR/meta.properties" ]]; then
  CLUSTER_ID="${KAFKA_CLUSTER_ID:-$("$KAFKA_HOME/bin/kafka-storage.sh" random-uuid)}"
  echo "[kafka-drr] Formatting storage: log.dir=$LOG_DIR cluster.id=$CLUSTER_ID"
  "$KAFKA_HOME/bin/kafka-storage.sh" format --ignore-formatted -t "$CLUSTER_ID" -c "$CONFIG_FILE"
fi

echo "[kafka-drr] Starting broker with config: $CONFIG_FILE"
exec "$KAFKA_HOME/bin/kafka-server-start.sh" "$CONFIG_FILE"
