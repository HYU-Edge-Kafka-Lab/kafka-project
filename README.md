# Kafka 4.1.0 DRR Patch + Experiment Guide

이 저장소에서 외부에 공유해야 할 핵심은 두 가지입니다.

- Kafka 4.1.0 `Selector` read scheduling용 DRR patch bundle
- patch 적용 후 fairness 실험을 돌리는 harness

## 포함 파일

- [`patches/kafka-4.1.0-drr-bundles`](/Users/taehee/personal/kafka-project/patches/kafka-4.1.0-drr-bundles)
- [`scripts/run-scenario.sh`](/Users/taehee/personal/kafka-project/scripts/run-scenario.sh)
- [`src/main/java/com/kafka/io/kafkaproject/scenarios/ScenarioRunner.java`](/Users/taehee/personal/kafka-project/src/main/java/com/kafka/io/kafkaproject/scenarios/ScenarioRunner.java)
- [`src/main/java/com/kafka/io/kafkaproject/analysis/metrics/LogSummaryTool.java`](/Users/taehee/personal/kafka-project/src/main/java/com/kafka/io/kafkaproject/analysis/metrics/LogSummaryTool.java)
- [`src/main/java/com/kafka/io/kafkaproject/logging/StageLogger.java`](/Users/taehee/personal/kafka-project/src/main/java/com/kafka/io/kafkaproject/logging/StageLogger.java)

## 전제

- 이 저장소에는 Kafka 브로커 소스 전체가 없습니다.
- DRR는 Kafka 4.1.0 소스 트리에 patch로 적용해야 합니다.
- 현재 [`docker-compose.yml`](/Users/taehee/personal/kafka-project/docker-compose.yml)과 [`scripts/start-broker.sh`](/Users/taehee/personal/kafka-project/scripts/start-broker.sh)는 순정 Kafka 경로라 DRR 실험용이 아닙니다.

## 1. Kafka 4.1.0 준비

Kafka 4.1.0 소스 트리 두 개를 준비합니다.

```bash
/path/to/kafka-4.1.0-pristine
/path/to/kafka-4.1.0-drr
```

## 2. DRR patch 적용

```bash
/Users/taehee/personal/kafka-project/patches/kafka-4.1.0-drr-bundles/apply-and-verify.sh \
  /path/to/kafka-4.1.0-drr
```

baseline 비교:

```bash
/Users/taehee/personal/kafka-project/patches/kafka-4.1.0-drr-bundles/compare-pristine-and-patched.sh \
  /path/to/kafka-4.1.0-pristine \
  /path/to/kafka-4.1.0-drr
```

## 3. 브로커 설정

patch 적용 후, DRR broker의 `config/kraft/server.properties`에 아래 설정을 추가합니다.

```properties
socket.read.scheduling.policy=drr
socket.read.scheduling.drr.quantum=1
socket.read.scheduling.drr.min.cost=1
socket.read.scheduling.drr.max.deficit=8
socket.read.scheduling.drr.enable.bytes.cost=false
```

비교 실험 시 설정값:

- baseline: `socket.read.scheduling.policy=default`
- shuffle: `socket.read.scheduling.policy=shuffle`
- drr: 위 5개 설정 사용

## 4. DRR broker 실행

예시:

```bash
KAFKA_HOME=/path/to/kafka-4.1.0-drr
CONFIG_FILE="$KAFKA_HOME/config/kraft/server.properties"

KAFKA_CLUSTER_ID=$("$KAFKA_HOME/bin/kafka-storage.sh" random-uuid)
"$KAFKA_HOME/bin/kafka-storage.sh" format -t "$KAFKA_CLUSTER_ID" -c "$CONFIG_FILE"
"$KAFKA_HOME/bin/kafka-server-start.sh" "$CONFIG_FILE"
```

현재 실험 코드는 broker 주소를 `localhost:9092`로 가정합니다.

## 5. 실험 harness 빌드

```bash
cd /Users/taehee/personal/kafka-project
./gradlew build
```

## 6. 실험 실행

결과 로그는 `results/<scenario>/`에 append 되므로 재실행 전 삭제를 권장합니다.

```bash
rm -rf results/S0 results/S1 results/S1N
./scripts/run-scenario.sh S0 60
./scripts/run-scenario.sh S1 120
./scripts/run-scenario.sh S1N 120
```

지원 시나리오:

- `S0`: balanced baseline
- `S1`: heavy vs single light
- `S1N`: heavy vs many light

## 7. 결과 확인

```bash
sed -n '1,20p' results/S0/summary.csv
sed -n '1,20p' results/S1/summary.csv
sed -n '1,20p' results/S1N/summary.csv
```

기본 요약 대상은 `ack_received`, `service_gap`입니다.
