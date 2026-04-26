package com.kafka.io.kafkaproject.config;

import java.nio.file.Path;

/**
 * 실험 정책/시나리오별 결과 경로 규칙을 한 곳에서 관리한다.
 */
public final class ExperimentPaths {

    private static final String DEFAULT_POLICY = "default";

    private ExperimentPaths() {}

    public static String resolvePolicy() {
        String policy = System.getProperty("experiment.policy");
        if (policy == null || policy.isBlank()) {
            policy = System.getenv("EXPERIMENT_POLICY");
        }
        if (policy == null || policy.isBlank()) {
            return DEFAULT_POLICY;
        }
        return sanitizeSegment(policy);
    }

    public static Path scenarioDir(String scenarioId) {
        return Path.of("results", resolvePolicy(), sanitizeSegment(scenarioId));
    }

    public static Path scenarioDir(String policy, String scenarioId) {
        return Path.of("results", sanitizeSegment(policy), sanitizeSegment(scenarioId));
    }

    private static String sanitizeSegment(String value) {
        return value.replace('\\', '-')
                .replace('/', '-')
                .replace(' ', '_');
    }
}
