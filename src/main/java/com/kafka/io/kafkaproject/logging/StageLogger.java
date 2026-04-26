package com.kafka.io.kafkaproject.logging;

import com.kafka.io.kafkaproject.config.ExperimentPaths;

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
 * 로그 포맷:
 * {ts}|{thread}|{clientId}|{stage}|{requestId}|{latency_ms}
 *
 * 예시:
 * 2024-01-15T10:30:00.123|network-thread-0|heavy-producer-1|send_start|12345|-1
 * 2024-01-15T10:30:00.125|network-thread-0|heavy-producer-1|ack_received|12345|2.5
 *
 * 주요 Stage 정의:
 *
 * Producer:
 * - send_start: Producer 메시지 전송 시작
 * - ack_received: Broker ACK 수신 시점
 * - service_gap: 이전 ACK와 현재 ACK 사이 간격
 *
 * Consumer:
 * - fetch_received: 메시지 fetch 완료
 * - process_done: 메시지 처리 완료
 * - poll_timeout: poll에서 메시지를 받지 못한 경우
 *
 * 이 로그는 실험 시나리오(S0, S1, S1N)에서
 * Producer/Consumer 동작 타이밍을 기록하고
 * ACK latency 및 service gap 분석에 사용된다.
 */
public class StageLogger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());

    private final PrintWriter writer;


    /**
     * 클라이언트별 StageLogger 생성자
     *
     * @param scenarioId 실험 시나리오 ID (e.g., S0, S1, S1N)
     * @param clientId   클라이언트 ID (e.g., heavy-producer-1)
     */
    public StageLogger(String scenarioId, String clientId) throws IOException {
        // results/{policy}/{scenarioId} 디렉토리 생성
        Path dirPath = ExperimentPaths.scenarioDir(scenarioId);
        Files.createDirectories(dirPath);

        // results/{policy}/{scenarioId}/{clientId}.log
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
