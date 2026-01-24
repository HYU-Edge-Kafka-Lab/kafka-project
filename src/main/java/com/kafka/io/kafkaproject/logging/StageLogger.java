package com.kafka.io.kafkaproject.logging;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Stage별 로그 기록기
 *
 * 로그 포맷 (KICKOFF.md 10.1절):
 * {ts}|{thread}|{clientId}|{stage}|{requestId}|{latency_ms}
 *
 * 예시:
 * 2024-01-15T10:30:00.123|network-thread-0|heavy-producer-1|read_done|12345|2.5
 * 2024-01-15T10:30:00.125|network-thread-0|heavy-producer-1|enqueue_req|12345|0.3
 *
 * Stage 정의 (KICKOFF.md 4.1절, 4.2절):
 *
 * 브로커 내부:
 * - poll: Selector.poll() 시점
 * - read_done: 데이터 읽기 완료
 * - enqueue_req: RequestChannel에 요청 적재
 * - dequeue_req: Handler가 요청 꺼냄
 * - handle_done: Handler 처리 완료
 * - send_done: Response 전송 완료
 *
 * 클라이언트:
 * - send_start: Producer 전송 시작
 * - ack_received: Producer ACK 수신
 * - poll_start: Consumer poll 시작
 * - fetch_received: Consumer fetch 완료
 * - process_done: Consumer 처리 완료
 */
public class StageLogger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());

    private final PrintWriter writer;

    public StageLogger(String filePath) throws IOException {
        this.writer = new PrintWriter(new FileWriter(filePath, true));
        // 헤더 출력
        writer.println("# ts|thread|clientId|stage|requestId|latency_ms");
    }

    /**
     * 로그 기록
     *
     * @param clientId  클라이언트 ID (e.g., "heavy-producer-1")
     * @param stage     처리 단계 (e.g., "send_start", "read_done")
     * @param requestId 요청 ID (correlationId 또는 메시지 key)
     * @param latencyMs 소요 시간 (ms), 해당 없으면 0
     */
    public void log(String clientId, String stage, String requestId, double latencyMs) {
        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        String threadName = Thread.currentThread().getName();

        String logLine = String.format("%s|%s|%s|%s|%s|%.3f",
                timestamp, threadName, clientId, stage, requestId, latencyMs);

        writer.println(logLine);
        writer.flush();
    }

    /**
     * 브로커 측 로그 (latency 없이)
     */
    public void logStage(String clientId, String stage, String requestId) {
        log(clientId, stage, requestId, 0.0);
    }

    /**
     * 클라이언트 측 latency 로그
     */
    public void logLatency(String clientId, String stage, String requestId,
                           long startNanos, long endNanos) {
        double latencyMs = (endNanos - startNanos) / 1_000_000.0;
        log(clientId, stage, requestId, latencyMs);
    }

    public void close() {
        writer.close();
    }

    // Singleton for convenience
    private static StageLogger instance;

    public static synchronized StageLogger getInstance() {
        if (instance == null) {
            try {
                instance = new StageLogger("results/stage-log.csv");
            } catch (IOException e) {
                throw new RuntimeException("Failed to create StageLogger", e);
            }
        }
        return instance;
    }
}
