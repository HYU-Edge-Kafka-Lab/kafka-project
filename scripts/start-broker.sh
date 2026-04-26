#!/usr/bin/env bash
set -euo pipefail

# ===== Config =====
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka41}"

# 호스트(맥)에서 접속할 bootstrap 주소
BOOTSTRAP="${BOOTSTRAP:-localhost:9092}"

TOPIC="${TOPIC:-starvation-test}"
PARTITIONS="${PARTITIONS:-8}"
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
