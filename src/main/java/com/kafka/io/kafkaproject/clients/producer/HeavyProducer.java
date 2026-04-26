package com.kafka.io.kafkaproject.clients.producer;

import com.kafka.io.kafkaproject.config.KafkaConfig;
import com.kafka.io.kafkaproject.logging.StageLogger;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Heavy Producer - 고부하 지속 전송 Producer
 *
 * Producer 주요 설정:
 * - clientId: heavy-producer-{N}
 * - acks: 1
 * - linger.ms: 0
 * - batch.size: Light와 동일
 * - buffer.memory: Light와 동일
 * - 메시지 크기: Light와 동일
 * - 전송 방식: target TPS 기반 지속 전송
 * - inflight limit: 3000
 *
 * 사용 시나리오:
 * - S0: Heavy/Light 동일 부하 비교 (1000 TPS)
 * - S1: Heavy 고부하 / Light 단일 connection 비교
 * - S1N: Heavy 고부하 / 다중 Light connection 비교
 *
 * 역할:
 * - 지속적으로 높은 부하를 생성하여 Broker에서 거의 항상 ready 상태에 가까운 connection을 구성
 * - send_start, ack_received, service_gap 로그를 기록한다.
 * - 다만 service_gap은 Broker 내부 scheduling 직접 지표가 아니라 보조 지표로만 해석한다.
 */
public class HeavyProducer implements AutoCloseable {

    private static final String TOPIC = KafkaConfig.TOPIC_NAME;
    private static final int MESSAGE_SIZE_BYTES = KafkaConfig.PRODUCER_MESSAGE_SIZE;

    private final KafkaProducer<String, String> producer;
    private final String clientId;
    private final StageLogger logger;

    private final AtomicLong sequenceNumber = new AtomicLong(0);
    private final AtomicLong sentCount=new AtomicLong(0);
    private final AtomicLong ackCount=new AtomicLong(0);

    private final ConcurrentHashMap<String, Long> startNanos=new ConcurrentHashMap<>();

    private final AtomicLong lastAckNanos = new AtomicLong(-1L);

    public HeavyProducer(String scenarioId, String bootstrapServers, int producerId) throws IOException {
        this.clientId = "heavy-producer-" + producerId;
        this.producer = createProducer(bootstrapServers);
        this.logger=new StageLogger(scenarioId, clientId);
    }

    private KafkaProducer<String, String> createProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        props.put(ProducerConfig.ACKS_CONFIG, KafkaConfig.ACKS);
        props.put(ProducerConfig.LINGER_MS_CONFIG, KafkaConfig.LINGER_MS);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, KafkaConfig.PRODUCER_BATCH_SIZE);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, KafkaConfig.PRODUCER_BUFFER_MEMORY);

        return new KafkaProducer<>(props);
    }

    /**
     * target TPS에 맞추어 일정 시간 동안 메시지를 지속적으로 전송한다.
     *
     * @param targetTps 목표 TPS
     * @param durationSeconds 실행 시간(초)
     */
    public void runContinuousSend(int targetTps, int durationSeconds) {
        sentCount.set(0);
        ackCount.set(0);
        lastAckNanos.set(-1L);
        sequenceNumber.set(0);

        final long intervalNanos=1_000_000_000L/targetTps;
        final long durationNanos=durationSeconds*1_000_000_000L;

        long experimentStart=System.nanoTime();
        long endTime=experimentStart+durationNanos;
        long nextSendTime=experimentStart;

        while(System.nanoTime()<endTime){
            long now=System.nanoTime();

            if(now<nextSendTime){
                LockSupport.parkNanos(nextSendTime-now);
                continue;
            }

            sendMessage();

            nextSendTime+=intervalNanos;

            long after = System.nanoTime();
            if (after - nextSendTime > 1_000_000_000L) { // 1초 이상 밀렸다면
                nextSendTime = after;
            }
        }

        producer.flush();

        long experimentEnd=System.nanoTime();
        double elapsedSec=(experimentEnd-experimentStart)/1_000_000_000.0;

        long totalSent=sentCount.get();
        long totalAcked=ackCount.get();

        double observedSendTps=elapsedSec>0?totalSent/elapsedSec:0.0;
        double observedAckTps=elapsedSec>0?totalAcked/elapsedSec:0.0;

        System.out.printf(
                "[HeavyProducer:%s] targetTPS=%d, duration=%ds, sent=%d, acked=%d, elapsed=%.3fs, observedSendTPS=%.2f, observedAckTPS=%.2f%n",
                clientId, targetTps, durationSeconds, totalSent, totalAcked, elapsedSec, observedSendTps, observedAckTps
        );
    }

    /**
     * 단일 메시지 전송
     */
    public void sendMessage() {

        long seq = sequenceNumber.incrementAndGet();
        long timestamp = System.currentTimeMillis();

        String requestId = clientId + "-" + timestamp + "-" + seq;
        String value = generatePayload(MESSAGE_SIZE_BYTES);

        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, requestId, value);

        // send_start 로깅
        long start=System.nanoTime();
        startNanos.put(requestId, start);
        logger.logStage(clientId, "send_start", requestId);

        sentCount.incrementAndGet();

        producer.send(record, (metadata, exception) -> {
            Long st = startNanos.remove(requestId);
            long end = System.nanoTime();
            if (exception != null) {
                logger.logStage(clientId, "ack_error", requestId);
                exception.printStackTrace();
                return;
            }
            ackCount.incrementAndGet();
            long prevAck = lastAckNanos.getAndSet(end);
            if (prevAck > 0) {
                logger.logLatency(clientId, "service_gap", requestId, prevAck, end);
            }
            if (st != null) {
                logger.logLatency(clientId, "ack_received", requestId, st, end);
            } else {
                logger.logStage(clientId, "ack_received", requestId);
            }

        });
    }

    private String generatePayload(int sizeBytes) {
        // 지정된 크기의 페이로드 생성
        return "X".repeat(sizeBytes);
    }

    @Override
    public void close() {
        try{
            producer.flush();
        }finally {
            producer.close();
            logger.close();
        }

    }
}
