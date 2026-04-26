#!/usr/bin/env bash
set -euo pipefail

SCENARIO="${1:-}"
DURATION="${2:-}"
POLICY="${3:-${EXPERIMENT_POLICY:-default}}"

if [[ -z "$SCENARIO" || -z "$DURATION" ]]; then
  echo "Usage: ./scripts/run-scenario.sh <S0|S1|S2|S1N> <durationSeconds> [policy]"
  exit 1
fi

WARMUP_SEC="${WARMUP_SEC:-5}"

# 기본 stage 세트 (필요하면 커스텀)
# ack_received를 기본으로 두고, service_gap은 보조 지표가 필요할 때만 추가한다.
# 예: STAGES="ack_received service_gap"
STAGES="${STAGES:-ack_received}"

echo "[1/3] Running scenario: $SCENARIO for ${DURATION}s"
echo "      Policy: $POLICY"
EXPERIMENT_POLICY="$POLICY" ./gradlew -q run -Dexperiment.policy="$POLICY" --args="$SCENARIO $DURATION"

echo "[2/3] Summarizing logs to CSV (warmupSec=$WARMUP_SEC)"
# LogSummaryTool을 main으로 직접 실행하는 방식(패키지명/클래스명에 맞게 수정)
# 아래 클래스 경로는 네가 만든 위치에 맞춰야 함.
SUMMARY_MAIN="com.kafka.io.kafkaproject.analysis.metrics.LogSummaryTool"

EXPERIMENT_POLICY="$POLICY" ./gradlew -q javaexec --args="$SCENARIO $STAGES --warmupSec $WARMUP_SEC --policy $POLICY" \
  -PmainClass="$SUMMARY_MAIN" || {
    echo "⚠️ Gradle javaexec 방식이 프로젝트 설정에 없으면, IntelliJ에서 LogSummaryTool main을 args로 실행해줘."
    echo "   args: $SCENARIO $STAGES --warmupSec $WARMUP_SEC --policy $POLICY"
  }

echo "[3/3] Done."
echo "✅ results/$POLICY/$SCENARIO/summary.csv 확인"
