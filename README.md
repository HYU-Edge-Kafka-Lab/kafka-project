# Kafka Selector Scheduling Policy 실험 프로젝트

Kafka Broker의 Network Thread / Selector 처리 구조를 분석하고, read scheduling policy에 따라 외부 응답 특성과 내부 상태가 어떻게 달라지는지 관찰하는 프로젝트입니다.

## 프로젝트 목적

| 구분 | 내용 |
|------|------|
| 목적 1 | Kafka Broker의 Network Thread / Selector 처리 구조를 이해하고 문서화 |
| 목적 2 | `default`, `shuffle`, `drr` 정책을 비교할 수 있는 실험 환경 구성 |
| 목적 3 | DRR의 deficit 기반 stateful 메커니즘 구현 및 계측 |

## 기술 스택

- **Kafka**: 4.1.0
- **Java**: JDK 21 (Temurin)
- **Spring Boot**: 4.0.2
- **브로커 모드**: KRaft

## 전제

- 이 저장소에는 Kafka 브로커 소스 전체가 포함되어 있지 않습니다.
- DRR는 Kafka 4.1.0 소스 트리에 patch bundle을 적용하는 방식으로 사용합니다.
- 저장소에는 patch bundle, 실험 harness, 정책별 broker 설정, 결과 요약 코드가 함께 포함되어 있습니다.

## 프로젝트 구조

```text
kafka-project/
├── src/main/java/com/kafka/io/kafkaproject/
│   ├── analysis/                          # 구조 분석 및 로그 요약
│   ├── clients/                           # producer / consumer
│   ├── config/                            # 실험 설정 상수
│   ├── logging/                           # stage 로그 기록
│   └── scenarios/                         # S0 / S1 / S2 / S1N 실행기
├── scripts/                               # 브로커 기동 및 시나리오 실행 스크립트
├── docker/                                # 정책별 broker 설정
├── patches/                               # Kafka patch bundle
├── results/                               # 실험 결과
├── docs/                                  # 구조 문서
├── KICKOFF.md
└── README.md
```

## 실험 시나리오

저장소에는 여러 탐색 시나리오가 포함되어 있다. 다만 최종 보고서의 핵심 비교는 강한 contention 조건을 만들기 위한 `S2`를 기준으로 수행하였다.

| ID | 설명 |
|----|------|
| S0 | Heavy 500 TPS / Light 500 TPS baseline 탐색 |
| S1 | Heavy 3000 TPS / Light 500 TPS 비대칭 부하 탐색 |
| S2 | Heavy 6개 × 500 TPS / Light 1개 × 500 TPS, 최종 비교 기준 |
| S1N | Heavy 1000 TPS / Light 5개 × 500 TPS 보조 탐색 |

## 로그 포맷

```text
ts|thread|clientId|stage|requestId|latency_ms
```

예시:

```text
2024-01-15T10:30:00.123|network-thread-0|heavy-producer-1|ack_received|12345|2.5
```

## Patch Bundle 사용

Kafka 4.1.0 소스 트리를 별도로 준비한 뒤, `patches/kafka-4.1.0-drr-bundles` 아래 스크립트를 사용해 DRR patch를 적용할 수 있습니다.

예시:

```bash
patches/kafka-4.1.0-drr-bundles/apply-and-verify.sh /path/to/kafka-4.1.0-drr
```

baseline 비교:

```bash
patches/kafka-4.1.0-drr-bundles/compare-pristine-and-patched.sh \
  /path/to/kafka-4.1.0-pristine \
  /path/to/kafka-4.1.0-drr
```

## 빠른 시작

### 1. 빌드

```bash
./gradlew build
```

### 2. 브로커 시작

```bash
./scripts/start-broker.sh
```

### 3. 시나리오 실행

```bash
# 최종 보고서 기준 시나리오
./scripts/run-scenario.sh S2 60 default
./scripts/run-scenario.sh S2 60 shuffle
./scripts/run-scenario.sh S2 60 drr

# 탐색 시나리오
./scripts/run-scenario.sh S0 60
./scripts/run-scenario.sh S1 120
./scripts/run-scenario.sh S1N 120
```

### 4. 결과 확인

```bash
ls results/
```

## DRR Kafka Docker 실행

패치 Kafka는 기본 compose가 아니라 정책별 compose를 사용합니다.

1. 패치된 Kafka 소스를 준비하고 patch를 적용합니다.
2. 로컬 이미지 생성:

```bash
PATCHED_KAFKA_ROOT=/tmp/kafka-4.1.0-drr ./scripts/build-drr-docker-image.sh
```

3. DRR 브로커 시작:

```bash
./scripts/start-drr-broker.sh
```

4. 실험 실행:

```bash
./scripts/run-scenario.sh S2 60 drr
```

## Default / Shuffle / DRR 실행 경로

### Default broker

```bash
./scripts/start-default-broker.sh
GRADLE_USER_HOME=/tmp/kafka-project-gradle ./scripts/run-scenario.sh S2 60 default
```

### Shuffle broker

```bash
./scripts/start-shuffle-broker.sh
GRADLE_USER_HOME=/tmp/kafka-project-gradle ./scripts/run-scenario.sh S2 60 shuffle
```

### DRR broker

```bash
./scripts/start-drr-broker.sh
GRADLE_USER_HOME=/tmp/kafka-project-gradle ./scripts/run-scenario.sh S2 60 drr
```

## 1-thread 실험 경로

최종 보고서의 강한 contention 비교는 `num.network.threads=1` 경로를 기준으로 수행하였다.

```bash
./scripts/start-default-1thread-broker.sh
./scripts/run-scenario.sh S2 60 default

./scripts/start-shuffle-1thread-broker.sh
./scripts/run-scenario.sh S2 60 shuffle

./scripts/start-drr-1thread-broker.sh
./scripts/run-scenario.sh S2 60 drr
```

## 주요 분석 대상 클래스

| 클래스 | 역할 |
|--------|------|
| `kafka.network.SocketServer` | Acceptor, Processor 관리 |
| `kafka.network.Processor` | Network Thread 구현체 |
| `org.apache.kafka.common.network.Selector` | NIO Selector 래퍼 |
| `kafka.network.RequestChannel` | Request/Response 큐 |

## 참고 문서

- [docs/architecture.md](./docs/architecture.md)
- [docs/stage-mapping.md](./docs/stage-mapping.md)

초기 실험 구상과 중간 설계 메모는 `KICKOFF.md`, `KICKOFF2.md`에 남아 있다. 해당 문서들은 작업 이력용 참고 자료이며, 최종 보고서 기준은 README와 보고서 본문을 따른다.
