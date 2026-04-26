package com.kafka.io.kafkaproject.config;

/**
 * Kafka 설정 상수
 *
 * 실험 환경 고정값 (KICKOFF.md 섹션 6)
 */
public final class KafkaConfig {

    private KafkaConfig() {}

    // ==================== 6.1 기본 인프라 ====================

    public static final String KAFKA_VERSION = "4.1.0";
    public static final String JAVA_VERSION = "JDK 21 (Temurin)";
    public static final String BROKER_MODE = "KRaft";  // ZK 의존성 제거
    public static final int BROKER_COUNT = 1;          // 단일 브로커

    // ==================== 6.2 토픽 설정 ====================

    public static final String TOPIC_NAME = "starvation-test";
    public static final int PARTITIONS = 8;            // 단일 partition contention 영향 완화
    public static final int REPLICATION_FACTOR = 1;    // 단일 브로커

    // ==================== 6.3 브로커 주요 설정 ====================

    public static final int NUM_NETWORK_THREADS = 3;   // 분석 대상
    public static final int NUM_IO_THREADS = 8;
    public static final int SOCKET_RECEIVE_BUFFER = 102400;  // 100KB
    public static final int SOCKET_SEND_BUFFER = 102400;     // 100KB
    public static final int QUEUED_MAX_REQUESTS = 500;

    // ==================== 6.4 클라이언트 설정 ====================

    // Producer 공통
    public static final String ACKS = "1";
    public static final int LINGER_MS = 0;

    // Producer 공통: 메시지 크기와 주요 옵션을 통일하고,
    // 시나리오별로는 TPS와 connection 수만 바꾼다.
    public static final int PRODUCER_BATCH_SIZE = 16384;         // 16KB
    public static final long PRODUCER_BUFFER_MEMORY = 33554432L; // 32MB
    public static final int PRODUCER_MESSAGE_SIZE = 1024;        // 1KB

    // Consumer 공통
    public static final int MAX_POLL_RECORDS = 500;
    public static final int FETCH_MIN_BYTES = 1;
    public static final int FETCH_MAX_WAIT_MS = 500;

    // Slow Consumer
    public static final long SLOW_CONSUMER_DELAY_MS = 500;      // 500ms per batch

    // ==================== 7.2 부하 파라미터 ====================

    // S0: Balanced Load
    public static final int S0_HEAVY_TPS = 500;
    public static final int S0_LIGHT_TPS = 500;

    // S1: Asymmetric load with the same producer settings
    public static final int S1_HEAVY_TPS = 3000;
    public static final int S1_LIGHT_TPS = 500;

    // S2: Same aggregate heavy load as S1, but split across many heavy connections
    public static final int S2_HEAVY_COUNT = 6;
    public static final int S2_HEAVY_TPS_PER_CONNECTION = 500;
    public static final int S2_LIGHT_COUNT = 1;
    public static final int S2_LIGHT_TPS_PER_CONNECTION = 500;

    // S1N: Same total ingress as S1, but distributed across many light connections
    public static final int S1N_HEAVY_TPS = 1000;
    public static final int S1N_LIGHT_COUNT = 5;
    public static final int S1N_LIGHT_TPS_PER_CONNECTION = 500;

    // ==================== Legacy threshold constants ====================

    public static final long P95_THRESHOLD_MS = 50;
    public static final long P99_THRESHOLD_MS = 200;
    public static final long MAX_THRESHOLD_MS = 1000;
    public static final long REQUEST_GAP_THRESHOLD_MS = 5000;

    // 기존 실험에서 사용하던 보조 임계값이며, 현재 결과 해석의 중심 지표는 아니다.

    public static final double BIAS_RATIO_THRESHOLD = 0.70;     // 70%
    public static final long BIAS_DURATION_THRESHOLD_SEC = 30;  // 30초
}
