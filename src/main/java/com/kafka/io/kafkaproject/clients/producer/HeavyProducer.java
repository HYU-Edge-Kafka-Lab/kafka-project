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
 * - S0: Balanced Load (1,000 TPS, 대조군)
 * - S1: Heavy vs Light Producer (10000 TPS, 폭주)
 */
public class HeavyProducer implements AutoCloseable {

    private static final String TOPIC = "starvation-test";
    private static final int MESSAGE_SIZE_BYTES = 10 * 1024; // 10KB

    private final KafkaProducer<String, String> producer;
    private final String clientId;
    private final StageLogger logger;

    private final AtomicLong sequenceNumber = new AtomicLong(0);

    private final ConcurrentHashMap<String, Long> startNanos=new ConcurrentHashMap<>();

    private final AtomicLong lastAckNanos = new AtomicLong(-1L);

    private final Semaphore inflight=new Semaphore(3000);

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
        final long intervalNanos=1_000_000_000L/targetTps;

        final long startTime=System.nanoTime();
        final long endTime=startTime+durationSeconds*1_000_000_000L;

//        다음 send 예정 시각
        long nextSendTime=startTime;

        while(System.nanoTime()<endTime){
            long now=System.nanoTime();

            // 아직 보낼 시간이 아니면 남은 시간만큼 기다린다
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
    }

    /**
     * 단일 메시지 전송
     */
    public void sendMessage() {
        try{
            inflight.acquire();
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            return;

        }
        long seq = sequenceNumber.incrementAndGet();
        long timestamp = System.currentTimeMillis();

        String requestId = String.format("%s-%d-%d", clientId, timestamp, seq);
        String value = generatePayload(MESSAGE_SIZE_BYTES);

        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, requestId, value);

        // send_start 로깅
        long start=System.nanoTime();
        startNanos.put(requestId, start);
        logger.logStage(clientId, "send_start", requestId);

        producer.send(record, (metadata, exception) -> {
            // ack_received 로깅
            try{
                Long st=startNanos.remove(requestId);
                long end=System.nanoTime();
                if (exception != null) {
                    logger.logStage(clientId, "ack_error", requestId);
                    exception.printStackTrace();
                    return;
                }
                long prevAck=lastAckNanos.getAndSet(end);
                if(prevAck>0){
                    logger.logLatency(clientId, "service_gap", requestId, prevAck, end);
                }
                if(st!=null) {
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
