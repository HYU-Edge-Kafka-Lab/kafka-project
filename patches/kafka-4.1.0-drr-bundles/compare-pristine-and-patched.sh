#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRISTINE_ROOT="${1:-}"
PATCHED_ROOT="${2:-}"

usage() {
  cat <<'EOF'
Usage: compare-pristine-and-patched.sh <pristine-kafka-root> <patched-kafka-root>

Runs a comparison in three phases:
1. Comparable baseline suite on pristine Kafka 4.1.0
2. The same comparable suite on the DRR-patched tree
3. Patch-only verification suite on the DRR-patched tree

If phase 1 fails, the script stops because the environment cannot provide a trustworthy baseline.
EOF
}

if [[ -z "$PRISTINE_ROOT" || -z "$PATCHED_ROOT" ]]; then
  usage
  exit 1
fi

for root in "$PRISTINE_ROOT" "$PATCHED_ROOT"; do
  if [[ ! -d "$root" ]]; then
    echo "Kafka source root not found: $root"
    exit 1
  fi
  if [[ ! -x "$root/gradlew" ]]; then
    echo "gradlew not found or not executable at: $root/gradlew"
    exit 1
  fi
done

GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/gradle-home}"

run_gradle() {
  local root="$1"
  local label="$2"
  shift 2

  echo
  echo "[RUN] $label"
  echo "      root=$root"
  echo "      task=$*"
  (
    cd "$root"
    GRADLE_USER_HOME="$GRADLE_USER_HOME" ./gradlew "$@" --no-daemon
  )
}

run_comparable_suite() {
  local root="$1"
  local label_prefix="$2"

  run_gradle "$root" "$label_prefix comparable compile" \
    :clients:compileJava \
    :server:compileJava \
    :core:compileScala

  run_gradle "$root" "$label_prefix comparable selector" \
    :clients:test --tests org.apache.kafka.common.network.SelectorTest

  run_gradle "$root" "$label_prefix comparable socket-server connection-id" \
    :core:test --tests kafka.network.SocketServerTest.testConnectionIdReuse

  run_gradle "$root" "$label_prefix comparable socket-server buffered-close" \
    :core:test --tests kafka.network.SocketServerTest.remoteCloseWithBufferedReceivesFailedSend
}

run_patch_only_suite() {
  local root="$1"
  local label_prefix="$2"

  run_gradle "$root" "$label_prefix patch-only selector scheduling" \
    :clients:test --tests org.apache.kafka.common.network.SelectorSchedulingPolicyTest

  run_gradle "$root" "$label_prefix patch-only socket-server configs" \
    :server:test --tests org.apache.kafka.network.SocketServerConfigsTest
}

echo "[PHASE 1] Establishing pristine baseline"
if ! run_comparable_suite "$PRISTINE_ROOT" "pristine"; then
  echo
  echo "[FAIL] Pristine baseline suite failed."
  echo "       Stop here. Do not treat patched failures as regressions until the pristine baseline passes."
  exit 2
fi

echo
echo "[PHASE 2] Running the same comparable suite on the patched tree"
if ! run_comparable_suite "$PATCHED_ROOT" "patched"; then
  echo
  echo "[FAIL] Comparable suite passed on pristine but failed on the patched tree."
  echo "       This indicates a likely DRR regression."
  exit 3
fi

echo
echo "[PHASE 3] Running patch-only verification on the patched tree"
if ! run_patch_only_suite "$PATCHED_ROOT" "patched"; then
  echo
  echo "[FAIL] Patch-only verification failed on the patched tree."
  exit 4
fi

echo
echo "[DONE] Pristine baseline passed, patched comparable suite matched it, and patch-only checks passed."
echo "       Use this result set as the trusted comparison point for further DRR iterations."
