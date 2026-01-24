package com.kafka.io.kafkaproject.clients.producer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Heavy Producer - 고부하 연속 전송 클라이언트
 *
 * 설정 (KICKOFF.md 6.4절):
 * - clientId: heavy-producer-{N}
 * - acks: 1
 * - linger.ms: 0
 * - batch.size: 65536 (64KB)
 * - buffer.memory: 67108864 (64MB)
 * - 전송 빈도: 연속 전송 (max throughput)
 * - 메시지 크기: 10KB
 *
 * 사용 시나리오:
 * - S1: Heavy vs Light Producer (10,000 TPS)
 * - S2: Slow Consumer Backpressure (5,000 TPS)
 * - S3: Control-plane 혼합 (5,000 TPS)
 */
public class HeavyProducer implements AutoCloseable {

    private static final String TOPIC = "starvation-test";
    private static final int MESSAGE_SIZE_BYTES = 10 * 1024; // 10KB

    private final KafkaProducer<String, String> producer;
    private final String clientId;
    private final AtomicLong sequenceNumber = new AtomicLong(0);

    public HeavyProducer(String bootstrapServers, int producerId) {
        this.clientId = "heavy-producer-" + producerId;
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
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 65536);        // 64KB
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864L); // 64MB

        return new KafkaProducer<>(props);
    }

    /**
     * 연속 전송 실행
     *
     * @param targetTps 목표 TPS (e.g., 10000)
     * @param durationSeconds 실행 시간(초)
     */
    public void runContinuousSend(int targetTps, int durationSeconds) {
        // TODO: 구현
        // - targetTps에 맞춰 전송 속도 조절
        // - 메시지 key: {clientId}-{timestamp}-{seq}
        // - send_start timestamp 기록
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

        // TODO: send_start 로깅
        producer.send(record, (metadata, exception) -> {
            // TODO: ack_received 로깅
            if (exception != null) {
                exception.printStackTrace();
            }
        });
    }

    private String generatePayload(int sizeBytes) {
        // 지정된 크기의 페이로드 생성
        return "X".repeat(sizeBytes);
    }

    @Override
    public void close() {
        producer.close();
    }
}
