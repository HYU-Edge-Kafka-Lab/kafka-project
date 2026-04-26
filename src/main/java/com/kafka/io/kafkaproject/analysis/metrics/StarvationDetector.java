package com.kafka.io.kafkaproject.analysis.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starvation 발생 감지기
 *
 * 판정 기준 (KICKOFF.md 섹션 3):
 *
 * 지연 기준 (3.1절):
 * - p95 latency >= 50ms → Starvation 의심
 * - p99 latency >= 200ms → Starvation 의심
 * - max latency >= 1,000ms → Starvation 의심
 * - 동일 client 연속 요청 간격 >= 5초 → Starvation 의심
 *
 * 편향 기준 (3.2절):
 * - 단일 client가 전체 처리의 >= 70% 점유 → 편향
 * - 해당 편향이 >= 30초 지속 → Starvation 판정
 *
 * 병목 구분 (3.3절):
 * - enqueue 이전 지연 → Network/Selector 단계 문제
 * - enqueue 이후 지연 → Handler/처리 병목
 */
public class StarvationDetector {

    // 판정 임계값 (KICKOFF.md 기준)
    private static final long P95_THRESHOLD_MS = 50;
    private static final long P99_THRESHOLD_MS = 200;
    private static final long MAX_THRESHOLD_MS = 1000;
    private static final long REQUEST_GAP_THRESHOLD_MS = 5000;
    private static final double BIAS_RATIO_THRESHOLD = 0.70;
    private static final long BIAS_DURATION_THRESHOLD_SEC = 30;

    // Client별 메트릭 저장
    private final Map<String, ClientMetrics> clientMetricsMap = new ConcurrentHashMap<>();

    /**
     * 요청 처리 완료 시 호출
     *
     * @param clientId   클라이언트 ID (e.g., "heavy-producer-1")
     * @param stage      처리 단계 (e.g., "read_done", "enqueue_req")
     * @param latencyMs  해당 단계 소요 시간
     */
    public void recordLatency(String clientId, String stage, long latencyMs) {
        // 향후 확장 지점:
        // - client별 latency 히스토그램 업데이트
        // - p95, p99, max 계산
    }

    /**
     * 처리 편향 검사
     *
     * @return 편향 발생 여부
     */
    public boolean checkBias() {
        // 향후 확장 지점:
        // - 최근 구간의 client별 처리 비율 계산
        // - 임계치 이상 점유 client 존재 여부 판단
        return false;
    }

    /**
     * Starvation 최종 판정
     *
     * @return Starvation 판정 결과
     */
    public StarvationResult detect() {
        // 향후 확장 지점:
        // - 지연 기준 + 편향 기준 종합 판단
        // - 병목 위치 식별
        return new StarvationResult(false, "N/A", "N/A");
    }

    /**
     * Starvation 판정 결과
     */
    public record StarvationResult(
            boolean detected,
            String bottleneckLocation,  // "network" | "handler" | "mixed"
            String triggerCondition
    ) {}

    /**
     * Client별 메트릭 저장 클래스
     */
    private static class ClientMetrics {
        Instant lastRequestTime;
        long requestCount;
        // 필요 시 latency 히스토그램을 추가할 수 있다.
    }
}
