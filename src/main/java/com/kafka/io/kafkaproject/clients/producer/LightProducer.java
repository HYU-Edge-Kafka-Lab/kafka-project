package com.kafka.io.kafkaproject.clients.producer;

import com.kafka.io.kafkaproject.logging.StageLogger;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Light Producer - 저부하 제어 전송 Producer
 *
 * Producer 주요 설정:
 * - clientId: light-producer-{N}
 * - acks: 1
 * - linger.ms: 0
 * - batch.size: 16384 (16KB)
 * - buffer.memory: 33554432 (32MB)
 * - 메시지 크기: 1KB
 * - 전송 방식: target TPS 기반 제어 전송
 * - inflight limit: 3000
 *
 * 역할:
 * - Heavy Producer와 비교되는 상대적으로 낮은 부하 connection 생성
 * - send_start, ack_received, service_gap 로그를 기록하여 ACK latency 및 connection 처리 간격 분석에 사용
 *
 *
 * 사용 시나리오:
 * - S0: Heavy/Light 동일 부하 비교 (1000 TPS)
 * - S1: Heavy 고부하 / Light 단일 connection 비교 (1000 TPS)
 * - S1N: 다중 Light connection 구성 (각 200 TPS)
 */
public class LightProducer implements AutoCloseable {

    private static final String TOPIC = "starvation-test";
    private static final int MESSAGE_SIZE_BYTES = 1024; // 1KB

    private final KafkaProducer<String, String> producer;
    private final String clientId;
    private final StageLogger logger;

    private final AtomicLong sequenceNumber = new AtomicLong(0);
    private final AtomicLong sentCount = new AtomicLong(0);
    private final AtomicLong ackCount = new AtomicLong(0);
    private final ConcurrentHashMap<String, Long> startNanos = new ConcurrentHashMap<>();

    private final AtomicLong lastAckNanos = new AtomicLong(-1L);
    private final Semaphore inflight = new Semaphore(3000);

    public LightProducer(String scenarioId, String bootstrapServers, int producerId) throws IOException {
        this.clientId = "light-producer-" + producerId;
        this.producer = createProducer(bootstrapServers);
        this.logger = new StageLogger(scenarioId, clientId);
    }

    private KafkaProducer<String, String> createProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);        // 16KB
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432L); // 32MB

        return new KafkaProducer<>(props);
    }

    /**
     * target TPS에 맞추어 일정 시간 동안 메시지를 제어 전송한다.
     *
     * @param targetTps 목표 TPS
     * @param durationSeconds 실행 시간(초)
     */
    public void runControlledSend(int targetTps, int durationSeconds) {
        sentCount.set(0);
        ackCount.set(0);
        lastAckNanos.set(-1L);
        sequenceNumber.set(0);

        final long intervalNanos = 1_000_000_000L / targetTps;
        final long durationNanos = durationSeconds * 1_000_000_000L;

        final long experimentStart=System.nanoTime();
        final long endTime=experimentStart+durationNanos;

        long nextSendTime=experimentStart;

        while (System.nanoTime() < endTime) {
            long now = System.nanoTime();

            if (now < nextSendTime) {
                LockSupport.parkNanos(nextSendTime - now);
                continue;
            }

            sendMessage();
            nextSendTime += intervalNanos;

            long after = System.nanoTime();
            if (after - nextSendTime > 1_000_000_000L) {
                nextSendTime = after;
            }
        }

        producer.flush();

        long experimentEnd = System.nanoTime();
        double elapsedSec = (experimentEnd - experimentStart) / 1_000_000_000.0;

        long totalSent = sentCount.get();
        long totalAcked = ackCount.get();

        double observedSendTps = elapsedSec > 0 ? totalSent / elapsedSec : 0.0;
        double observedAckTps = elapsedSec > 0 ? totalAcked / elapsedSec : 0.0;

        System.out.printf(
                "[LightProducer:%s] targetTPS=%d, duration=%ds, sent=%d, acked=%d, elapsed=%.3fs, observedSendTPS=%.2f, observedAckTPS=%.2f%n",
                clientId, targetTps, durationSeconds, totalSent, totalAcked, elapsedSec, observedSendTps, observedAckTps
        );

    }

    /**
     * 단일 메시지를 전송하고 send_start, ack_received, service_gap을 기록한다.
     */
    public void sendMessage() {
        try {
            inflight.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        long seq = sequenceNumber.incrementAndGet();
        long timestamp = System.currentTimeMillis();

        String requestId = clientId + "-" + timestamp + "-" + seq;
        String value = generatePayload(MESSAGE_SIZE_BYTES);

        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, requestId, value);

        long start = System.nanoTime();
        startNanos.put(requestId, start);
        logger.logStage(clientId, "send_start", requestId);

        sentCount.incrementAndGet();

        producer.send(record, (metadata, exception) -> {
            try{
                long end=System.nanoTime();
                Long st=startNanos.remove(requestId);

                if (exception != null) {
                    logger.logStage(clientId, "ack_error", requestId);
                    exception.printStackTrace();
                    return;
                }

                ackCount.incrementAndGet();
                long prevAck=lastAckNanos.getAndSet(end);
                if(prevAck>0){
                    logger.logLatency(clientId, "service_gap", requestId, prevAck, end);
                }

                if (st != null) {
                    logger.logLatency(clientId, "ack_received", requestId, st, end);
                }else{
                    logger.logStage(clientId, "ack_received", requestId);
                }
            }finally {
                inflight.release();
            }

        });
    }

    private String generatePayload(int sizeBytes) {
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
