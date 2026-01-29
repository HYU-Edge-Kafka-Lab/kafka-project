#!/usr/bin/env bash
set -euo pipefail

# ===== Config =====
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka41}"

# 호스트(맥)에서 접속할 bootstrap 주소
BOOTSTRAP="${BOOTSTRAP:-localhost:9092}"

TOPIC="${TOPIC:-starvation-test}"
PARTITIONS="${PARTITIONS:-1}"
RF="${RF:-1}"

echo "[1/3] Starting broker via docker compose..."
docker compose -f "$COMPOSE_FILE" up -d

echo "[2/3] Waiting for Kafka tools in container ($KAFKA_CONTAINER)..."
# 컨테이너가 뜰 때까지 기다림
for i in {1..30}; do
  if docker exec "$KAFKA_CONTAINER" test -x /opt/kafka/bin/kafka-topics.sh >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "[3/3] Ensuring topic exists: $TOPIC"
# 토픽 존재 확인 (빠르게 실패/성공)
if docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP" --list 2>/dev/null | grep -q "^${TOPIC}$"; then
  echo "  - Topic already exists: $TOPIC"
else
  echo "  - Creating topic: $TOPIC (partitions=$PARTITIONS, rf=$RF)"
  docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP" \
    --create --topic "$TOPIC" --partitions "$PARTITIONS" --replication-factor "$RF" || true
fi

echo "✅ Broker ready. bootstrapServers=$BOOTSTRAP"
##!/bin/bash
##
## Kafka KRaft 브로커 시작 스크립트
##
## 브로커 설정 (KICKOFF.md 6.1, 6.3절):
## - Kafka 버전: 4.1.0
## - 모드: KRaft (ZK 의존성 제거)
## - num.network.threads: 3
## - num.io.threads: 8
##
#
#KAFKA_HOME="${KAFKA_HOME:-/opt/kafka}"
#CONFIG_FILE="$KAFKA_HOME/config/kraft/server.properties"
#
#echo "=========================================="
#echo "  Kafka KRaft 브로커 시작"
#echo "  버전: 4.1.0"
#echo "=========================================="
#
## 1. Storage 초기화 (최초 1회)
#if [ ! -d "$KAFKA_HOME/kraft-combined-logs" ]; then
#    echo "[1/3] Storage 초기화..."
#    KAFKA_CLUSTER_ID=$($KAFKA_HOME/bin/kafka-storage.sh random-uuid)
#    $KAFKA_HOME/bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c $CONFIG_FILE
#fi
#
## 2. 브로커 시작
#echo "[2/3] 브로커 시작..."
#$KAFKA_HOME/bin/kafka-server-start.sh $CONFIG_FILE &
#
## 3. 토픽 생성 (starvation-test)
#echo "[3/3] 토픽 생성 대기..."
#sleep 10
#
#$KAFKA_HOME/bin/kafka-topics.sh --create \
#    --topic starvation-test \
#    --partitions 1 \
#    --replication-factor 1 \
#    --bootstrap-server localhost:9092 \
#    2>/dev/null || echo "토픽이 이미 존재합니다."
#
#echo "=========================================="
#echo "  브로커 준비 완료"
#echo "  Bootstrap: localhost:9092"
#echo "  토픽: starvation-test (1 partition)"
#echo "=========================================="
