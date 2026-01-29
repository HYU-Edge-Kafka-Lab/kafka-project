package com.kafka.io.kafkaproject.clients.producer;

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
 *
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
    private final StageLogger logger;

    private final AtomicLong sequenceNumber = new AtomicLong(0);

    private final ConcurrentHashMap<String, Long> startNanos=new ConcurrentHashMap<>();

    private final AtomicLong lastAckNanos=new AtomicLong(-1L);

    public LightProducer(String scenarioId, String bootstrapServers, int producerId) throws IOException {
        this.clientId = "light-producer-" + producerId;
        this.producer = createProducer(bootstrapServers);
        this.logger=new StageLogger(scenarioId, clientId);
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


    public void runControlledSend(int targetTps, int durationSeconds) {
        final long intervalNanos = 1_000_000_000L / targetTps;

        final long startTime = System.nanoTime();
        final long endTime = startTime + durationSeconds * 1_000_000_000L;

        long nextSendTime = startTime;

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
    }

    /**
     * 단일 메시지 전송
     */
    public void sendMessage() {
        long seq = sequenceNumber.incrementAndGet();
        long timestamp = System.currentTimeMillis();

        String requestId = String.format("%s-%d-%d", clientId, timestamp, seq);
        String value = generatePayload(MESSAGE_SIZE_BYTES);

        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, requestId, value);

        long start=System.nanoTime();
        startNanos.put(requestId, start);
        logger.logStage(clientId, "send_start", requestId);

        producer.send(record, (metadata, exception) -> {
            long end=System.nanoTime();
            Long st=startNanos.remove(requestId);

            if (exception != null) {
                logger.logStage(clientId, "ack_error", requestId);
                exception.printStackTrace();
                return;
            }

            long prevAck=lastAckNanos.getAndSet(end);
            if(prevAck>0){
                logger.logLatency(clientId, "service_gap", requestId, prevAck, end);
            }

            if (st != null) {
                logger.logLatency(clientId, "ack_received", requestId, st, end);
            }else{
                logger.logStage(clientId, "ack_received", requestId);
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
