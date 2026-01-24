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
    public static final int PARTITIONS = 1;            // 분석 단순화
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

    // Heavy Producer
    public static final int HEAVY_BATCH_SIZE = 65536;           // 64KB
    public static final long HEAVY_BUFFER_MEMORY = 67108864L;   // 64MB
    public static final int HEAVY_MESSAGE_SIZE = 10 * 1024;     // 10KB

    // Light Producer
    public static final int LIGHT_BATCH_SIZE = 16384;           // 16KB
    public static final long LIGHT_BUFFER_MEMORY = 33554432L;   // 32MB
    public static final int LIGHT_MESSAGE_SIZE = 1024;          // 1KB
    public static final long LIGHT_SEND_INTERVAL_MS = 100;      // 100ms

    // Consumer 공통
    public static final int MAX_POLL_RECORDS = 500;
    public static final int FETCH_MIN_BYTES = 1;
    public static final int FETCH_MAX_WAIT_MS = 500;

    // Slow Consumer
    public static final long SLOW_CONSUMER_DELAY_MS = 500;      // 500ms per batch

    // ==================== 7.2 부하 파라미터 ====================

    // S0: Balanced Load
    public static final int S0_HEAVY_TPS = 1000;
    public static final int S0_LIGHT_TPS = 1000;

    // S1: Heavy vs Light
    public static final int S1_HEAVY_TPS = 10000;
    public static final int S1_LIGHT_TPS = 100;

    // S2: Slow Consumer
    public static final int S2_HEAVY_TPS = 5000;

    // S3: Control-plane
    public static final int S3_HEAVY_TPS = 5000;
    public static final int S3_CONTROL_TPS = 10;

    // ==================== 3.1 Starvation 판정 기준 ====================

    public static final long P95_THRESHOLD_MS = 50;
    public static final long P99_THRESHOLD_MS = 200;
    public static final long MAX_THRESHOLD_MS = 1000;
    public static final long REQUEST_GAP_THRESHOLD_MS = 5000;

    // ==================== 3.2 편향 기준 ====================

    public static final double BIAS_RATIO_THRESHOLD = 0.70;     // 70%
    public static final long BIAS_DURATION_THRESHOLD_SEC = 30;  // 30초
}
