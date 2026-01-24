# Network Thread 아키텍처

> A 담당 산출물 (KICKOFF.md 9.1절)

## 1. 전체 흐름도

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                            Kafka Broker                                       │
│                                                                              │
│  ┌─────────┐     ┌─────────────────────────────────────┐     ┌───────────┐  │
│  │         │     │        Network Threads (3개)         │     │           │  │
│  │         │     │  ┌───────────┐  ┌───────────┐       │     │           │  │
│  │ Acceptor├────►│  │Processor-0│  │Processor-1│  ...  │     │ Handler   │  │
│  │         │     │  │           │  │           │       │     │ Threads   │  │
│  │ (1개)   │     │  │ Selector  │  │ Selector  │       │     │ (8개)     │  │
│  │         │     │  └─────┬─────┘  └─────┬─────┘       │     │           │  │
│  └─────────┘     │        │              │             │     │           │  │
│                  │        ▼              ▼             │     │           │  │
│                  │  ┌─────────────────────────────┐    │     │           │  │
│                  │  │      RequestChannel         │◄───┼────►│           │  │
│                  │  │  (Request/Response Queue)   │    │     │           │  │
│                  │  └─────────────────────────────┘    │     │           │  │
│                  └─────────────────────────────────────┘     └───────────┘  │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

## 2. Stage별 코드 위치 매핑

| Stage | 설명 | Kafka 소스 위치 |
|-------|------|----------------|
| `poll` | Selector.poll() 호출 | `clients/.../Selector.java:poll()` |
| `read_done` | 데이터 읽기 완료 | `clients/.../Selector.java:pollSelectionKeys()` |
| `enqueue_req` | RequestChannel에 적재 | `core/.../SocketServer.scala:processCompletedReceives()` |
| `dequeue_req` | Handler가 요청 꺼냄 | `core/.../RequestChannel.scala:receiveRequest()` |
| `handle_done` | Handler 처리 완료 | `core/.../KafkaApis.scala:handle()` |
| `send_done` | Response 전송 완료 | `clients/.../Selector.java:pollSelectionKeys()` |

## 3. 주요 분석 대상 클래스

### 3.1 kafka.network.SocketServer
- Acceptor, Processor 관리
- 연결 할당 로직

### 3.2 kafka.network.Processor
- Network Thread 구현체
- Selector 소유 및 운영

### 3.3 org.apache.kafka.common.network.Selector
- NIO Selector 래퍼
- **Starvation 분석 핵심 지점**

### 3.4 kafka.network.RequestChannel
- Request/Response 큐
- Handler와 Network Thread 간 인터페이스

## 4. Starvation 발생 가능 지점

```
                    ┌─────────────────────────┐
                    │   Starvation 가능 지점   │
                    └─────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ Selector.poll │    │ Request Queue │    │ Response Send │
│               │    │               │    │               │
│ - 대용량 read │    │ - Queue full  │    │ - Buffer full │
│ - 채널 편향   │    │ - 처리 지연   │    │ - Slow client │
└───────────────┘    └───────────────┘    └───────────────┘
```

## 5. TODO

- [ ] 각 Stage에서 timestamp 수집 코드 삽입 위치 확정
- [ ] Selector의 채널 선택 알고리즘 분석
- [ ] RequestChannel 큐 모니터링 방법 결정
