#!/bin/bash
#
# 시나리오 실행 스크립트
#
# 사용법:
#   ./run-scenario.sh S0 60    # S0 시나리오, 60초 실행
#   ./run-scenario.sh S1 120   # S1 시나리오, 120초 실행
#
# 시나리오 (KICKOFF.md 7.1절):
#   S0 - Balanced Load (대조군)
#   S1 - Heavy vs Light Producer
#   S2 - Slow Consumer Backpressure
#   S3 - Control-plane 혼합
#

SCENARIO=${1:-S0}
DURATION=${2:-60}
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULT_DIR="results/${SCENARIO}_${TIMESTAMP}"

echo "=========================================="
echo "  시나리오 실행: $SCENARIO"
echo "  실행 시간: ${DURATION}초"
echo "  결과 디렉토리: $RESULT_DIR"
echo "=========================================="

# 결과 디렉토리 생성
mkdir -p "$RESULT_DIR"

# 시나리오 실행
./gradlew run --args="$SCENARIO $DURATION" 2>&1 | tee "$RESULT_DIR/execution.log"

# 결과 파일 이동
mv results/stage-log.csv "$RESULT_DIR/" 2>/dev/null

echo "=========================================="
echo "  실행 완료"
echo "  로그: $RESULT_DIR/stage-log.csv"
echo "=========================================="
