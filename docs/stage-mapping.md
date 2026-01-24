# Stage ↔ 코드 위치 매핑

> A 담당 산출물 (KICKOFF.md 9.1절)

## 브로커 내부 Stage (KICKOFF.md 4.1절)

### poll
- **설명**: Selector.poll() 호출 시점
- **파일**: `clients/src/main/java/org/apache/kafka/common/network/Selector.java`
- **메서드**: `poll(long timeout)`
- **라인**: TBD (버전별 상이)

### read_done
- **설명**: 데이터 읽기 완료
- **파일**: `clients/src/main/java/org/apache/kafka/common/network/Selector.java`
- **메서드**: `pollSelectionKeys(Set<SelectionKey> selectionKeys, ...)`
- **조건**: `key.isReadable()` 처리 후

### enqueue_req
- **설명**: RequestChannel에 요청 적재
- **파일**: `core/src/main/scala/kafka/network/SocketServer.scala`
- **메서드**: `Processor.processCompletedReceives()`
- **호출**: `requestChannel.sendRequest()`

### dequeue_req
- **설명**: Handler가 요청 꺼냄
- **파일**: `core/src/main/scala/kafka/network/RequestChannel.scala`
- **메서드**: `receiveRequest(timeout)`

### handle_done
- **설명**: Handler 처리 완료
- **파일**: `core/src/main/scala/kafka/server/KafkaApis.scala`
- **메서드**: `handle(request, requestLocal)`

### send_done
- **설명**: Response 전송 완료
- **파일**: `clients/src/main/java/org/apache/kafka/common/network/Selector.java`
- **조건**: `key.isWritable()` 처리 완료

---

## 클라이언트 Stage (KICKOFF.md 4.2절)

### Producer

| Stage | 설명 | 코드 위치 |
|-------|------|----------|
| `send_start` | 전송 시작 | `KafkaProducer.send()` 호출 시점 |
| `ack_received` | ACK 수신 | `Callback.onCompletion()` 호출 시점 |

### Consumer

| Stage | 설명 | 코드 위치 |
|-------|------|----------|
| `poll_start` | poll 시작 | `KafkaConsumer.poll()` 호출 시점 |
| `fetch_received` | fetch 완료 | `poll()` 리턴 시점 |
| `process_done` | 처리 완료 | 레코드 처리 완료 시점 |

---

## 계측 코드 삽입 방법

### 방법 1: 클라이언트 측 계측 (권장)
```java
// Producer
long sendStart = System.nanoTime();
producer.send(record, (metadata, exception) -> {
    long ackReceived = System.nanoTime();
    logger.logLatency(clientId, "ack_received", requestId, sendStart, ackReceived);
});
logger.logStage(clientId, "send_start", requestId);
```

### 방법 2: 브로커 패치 (선택)
- Kafka 소스 수정 후 재빌드 필요
- 정밀한 내부 측정 가능
- 유지보수 부담 존재

---

## TODO

- [ ] Kafka 4.1.0 소스에서 정확한 라인 번호 확인
- [ ] 브로커 패치 여부 결정
- [ ] JMX 메트릭과 연동 가능 여부 확인
