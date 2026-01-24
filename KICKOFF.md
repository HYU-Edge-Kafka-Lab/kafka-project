# Kafka Selector Starvation & Network Thread 분석

## 프로젝트 킥오프 정의 문서 (v1.0)

---

## 1. 프로젝트 목적 (Why)

| 구분 | 내용 |
|------|------|
| **목적 1** | Kafka Broker의 Network Thread / Selector 이벤트 처리 구조를 명확히 이해하고 문서화한다 |
| **목적 2** | 해당 구조에서 Selector starvation이 발생 가능한지 여부를 실험적으로 검증한다 |
| **비목적 (Out of Scope)** | Kafka handler 로직 최적화, 디스크 I/O, GC, OS 네트워크 튜닝 분석 |

---

## 2. 용어 및 범위 정의 (What exactly)

### 2.1 Starvation 정의

- **Starvation이란:**
  - 특정 client/connection/request가 지속적으로 처리되지 못해 지연이 누적되는 현상
  - 단발성 지연이나 handler 처리 병목은 starvation으로 보지 않는다

### 2.2 분석 범위

| 구분 | 내용 |
|------|------|
| **포함** | Acceptor → Network Thread → Selector → request enqueue → response send |
| **제외** | 비즈니스 로직 처리(handler 내부 연산), 저장소/디스크 병목 |

---

## 3. 판정 기준 (How to say "it happened")

### 3.1 지연 기준

| 측정 지표 | Starvation 의심 기준 |
|-----------|---------------------|
| p95 latency | light-client p95 latency ≥ **50ms** |
| p99 latency | light-client p99 latency ≥ **200ms** |
| max latency | light-client max latency ≥ **1,000ms** |
| 요청 처리 간격 | 동일 client의 연속 요청 간격 ≥ **5초** |

**근거:**
- Kafka 네트워크 레벨의 정상 지연은 1-10ms 수준
- 50ms는 정상치의 5-50배로, 명확한 이상 징후로 판단 가능
- 5초 간격은 일반적인 produce/fetch 주기 대비 충분히 긴 시간

### 3.2 편향 기준

| 기준 | 임계값 |
|------|--------|
| 처리 비율 편향 | 단일 client가 전체 처리의 ≥ **70%** 지속 점유 시 편향 |
| 지속 시간 | 해당 편향이 ≥ **30초** 이상 유지될 때 starvation으로 간주 |

**근거:**
- 2개 클라이언트 기준 50:50이 이상적, 70:30 이상이면 명확한 불균형
- 30초는 일시적 burst와 구분할 수 있는 충분한 지속 시간

### 3.3 병목 구분 규칙

| 지연 발생 시점 | 판정 |
|---------------|------|
| enqueue 이전부터 지연 | Network/Selector 단계 문제 |
| enqueue 이후부터 지연 | Handler/처리 병목 |

---

## 4. 관측 포인트 정의 (Where to measure)

### 4.1 공통 stage 이름 (브로커 내부)

```
┌─────────┐    ┌───────────┐    ┌─────────────┐    ┌───────────┐
│  poll   │ -> │ read_done │ -> │ enqueue_req │ -> │ send_done │
└─────────┘    └───────────┘    └─────────────┘    └───────────┘
                                       │
                               ┌───────┴───────┐
                               │  dequeue_req  │ (optional)
                               │  handle_done  │ (optional)
                               └───────────────┘
```

### 4.2 클라이언트 외부 stage

| 역할 | Stage |
|------|-------|
| Producer | `send_start`, `ack_received` |
| Consumer | `poll_start`, `fetch_received`, `process_done` |

---

## 5. 식별자 규약 (Log join을 위해)

### Client ID 규약

| Client | clientId 값 |
|--------|-------------|
| Heavy Producer | `heavy-producer-{N}` |
| Light Producer | `light-producer-{N}` |
| Slow Consumer | `slow-consumer-{N}` |
| Normal Consumer | `normal-consumer-{N}` |

### Request 식별

| 방법 | 설명 |
|------|------|
| correlationId | Kafka 프로토콜 기본 제공 |
| 메시지 key | `{clientId}-{timestamp}-{seq}` 형식 UUID |

### Connection 식별

- 우선: `connectionId` (Kafka 내부 할당)
- 불가 시: `clientId + thread` 조합

---

## 6. 실험 환경 고정값 (Reproducibility)

### 6.1 기본 인프라

| 항목 | 값                    | 비고                               |
|------|----------------------|----------------------------------|
| Kafka 버전 | **4.1.0**            | 최신 stable 버전                     |
| Java 버전 | **JDK 21 (Temurin)** | Kafka 3.x 공식 지원                  |
| 브로커 모드 | **KRaft**            | 단일 노드 실험에 적합, ZK 의존성 제거          |
| 브로커 수 | **1**                | Network Thread 분석 목적상 단일 브로커로 충분 |

### 6.2 토픽 설정

| 항목 | 값 | 비고 |
|------|-----|------|
| 파티션 수 | **1** | 분석 단순화 (파티션 분산 효과 배제) |
| replication factor | **1** | 단일 브로커 환경 |
| 토픽 이름 | `starvation-test` | |

