# Kafka Selector Fairness Degradation Analysis

> 상태: 중간 실험 설계 문서
>
> 이 문서는 fairness/service_gap 중심의 중간 설계안을 정리한 작업 문서이다. 현재 저장소의 최종 실험 방향 및 보고서 결론과는 차이가 있을 수 있으며, 최신 기준은 README와 최종 보고서 본문을 따른다.

## 프로젝트 킥오프 정의 문서

---

## 1. 프로젝트 목적 (Why)

| 구분 | 내용 |
|------|------|
| 목적 1 | Kafka Broker의 Network Thread / Selector 이벤트 처리 구조를 명확히 이해하고 문서화한다 |
| 목적 2 | Selector 기반 네트워크 처리 구조에서 **연결 단위 공정성 붕괴(Fairness Degradation)** 가 발생할 수 있는지 실험적으로 검증한다 |
| Out of Scope | Handler 로직 최적화, 디스크 I/O, GC, OS 네트워크 튜닝 |

---

## 2. 문제 정의 및 범위 (What exactly)

### 2.1 공정성 붕괴(Fairness Degradation) 정의

- 특정 client / connection 이 지속적으로 상대적으로 적은 처리 기회를 받는 현상
- 평균 latency 가 정상 범위여도 발생 가능
- 누적될 경우 **연결 단위 서비스 공백(service gap)** 증가로 관측됨

### 2.2 분석 범위

| 구분 | 내용 |
|------|------|
| 포함 | Acceptor → Network Thread → Selector → request enqueue → response send |
| 제외 | 비즈니스 로직 처리, 디스크 / 스토리지 병목 |

---

## 3. 측정 지표 및 판정 기준 (How to judge)

### 3.1 핵심 측정 지표

| 지표 | 정의 | 용도 |
|-----|------|------|
| ack_received latency | send_start → ack_received | 요청 단위 처리 지연 (보조 지표) |
| service_gap | 동일 client 의 연속 ack_received 간 시간 | 연결 단위 처리 기회 빈도 (핵심 지표) |

### 3.2 판정 기준

| 구분 | 판정 기준 |
|-----|-----------|
| Baseline (S0) | Heavy / Light client 간 service_gap 분포가 유사 |
| Fairness Degradation (S1) | Light client 의 service_gap p95/p99 가 Heavy 대비 유의미하게 증가 |
| Starvation | service_gap 분포가 지속적으로 분리|

> 평균 latency 가 정상 범위여도 service_gap 분포 분리는 공정성 붕괴로 판단한다.

---

## 4. 관측 포인트 (Where to measure)

### 4.1 Producer 관측 Stage

| Stage | 설명 |
|------|------|
| send_start | 메시지 전송 시작 시각 |
| ack_received | Broker 응답 수신 시각 |
| service_gap | 연속 ack_received 간 시간 |

### 4.2 Consumer 관측 Stage (보조)

| Stage | 설명 |
|------|------|
| poll_start | poll 호출 시점 |
| fetch_received | fetch 응답 수신 시점 |
| process_done | 처리 완료 시점 |

---

## 5. 식별자 규약

### Client ID

| Client | clientId |
|------|----------|
| Heavy Producer | heavy-producer-{N} |
| Light Producer | light-producer-{N} |

### Request ID

{clientId}-{timestamp}-{seq}

---

## 6. 실험 환경 (Reproducibility)

### 6.1 인프라

| 항목 | 값 |
|-----|----|
| Kafka | 4.1.0 (KRaft, Single Broker) |
| Java | JDK 21 |
| Broker Count | 1 |
| Topic / Partition | 1 / 1 |

### 6.2 Producer 설정

| 항목 | Heavy | Light |
|-----|------|-------|
| Message Size | 10KB | 1KB |
| TPS | 시나리오별 상이 | 시나리오별 상이 |
| 역할 | Hot workload | Cold / 피해자 |

---

## 7. 실험 시나리오

### S0 — Baseline (공정성 정상 상태)

| 항목 | 값 |
|-----|----|
| Heavy TPS | 1,000 |
| Light TPS | 1,000 |
| 목적 | service_gap 분포 기준선 확보 |
| 기대 결과 | Heavy / Light service_gap 분포 유사 |

---

### S1 — Fairness Degradation (핵심 시나리오)

| 항목 | 값 |
|-----|----|
| Heavy TPS | 10,000 |
| Light TPS | 100 |
| 목적 | Selector 편향으로 인한 연결 단위 처리 기회 박탈 관측 |
| 기대 결과 | Light client service_gap 분포 증가 및 분리 |

---

### S1N — Fairness Degradation (Multiple Light Producers)

| 항목 | 값 |
|-----|----|
| Heavy TPS | 10,000 |
| Light TPS | 100 × 5 |
| Light Producer 수 | 5 |
| 목적 | 다수 Cold 연결 환경에서 공정성 붕괴가 개별 연결 문제가 아닌 구조적 현상임을 검증 |
| 기대 결과 | 모든 Light Producer에서 service_gap 분포가 일관되게 증가 |

**의의:**
- 단일 Light Producer가 아닌 다수 Light Producer가 동시에 피해자가 되는 패턴을 검증
- 스마트 팩토리 / 엣지 환경에서 다수 저빈도 디바이스가 공존하는 현실적 워크로드 모델에 부합
- Selector 수준에서 연결 단위 공정성 붕괴가 확장(scale)됨을 확인

---


## 8. 역할 분담

### A 담당 — 구조 분석

- Kafka Network Thread / Selector 처리 흐름 분석
- Selector 구조에서 공정성 붕괴가 발생하는 원인 규명
- service_gap 증가와 구조적 특성의 연결

### B 담당 — 실험 및 계측

- Heavy / Light Producer 구현
- service_gap 및 latency 계측
- S0 / S1 / S1N 실험 결과 비교 및 요약

---

## 9. 최종 산출물

| 항목 | 내용 |
|-----|------|
| 실험 결과 | S0 / S1 / S1N service_gap 비교 CSV |
| 핵심 결론 | Selector 기반 처리 구조에서 연결 단위 공정성 붕괴 발생 가능성 확인 |
| 확장 방향 | Selector-level fairness scheduling 설계 제안 |

---

## 10. 로그 포맷

{ts}|{thread}|{clientId}|{stage}|{requestId}|{latency_ms}
