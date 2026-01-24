package com.kafka.io.kafkaproject.clients.producer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Light Producer - 저부하 간헐적 전송 클라이언트
 *
 * 설정 (KICKOFF.md 6.4절):
 * - clientId: light-producer-{N}
 * - acks: 1
 * - linger.ms: 0
 * - batch.size: 16384 (16KB)
 * - buffer.memory: 33554432 (32MB)
 * - 전송 빈도: 100ms 간격
 * - 메시지 크기: 1KB
 *
 * 역할:
 * - Starvation 피해자 역할
 * - Heavy Producer와의 비교 대상
 * - p95/p99 latency 측정 대상 (KICKOFF.md 3.1절)
 *
 * 사용 시나리오:
 * - S0: Balanced Load (1,000 TPS, 대조군)
 * - S1: Heavy vs Light Producer (100 TPS, 피해자)
 */
public class LightProducer implements AutoCloseable {

    private static final String TOPIC = "starvation-test";
    private static final int MESSAGE_SIZE_BYTES = 1024; // 1KB
    private static final long SEND_INTERVAL_MS = 100;   // 100ms 간격

    private final KafkaProducer<String, String> producer;
    private final String clientId;
    private final AtomicLong sequenceNumber = new AtomicLong(0);

    public LightProducer(String bootstrapServers, int producerId) {
        this.clientId = "light-producer-" + producerId;
        this.producer = createProducer(bootstrapServers);
    }

    private KafkaProducer<String, String> createProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // KICKOFF.md 6.4절 설정
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);        // 16KB
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432L); // 32MB

        return new KafkaProducer<>(props);
    }

    /**
     * 간헐적 전송 실행 (100ms 간격)
     *
     * @param durationSeconds 실행 시간(초)
     */
    public void runIntervalSend(int durationSeconds) throws InterruptedException {
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);

        while (System.currentTimeMillis() < endTime) {
            long sendStart = System.nanoTime();
            sendMessage();

            // TODO: send_start -> ack_received latency 기록
            // Starvation 판정 기준: p95 >= 50ms, p99 >= 200ms

            Thread.sleep(SEND_INTERVAL_MS);
        }
    }

    /**
     * 단일 메시지 전송
     */
    public void sendMessage() {
        long seq = sequenceNumber.incrementAndGet();
        long timestamp = System.currentTimeMillis();

        String key = String.format("%s-%d-%d", clientId, timestamp, seq);
        String value = generatePayload(MESSAGE_SIZE_BYTES);

        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, value);

        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                exception.printStackTrace();
            }
        });
    }

    private String generatePayload(int sizeBytes) {
        return "X".repeat(sizeBytes);
    }

    @Override
    public void close() {
        producer.close();
    }
}
