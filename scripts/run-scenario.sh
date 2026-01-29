#!/usr/bin/env bash
set -euo pipefail

SCENARIO="${1:-}"
DURATION="${2:-}"

if [[ -z "$SCENARIO" || -z "$DURATION" ]]; then
  echo "Usage: ./scripts/run-scenario.sh <S0|S1|S2|S3> <durationSeconds>"
  exit 1
fi

WARMUP_SEC="${WARMUP_SEC:-5}"

# 기본 stage 세트 (필요하면 커스텀)
# 예: STAGES="ack_received service_gap"
STAGES="${STAGES:-ack_received service_gap}"

echo "[1/3] Running scenario: $SCENARIO for ${DURATION}s"
./gradlew -q run --args="$SCENARIO $DURATION"

echo "[2/3] Summarizing logs to CSV (warmupSec=$WARMUP_SEC)"
# LogSummaryTool을 main으로 직접 실행하는 방식(패키지명/클래스명에 맞게 수정)
# 아래 클래스 경로는 네가 만든 위치에 맞춰야 함.
SUMMARY_MAIN="com.kafka.io.kafkaproject.analysis.metrics.LogSummaryTool"

./gradlew -q javaexec --args="$SCENARIO $STAGES --warmupSec $WARMUP_SEC" \
  -PmainClass="$SUMMARY_MAIN" || {
    echo "⚠️ Gradle javaexec 방식이 프로젝트 설정에 없으면, IntelliJ에서 LogSummaryTool main을 args로 실행해줘."
    echo "   args: $SCENARIO $STAGES --warmupSec $WARMUP_SEC"
  }

echo "[3/3] Done."
echo "✅ results/$SCENARIO/summary.csv 확인"


##!/bin/bash
##
## 시나리오 실행 스크립트
##
## 사용법:
##   ./run-scenario.sh S0 60    # S0 시나리오, 60초 실행
##   ./run-scenario.sh S1 120   # S1 시나리오, 120초 실행
##
## 시나리오 (KICKOFF.md 7.1절):
##   S0 - Balanced Load (대조군)
##   S1 - Heavy vs Light Producer
##   S2 - Slow Consumer Backpressure
##   S3 - Control-plane 혼합
##
#
#SCENARIO=${1:-S0}
#DURATION=${2:-60}
#TIMESTAMP=$(date +%Y%m%d_%H%M%S)
#RESULT_DIR="results/${SCENARIO}_${TIMESTAMP}"
#
#echo "=========================================="
#echo "  시나리오 실행: $SCENARIO"
#echo "  실행 시간: ${DURATION}초"
#echo "  결과 디렉토리: $RESULT_DIR"
#echo "=========================================="
#
## 결과 디렉토리 생성
#mkdir -p "$RESULT_DIR"
#
## 시나리오 실행
#./gradlew run --args="$SCENARIO $DURATION" 2>&1 | tee "$RESULT_DIR/execution.log"
#
## 결과 파일 이동
#mv results/stage-log.csv "$RESULT_DIR/" 2>/dev/null
#
#echo "=========================================="
#echo "  실행 완료"
#echo "  로그: $RESULT_DIR/stage-log.csv"
#echo "=========================================="
