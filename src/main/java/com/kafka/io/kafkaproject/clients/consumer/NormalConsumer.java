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
 * Normal Consumer - 실험 보조 소비자
 *
 * - clientId: normal-consumer-{N}
 * - max.poll.records: 500
 * - fetch.min.bytes: 1
 * - fetch.max.wait.ms: 500
 * - 처리 지연: 없음 (baseline consumer)
 *
 * 역할:
 * - Producer 실험(S0, S1, S1N) 동안 토픽에 적재된 메시지를 지속적으로 소비
 * - broker 내부 backlog가 과도하게 누적되는 것을 방지
 * - Producer ACK 및 Service Gap 측정이 broker queue 적체의 영향을 받지 않도록 유지
 *
 * 동작 방식:
 * - poll → fetch_received → process_done 단계 로깅
 * - 메시지 처리 로직 없이 즉시 소비
 * - Consumer 처리 지연을 최소화하여 실험 환경을 안정적으로 유지
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
            for (var record : records) {
                // no processing (baseline consumer)
            }

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