### 6.3 브로커 주요 설정

| 설정 | 값 | 비고 |
|------|-----|------|
| `num.network.threads` | **3** (기본값) | 분석 대상 |
| `num.io.threads` | **8** (기본값) | |
| `socket.receive.buffer.bytes` | **102400** (100KB) | |
| `socket.send.buffer.bytes` | **102400** (100KB) | |
| `queued.max.requests` | **500** (기본값) | |

### 6.4 클라이언트 설정

**Producer:**

| 설정 | Heavy Producer | Light Producer |
|------|----------------|----------------|
| `acks` | `1` | `1` |
| `linger.ms` | `0` | `0` |
| `batch.size` | `65536` (64KB) | `16384` (16KB) |
| `buffer.memory` | `67108864` (64MB) | `33554432` (32MB) |
| 전송 빈도 | 연속 전송 (max throughput) | 100ms 간격 |
| 메시지 크기 | `10KB` | `1KB` |

**Consumer:**

| 설정 | Normal Consumer | Slow Consumer |
|------|-----------------|---------------|
| `max.poll.records` | `500` | `500` |
| `fetch.min.bytes` | `1` | `1` |
| `fetch.max.wait.ms` | `500` | `500` |
| 처리 지연 | 없음 | `500ms` sleep per batch |

---

## 7. Starvation 유발 전략 (의도적으로 만들 상황)

### 7.1 실험 시나리오

| 시나리오 ID | 이름 | 설명 | 기대 결과 |
|-------------|------|------|-----------|
| S1 | Heavy vs Light Producer | Heavy가 연속 전송, Light가 간헐적 전송 | Read 편향 발생 여부 확인 |
| S2 | Slow Consumer Backpressure | Consumer 처리 지연으로 send buffer 누적 | Write 편향 발생 여부 확인 |
| S3 | Control-plane 혼합 | 대량 Produce 중 Metadata/JoinGroup 요청 | Control 요청 지연 여부 확인 |
| S0 (대조군) | Balanced Load | 동일 TPS로 균형 잡힌 요청 | 정상 baseline 측정 |

### 7.2 부하 파라미터

| 시나리오 | Heavy TPS | Light TPS | 비율 |
|----------|-----------|-----------|------|
| S0 | 1,000 | 1,000 | 1:1 |
| S1 | 10,000 | 100 | 100:1 |
| S2 | 5,000 | - | Consumer 병목 |
| S3 | 5,000 | 10 (control) | Data vs Control |

---

## 8. 역할 분담 (Who does what)

### A 담당: 구조 분석 (목적 1 + 판정)

| 산출물 | 설명 |
|--------|------|
| Network Thread 흐름도 | Acceptor → Processor → Selector 전체 흐름 1장 |
| Stage ↔ 코드 위치 매핑 | 각 stage가 Kafka 소스 어디에 해당하는지 |
| Starvation 관측 포인트 | 로그/메트릭 삽입 위치 정의 |
| 판정 로직 구현 | 수집된 데이터로 starvation 여부 판단 |

**주요 분석 대상 클래스:**
- `kafka.network.SocketServer`
- `kafka.network.Processor`
- `org.apache.kafka.common.network.Selector`
- `kafka.network.RequestChannel`

### B 담당: 실험 설계 및 실행 (목적 2)

| 산출물 | 설명 |
|--------|------|
| 부하 재현 클라이언트 | Heavy/Light Producer, Slow Consumer 구현 |
| 실행 스크립트 | 시나리오별 실행 자동화 |
| 계측 코드 | 클라이언트 측 timestamp 기록 |
| 결과 데이터 | latency/편향 결과 CSV |
| 실험 요약 | 각 시나리오별 결과 요약 문서 |

---

## 9. 산출물 및 완료 정의 (DoD)

### 9.1 A 산출물 체크리스트

- [ ] Network Thread 아키텍처 다이어그램 (1장)
- [ ] Stage별 코드 위치 매핑 테이블
- [ ] Starvation 관측 포인트 정의서
- [ ] (선택) 브로커 측 계측 패치

### 9.2 B 산출물 체크리스트

- [ ] 부하 생성 클라이언트 코드 (`/clients`)
- [ ] 실행 스크립트 및 README (`/scripts`)
- [ ] 시나리오별 실험 결과 CSV (`/results`)
- [ ] 실험 결과 요약 문서

### 9.3 최종 결론 (공동)

| 항목 | 내용 |
|------|------|
| 발생 여부 | 해당 구조에서 starvation 발생함 / 발생하지 않음 |
| 발생 조건 | (발생 시) 트리거 조건 명시 |
| 발생 위치 | 네트워크 단계 / handler 단계 / 혼합 |
| 권장 사항 | (발생 시) 완화 방안 제안 |

---

## 10. 작업 방식 & 공유 규칙

### 10.1 로그 포맷

```
{ts}|{thread}|{clientId}|{stage}|{requestId}|{latency_ms}
```

**예시:**
```
2024-01-15T10:30:00.123|network-thread-0|heavy-producer-1|read_done|12345|2.5
2024-01-15T10:30:00.125|network-thread-0|heavy-producer-1|enqueue_req|12345|0.3
```
