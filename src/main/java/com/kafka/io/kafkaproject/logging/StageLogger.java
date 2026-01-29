package com.kafka.io.kafkaproject.logging;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
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


    /**
     * 클라이언트별 StageLogger 생성자
     *
     * @param scenarioId 실험 시나리오 ID (e.g., S1, S2)
     * @param clientId   클라이언트 ID (e.g., heavy-producer-1)
     */
    public StageLogger(String scenarioId, String clientId) throws IOException {
        // results/{scenarioId} 디렉토리 생성
        Path dirPath=Path.of("results", scenarioId);
        Files.createDirectories(dirPath);

        // results/{scenarioId}/{clientId}.log
        File logFile=dirPath.resolve(clientId+".log").toFile();
        boolean isNewFile=!logFile.exists();
        this.writer = new PrintWriter(new FileWriter(logFile, true));

        // 파일 최초 생성 시에만 헤더 출력
        if (isNewFile) {
            writer.println("# ts|thread|clientId|stage|requestId|latency_ms");
            writer.flush();
        }
    }

    /**
     * 로그 기록
     *
     * @param clientId  클라이언트 ID (e.g., "heavy-producer-1")
     * @param stage     처리 단계 (e.g., "send_start", "read_done")
     * @param requestId 요청 ID (correlationId 또는 메시지 key)
     * @param latencyMs 소요 시간 (ms), 해당 없으면 0
     */
    public synchronized void log(String clientId, String stage, String requestId, double latencyMs) {
        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());

        String threadName = sanitize(Thread.currentThread().getName());
        String safeClientId = sanitize(clientId);
        String safeStage = sanitize(stage);
        String safeRequestId = sanitize(requestId);

        String logLine = String.format("%s|%s|%s|%s|%s|%.3f",
                timestamp, threadName, safeClientId, safeStage, safeRequestId, latencyMs);

        writer.println(logLine);
        writer.flush();
    }

    /**
     * 브로커 측 로그 (latency 없이)
     */
    public void logStage(String clientId, String stage, String requestId) {
        log(clientId, stage, requestId, -1.0);
    }

    /**
     * 클라이언트 측 latency 로그
     */
    public void logLatency(String clientId, String stage, String requestId,
                           long startNanos, long endNanos) {
        double latencyMs = (endNanos - startNanos) / 1_000_000.0;
        log(clientId, stage, requestId, latencyMs);
    }

    private String sanitize(String s) {
        if (s == null) return "";
        // Prevent delimiter/newline injection that would break log parsing
        return s.replace("|", "/")
                .replace("\n", " ")
                .replace("\r", " ");
    }


    public void close() {
        writer.close();
    }
}
