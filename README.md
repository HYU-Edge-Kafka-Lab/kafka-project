# Kafka Selector Starvation & Network Thread 분석

Kafka Broker의 Network Thread / Selector 이벤트 처리 구조를 분석하고, Selector starvation 발생 가능 여부를 실험적으로 검증하는 프로젝트입니다.

## 프로젝트 목적

| 구분 | 내용 |
|------|------|
| 목적 1 | Kafka Broker의 Network Thread / Selector 이벤트 처리 구조를 명확히 이해하고 문서화 |
| 목적 2 | 해당 구조에서 Selector starvation이 발생 가능한지 여부를 실험적으로 검증 |

## 기술 스택

- **Kafka**: 4.1.0
- **Java**: JDK 21 (Temurin)
- **Spring Boot**: 4.0.2
- **브로커 모드**: KRaft (ZK 의존성 제거)

## 프로젝트 구조

```
kafka-project/
├── src/main/java/com/kafka/io/kafkaproject/
│   │
│   ├── analysis/                          # A 담당: 구조 분석
│   │   ├── selector/
│   │   │   └── SelectorAnalyzer.java      # Selector 내부 동작 분석
│   │   ├── network/
│   │   │   └── NetworkThreadTracer.java   # Network Thread 추적
│   │   └── metrics/
│   │       └── StarvationDetector.java    # Starvation 판정 (임계값 기반)
│   │
│   ├── clients/                           # B 담당: 부하 클라이언트
│   │   ├── producer/
│   │   │   ├── HeavyProducer.java         # 고부하 연속 전송 (10KB, 64KB batch)
│   │   │   └── LightProducer.java         # 저부하 간헐적 전송 (1KB, 100ms 간격)
│   │   └── consumer/
│   │       ├── SlowConsumer.java          # 처리 지연 소비자 (500ms/batch)
│   │       └── NormalConsumer.java        # 정상 속도 소비자
│   │
│   ├── scenarios/
│   │   └── ScenarioRunner.java            # S0~S3 실험 시나리오 실행기
│   │
│   ├── logging/
│   │   └── StageLogger.java               # Stage별 로그 기록기
│   │
│   └── config/
│       └── KafkaConfig.java               # 실험 설정값 상수
│
├── scripts/
│   ├── start-broker.sh                    # KRaft 브로커 시작
│   └── run-scenario.sh                    # 시나리오 실행
│
├── results/                               # 실험 결과 CSV 저장
│
├── docs/
│   ├── architecture.md                    # Network Thread 아키텍처
│   └── stage-mapping.md                   # Stage ↔ 코드 위치 매핑
│
├── KICKOFF.md                             # 프로젝트 킥오프 정의 문서
└── README.md
```

## 실험 시나리오

| ID | 이름 | 설명 | Heavy TPS | Light TPS |
|----|------|------|-----------|-----------|
| S0 | Balanced Load | 대조군 (균형 부하) | 1,000 | 1,000 |
| S1 | Heavy vs Light | Read 편향 테스트 | 10,000 | 100 |
| S2 | Slow Consumer | Write 편향 테스트 (Backpressure) | 5,000 | - |
| S3 | Control-plane | Control 요청 지연 테스트 | 5,000 | 10 |

## Starvation 판정 기준

### 지연 기준
| 지표 | 임계값 |
|------|--------|
| p95 latency | ≥ 50ms |
| p99 latency | ≥ 200ms |
| max latency | ≥ 1,000ms |
| 요청 간격 | ≥ 5초 |

### 편향 기준
- 단일 client가 전체 처리의 **70% 이상** 점유
- 해당 편향이 **30초 이상** 지속

## 로그 포맷

```
{ts}|{thread}|{clientId}|{stage}|{requestId}|{latency_ms}
```

예시:
```
2024-01-15T10:30:00.123|network-thread-0|heavy-producer-1|read_done|12345|2.5
2024-01-15T10:30:00.125|network-thread-0|heavy-producer-1|enqueue_req|12345|0.3
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
# S0 시나리오, 60초 실행
./scripts/run-scenario.sh S0 60

# S1 시나리오, 120초 실행
./scripts/run-scenario.sh S1 120
```

### 4. 결과 확인
```bash
ls results/
```

## 주요 분석 대상 클래스

| 클래스 | 역할 |
|--------|------|
| `kafka.network.SocketServer` | Acceptor, Processor 관리 |
| `kafka.network.Processor` | Network Thread 구현체 |
| `org.apache.kafka.common.network.Selector` | NIO Selector 래퍼 (핵심) |
| `kafka.network.RequestChannel` | Request/Response 큐 |

## 역할 분담

### A 담당: 구조 분석
- Network Thread 흐름도
- Stage ↔ 코드 위치 매핑
- Starvation 관측 포인트 정의

### B 담당: 실험 설계 및 실행
- 부하 생성 클라이언트 구현
- 시나리오별 실험 실행
- 결과 데이터 수집 및 분석

## 참고 문서

- [KICKOFF.md](./KICKOFF.md) - 프로젝트 킥오프 정의 문서
- [docs/architecture.md](./docs/architecture.md) - Network Thread 아키텍처
- [docs/stage-mapping.md](./docs/stage-mapping.md) - Stage 코드 매핑
