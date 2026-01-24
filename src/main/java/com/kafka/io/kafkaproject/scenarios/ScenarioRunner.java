package com.kafka.io.kafkaproject.scenarios;

/**
 * 실험 시나리오 실행기
 *
 * 시나리오 정의 (KICKOFF.md 7.1절):
 *
 * | ID | 이름                    | 설명                                    | 기대 결과              |
 * |----|------------------------|----------------------------------------|----------------------|
 * | S0 | Balanced Load          | 동일 TPS로 균형 잡힌 요청                   | 정상 baseline 측정     |
 * | S1 | Heavy vs Light Producer| Heavy 연속 전송, Light 간헐적 전송          | Read 편향 발생 여부 확인 |
 * | S2 | Slow Consumer          | Consumer 처리 지연으로 send buffer 누적     | Write 편향 발생 여부 확인|
 * | S3 | Control-plane 혼합      | 대량 Produce 중 Metadata/JoinGroup 요청   | Control 요청 지연 여부  |
 *
 * 부하 파라미터 (KICKOFF.md 7.2절):
 *
 * | 시나리오 | Heavy TPS | Light TPS | 비율      |
 * |---------|-----------|-----------|----------|
 * | S0      | 1,000     | 1,000     | 1:1      |
 * | S1      | 10,000    | 100       | 100:1    |
 * | S2      | 5,000     | -         | Consumer |
 * | S3      | 5,000     | 10        | Data:Ctrl|
 */
public class ScenarioRunner {

    private final String bootstrapServers;

    public ScenarioRunner(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    /**
     * S0: Balanced Load (대조군)
     * - 정상 baseline 측정
     * - Heavy/Light 각 1,000 TPS
     */
    public void runS0BalancedLoad(int durationSeconds) {
        // TODO: 구현
        // 1. HeavyProducer 1개 (1,000 TPS)
        // 2. LightProducer 1개 (1,000 TPS)
        // 3. NormalConsumer 1개
        // 4. 결과 수집 및 저장
    }

    /**
     * S1: Heavy vs Light Producer
     * - Read 편향 발생 여부 확인
     * - Heavy 10,000 TPS, Light 100 TPS (100:1)
     */
    public void runS1HeavyVsLight(int durationSeconds) {
        // TODO: 구현
        // 1. HeavyProducer 1개 (10,000 TPS)
        // 2. LightProducer 1개 (100 TPS)
        // 3. NormalConsumer 1개
        // 4. Light Producer의 p95/p99 latency 측정
    }

    /**
     * S2: Slow Consumer Backpressure
     * - Write 편향 발생 여부 확인
     * - Heavy 5,000 TPS + SlowConsumer
     */
    public void runS2SlowConsumer(int durationSeconds) {
        // TODO: 구현
        // 1. HeavyProducer 1개 (5,000 TPS)
        // 2. SlowConsumer 1개 (500ms 지연)
        // 3. Send buffer 누적 모니터링
    }

    /**
     * S3: Control-plane 혼합
     * - Control 요청 지연 여부 확인
     * - Heavy 5,000 TPS + Metadata 요청 10 TPS
     */
    public void runS3ControlPlane(int durationSeconds) {
        // TODO: 구현
        // 1. HeavyProducer 1개 (5,000 TPS)
        // 2. Metadata 요청 클라이언트 (10 TPS)
        // 3. Control 요청 latency 측정
    }

    public static void main(String[] args) {
        ScenarioRunner runner = new ScenarioRunner("localhost:9092");

        String scenario = args.length > 0 ? args[0] : "S0";
        int duration = args.length > 1 ? Integer.parseInt(args[1]) : 60;

        switch (scenario) {
            case "S0" -> runner.runS0BalancedLoad(duration);
            case "S1" -> runner.runS1HeavyVsLight(duration);
            case "S2" -> runner.runS2SlowConsumer(duration);
            case "S3" -> runner.runS3ControlPlane(duration);
            default -> System.out.println("Unknown scenario: " + scenario);
        }
    }
}
