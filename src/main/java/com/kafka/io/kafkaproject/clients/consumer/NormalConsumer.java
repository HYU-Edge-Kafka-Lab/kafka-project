package com.kafka.io.kafkaproject.clients.consumer;

import com.kafka.io.kafkaproject.logging.StageLogger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

/**
 * Normal Consumer - 정상 속도 소비 클라이언트
 *
 * 설정 (KICKOFF.md 6.4절):
 * - clientId: normal-consumer-{N}
 * - max.poll.records: 500
 * - fetch.min.bytes: 1
 * - fetch.max.wait.ms: 500
 * - 처리 지연: 없음
 *
 * 역할:
 * - 정상 baseline 측정
 * - SlowConsumer와의 비교 대상
 *
 * 사용 시나리오:
 * - S0: Balanced Load (대조군)
 */
public class NormalConsumer implements AutoCloseable {

    private static final String TOPIC = "starvation-test";

    private final KafkaConsumer<String, String> consumer;
    private final String clientId;
    private final StageLogger logger;

    private volatile boolean running = true;

    public NormalConsumer(String scenarioId, String bootstrapServers, String groupId, int consumerId) throws IOException {
        this.clientId = "normal-consumer-" + consumerId;
        this.consumer = createConsumer(bootstrapServers, groupId);
        this.logger=new StageLogger(scenarioId, clientId);
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
     * 정상 속도 소비 실행
     */
    public void runConsume() {
        consumer.subscribe(List.of(TOPIC));

        while (running) {
            long pollStart = System.nanoTime();
            logger.logStage(clientId, "poll_start","-");

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            long pollEnd= System.nanoTime();

            if(records.isEmpty()){
                logger.logLatency(clientId, "poll_timeout", "-", pollStart, pollEnd);
                continue;
            }

            logger.logLatency(clientId, "fetch_received","-", pollStart, pollEnd);

            // 즉시 처리 (지연 없음)
            records.forEach(record -> {
            });

            long processDone = System.nanoTime();

            // TODO: 로깅
            logger.logLatency(clientId, "process_done","-", pollEnd, processDone);
        }
    }

    public void stop() {
        running = false;
    }

    @Override
    public void close() {
        try{
            consumer.close();
        }finally {
            logger.close();
        }

    }
}
