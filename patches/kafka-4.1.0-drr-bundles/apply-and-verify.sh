#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATCH_DIR="$SCRIPT_DIR"
KAFKA_SRC_ROOT="${1:-}"
VERIFY_MODE="${2:-}"

if [[ -z "$KAFKA_SRC_ROOT" ]]; then
  echo "Usage: $0 <kafka-source-root> [--no-verify]"
  exit 1
fi

if [[ ! -d "$KAFKA_SRC_ROOT" ]]; then
  echo "Kafka source root not found: $KAFKA_SRC_ROOT"
  exit 1
fi

if [[ "$VERIFY_MODE" != "" && "$VERIFY_MODE" != "--no-verify" ]]; then
  echo "Unknown option: $VERIFY_MODE"
  echo "Usage: $0 <kafka-source-root> [--no-verify]"
  exit 1
fi

cd "$KAFKA_SRC_ROOT"

for patch_file in "$PATCH_DIR"/0*.patch; do
  echo "[CHECK] $(basename "$patch_file")"
  git apply --check --recount "$patch_file"
done

for patch_file in "$PATCH_DIR"/0*.patch; do
  echo "[APPLY] $(basename "$patch_file")"
  git apply --recount "$patch_file"
done

if [[ "$VERIFY_MODE" == "--no-verify" ]]; then
  echo "[DONE] Patch applied without verification."
  exit 0
fi

if [[ ! -x "./gradlew" ]]; then
  echo "gradlew not found or not executable at: $KAFKA_SRC_ROOT/gradlew"
  exit 1
fi

echo "[VERIFY] Running compile and targeted tests..."
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/gradle-home}" ./gradlew \
  :clients:compileJava \
  :server:compileJava \
  :core:compileScala \
  :clients:test --tests org.apache.kafka.common.network.SelectorSchedulingPolicyTest \
  :server:test --tests org.apache.kafka.network.SocketServerConfigsTest \
  --no-daemon

echo "[DONE] Patch applied and verification passed."
