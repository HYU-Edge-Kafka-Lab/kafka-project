package com.kafka.io.kafkaproject.clients.consumer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

/**
 * Slow Consumer - 처리 지연 클라이언트
 *
 * 설정 (KICKOFF.md 6.4절):
 * - clientId: slow-consumer-{N}
 * - max.poll.records: 500
 * - fetch.min.bytes: 1
 * - fetch.max.wait.ms: 500
 * - 처리 지연: 500ms sleep per batch
 *
 * 역할:
 * - Backpressure 유발
 * - Send buffer 누적 원인
 * - Write 편향 발생 테스트
 *
 * 사용 시나리오:
 * - S2: Slow Consumer Backpressure
 */
public class SlowConsumer implements AutoCloseable {

    private static final String TOPIC = "starvation-test";
    private static final long PROCESSING_DELAY_MS = 500; // 500ms per batch

    private final KafkaConsumer<String, String> consumer;
    private final String clientId;
    private volatile boolean running = true;

    public SlowConsumer(String bootstrapServers, String groupId, int consumerId) {
        this.clientId = "slow-consumer-" + consumerId;
        this.consumer = createConsumer(bootstrapServers, groupId);
    }

    private KafkaConsumer<String, String> createConsumer(String bootstrapServers, String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        // KICKOFF.md 6.4절 설정
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new KafkaConsumer<>(props);
    }

    /**
     * 느린 소비 실행 (batch당 500ms 지연)
     */
    public void runSlowConsume() throws InterruptedException {
        consumer.subscribe(List.of(TOPIC));

        while (running) {
            // poll_start 기록
            long pollStart = System.nanoTime();

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

            // fetch_received 기록
            long fetchReceived = System.nanoTime();

            if (!records.isEmpty()) {
                // 의도적 지연 - Backpressure 유발
                Thread.sleep(PROCESSING_DELAY_MS);

                // process_done 기록
                long processDone = System.nanoTime();

                // TODO: 로깅
                // - poll_start -> fetch_received: fetch latency
                // - fetch_received -> process_done: processing time
            }
        }
    }

    public void stop() {
        running = false;
    }

    @Override
    public void close() {
        consumer.close();
    }
}
